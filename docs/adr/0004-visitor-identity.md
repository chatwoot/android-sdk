# 0004 — Visitor identity via `Chatwoot.setUser`, HMAC stays server-side

Date: 2026-06-15 · Status: accepted

## Context

The SDK was anonymous-only: every contact Chatwoot created was an unnamed record, so agents
couldn't see who they were talking to and conversations couldn't be tied to a known user across
reinstalls/devices. Host apps that already authenticate their users need to forward name / email /
phone / custom attributes, and — to prevent impersonation — optionally a stable **identifier**
validated by HMAC. Chatwoot's website widget exposes this through `setUser`, backed by
`PATCH /api/v1/widget/contact` and `/contact/set_user` (see CONTEXT.md).

## Decision

- **Surface:** imperative `Chatwoot.setUser(...)`, `Chatwoot.setCustomAttributes(...)`, and
  `Chatwoot.reset()` on the existing singleton — not parameters on `configure()` or `ChatPage()`.
  Identity is usually known only after the host's own login, which happens after `configure()` and
  independently of when the chat UI is shown; an imperative call decouples the two and mirrors
  Chatwoot's official JS/mobile SDK shape. Identity lives in a `StateFlow` the repository observes,
  so it is flushed right after bootstrap and again on later changes.
- **HMAC stays server-side:** `setUser` accepts an optional **precomputed** `identifierHash`; the
  SDK never takes the inbox's HMAC secret. The secret is per-inbox and computing the hash in the
  app would require shipping it in the binary, where it is trivially extractable — defeating the
  point of identity validation. The host's backend computes `HMAC-SHA256(hmac_token, identifier)`.
- **Identifier switch auto-resets:** the active identifier is persisted next to the conversation
  JWT (`TokenStore`). If `setUser`'s identifier differs from the stored one, the session is cleared
  before bootstrap so a second user can't resume the first user's conversation. `reset()` clears it
  explicitly on logout.

## Consequences

- `setUser` taking a precomputed hash (rather than "just working") pushes a server-side
  integration step onto adopters when identity validation is enabled — accepted as the only secure
  option. With validation off, the hash is omitted and attributes still flow.
- Because the singleton holds no reference to a live session, `reset()` / an identifier switch take
  effect on the **next** `ChatPage` open; a session already on screen continues until dismissed.
  A mid-session identifier change therefore can't re-key the current conversation — documented, not
  worked around.
- Two new contact endpoints are now part of the SDK's verified contract (recorded in CONTEXT.md).
- `set_user` may return a fresh `widget_auth_token` when identifying merges/swaps the underlying
  contact. The repository adopts it as the new active+persisted `X-Auth-Token` so subsequent REST
  calls follow the contact the server resolved — `session` is the single source of truth for the
  token, read at call time rather than captured. The realtime channel can't follow (no new
  `pubsub_token` is returned), so it stays on the original contact's `RoomChannel` until the next
  `ChatPage` open. This is the REST-side counterpart to the "can't re-key the current conversation"
  consequence above.
