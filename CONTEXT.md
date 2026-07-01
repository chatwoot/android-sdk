# Context

Domain language and protocol contract for the Chatwoot SDK. Terms here are canonical — use
them in code, docs, and discussion.

## Glossary

- **Website inbox** — the Chatwoot inbox type this SDK talks to. Created in the Chatwoot
  dashboard; identified by its **website token**.
- **Website token** — public token identifying a website inbox (`website_token` query param
  on every API call). Not a secret.
- **Conversation token** (`cw_conversation`) — a JWT identifying one contact session
  (claims: `source_id`, `inbox_id`). Returned by the widget bootstrap as `window.authToken`,
  sent as the `X-Auth-Token` header on REST calls, and passed back as `?cw_conversation=` on
  re-bootstrap to resume the same contact + conversation. Persisted by `TokenStore`
  (SharedPreferences / NSUserDefaults), keyed per website token.
- **Pubsub token** — the contact's ActionCable subscription credential
  (`window.chatwootPubsubToken`). A *contact* pubsub token only receives events for that
  contact's own session; *user* (agent) tokens see account-wide events — the SDK only ever
  holds contact tokens.
- **RoomChannel** — the single ActionCable channel Chatwoot broadcasts on. Contact
  subscriptions identify as `{"channel":"RoomChannel","pubsub_token":"…"}` (no
  account_id/user_id — those are for agent connections).
- **ChatPage** — the SDK's entire public UI surface:
  `ChatPage(show, onFinish, styleConfig = DefaultStyle)`.
- **StyleConfig / DefaultStyle** — the one theming object; all visual customisation flows
  through it. No other styling knobs exist.
- **Contact** — the end user chatting. Chatwoot auto-creates an anonymous contact (e.g.
  "weathered-shape-813") on first bootstrap. The host names it via `Chatwoot.setUser(...)`.
- **Identifier** — a stable, host-defined id for the contact (e.g. the host app's user id).
  Supplied via `setUser`; lets Chatwoot recognise the same person across reinstalls/devices.
  Persisted next to the conversation token by `TokenStore`; changing it starts a fresh contact.
- **Identity validation / HMAC** — optional impersonation protection. When an inbox enables it,
  associating an **identifier** requires an `identifier_hash` = `HMAC-SHA256(inbox_hmac_token,
  identifier)`. The `hmac_token` is a per-inbox **secret** computed **server-side** by the host's
  backend; the SDK only ever forwards a precomputed hash and never holds the secret.
- **Custom attributes** — inbox-defined key/value fields on the contact (`custom_attributes`).
  The SDK takes a `Map<String,String>`; numbers/dates are passed as strings.
- **Attachment** — a file carried by a Message. Each has a `file_type` (`image`, `audio`,
  `video`, `file`, plus rarer kinds — anything other than the first three renders as a generic
  file), a `data_url` (original) and optional `thumb_url` (preview). Sent one-per-message and
  caption-less (the upload carries no `content`). _Avoid_: media, upload.

## Protocol contract (verified against app.chatwoot.com, June 2026)

> The upstream wiki (`Steps-to-build-the-integration`) is stale: `GET /widgets.json` no
> longer exists, and message POSTs authenticate with `X-Auth-Token`, not bare query params.

### Bootstrap

`GET <base>/widget?website_token=T[&cw_conversation=JWT]` returns **HTML**; the session
tokens are embedded as script globals and parsed by `WidgetPageParser`:

```
window.authToken = '<conversation JWT>'
window.chatwootPubsubToken = '<pubsub token>'
```

Omitting `cw_conversation` creates a fresh contact; passing it resumes the session.

### REST (all under `/api/v1/widget`, all with `?website_token=T` + `X-Auth-Token` header)

| Call | Notes |
|---|---|
| `GET /messages` | `{"payload":[Message…]}`. `message_type`: 0 contact, 1 agent, 2 activity, 3 template. `created_at` is unix seconds. `private: true` messages are agent notes — never render. |
| `POST /messages` | Body `{"message":{"content","timestamp","referer_url"}}`. **Lazily creates the conversation** if none exists yet (verified: posting to a fresh session returns the created Message with `conversation_id` set and `message_type` as an **int**). The SDK still routes the *first text* send through `POST /conversations` (below); attachments always use this endpoint. Returns the created Message. |
| `POST /messages` (multipart) | **Attachment** upload (`multipart/form-data`): `message[attachments][]` (the file, with filename) + `message[referer_url]` + `message[timestamp]`. No `content`, no `echo_id` — one file, caption-less. Like the JSON form it **lazily creates the conversation** when none exists, so attachment-*first* sends use this — never `POST /conversations`. Returns the created Message with a parseable int `message_type` and a populated `attachments[]` (parse for the real `id` + attachment URLs; no refetch needed). |
| `POST /conversations` | First message of a session: `{"message":{…}}` (optional `contact:{name,email,phone_number}`). Creates conversation + message. Caveat: in *this* response `message_type` is a string (`"template"`); the SDK refetches `GET /messages` instead of parsing it. |
| `GET /conversations` | `{}` when no conversation exists yet. |
| `PATCH /contact` | Updates the (possibly anonymous) contact. **Flat** body (not nested under `contact`): `{name,email,phone_number,avatar_url,custom_attributes:{},additional_attributes:{}}`. No identity validation. Also the `setCustomAttributes` path (`{custom_attributes:{}}`). |
| `PATCH /contact/set_user` | Associates a stable `identifier`: body `{identifier, …same fields…, identifier_hash}`. Server runs `validate_hmac` (`HMAC-SHA256(hmac_token, identifier)`) — enforced only when the inbox has identity validation on; otherwise `identifier_hash` is optional. Response is `{id, has_email, has_name, has_phone_number}`, and **conditionally** a `widget_auth_token` — the server mints a fresh session JWT when identifying changes the underlying contact (a merge/swap). When present, the SDK must adopt it as the new active+persisted `X-Auth-Token` (it replaces the `cw_conversation` JWT). Not returned on inboxes without identity validation (unverified against such an inbox; implemented defensively as optional). No new `pubsub_token` is returned, so the realtime channel stays on the original contact's `RoomChannel`. |
| `GET /inbox_members` | `{"payload":[{id,name,avatar_url,availability_status}]}` — no auth header needed. |

A Message carries an `attachments` array; each entry: `{id, file_type, data_url, thumb_url,
file_size, width, height, extension}`. Received attachments need no special handling — they
arrive on `GET /messages` and on the `message.created`/`message.updated` websocket events like
any other field.

> **Attachment-first sends** (verified against app.chatwoot.com): the session's *first* message
> when it carries a file goes through multipart `POST /messages`, **not** `POST /conversations`.
> The server lazily creates the conversation and returns a fully parseable Message (int
> `message_type` + populated `attachments[]`), so the SDK reconciles its optimistic bubble
> directly from the response — no refetch, no separate "create with attachment" path.

### Realtime (`wss://<base>/cable`, ActionCable wire protocol)

1. On connect the server sends `{"type":"welcome"}`, then `{"type":"ping"}` every ~3s.
2. Subscribe: `{"command":"subscribe","identifier":"{\"channel\":\"RoomChannel\",\"pubsub_token\":\"…\"}"}`
   — note the identifier is a JSON-*string*, not an object. Server confirms with
   `{"type":"confirm_subscription"}`.
3. Keepalive: every 30s send
   `{"command":"message","identifier":<same>,"data":"{\"action\":\"update_presence\"}"}`.
4. Broadcasts arrive as `{"identifier":…,"message":{"event":"…","data":{…}}}`. Events a
   contact receives: `message.created`, `message.updated`, `conversation.typing_on/off`
   (`data.user` may be the contact itself — filter `type=="contact"`; honour `is_private`),
   `conversation.status_changed`, `presence.update`.
5. On drop: reconnect with exponential backoff and refetch `GET /messages` to catch up
   (`CableClient` + `ChatRepository.onCableEvent`).
