# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Kotlin Multiplatform (Android + iOS) chat SDK for Chatwoot, published as
`com.chatwoot.android:sdk`. The public surface is `ChatPage(show, onFinish,
styleConfig = DefaultStyle)` (Compose Multiplatform), the `Chatwoot` singleton
(`configure()`, plus the visitor-identity calls `setUser()` / `setCustomAttributes()` /
`reset()`), and, on iOS, the `ChatPageViewController()` wrapper — everything else is
`internal` (the module uses `explicitApi()`).

**Read `CONTEXT.md` first** for the domain glossary and the *verified* Chatwoot protocol
contract (REST + ActionCable websocket). The upstream wiki this project started from is
stale — `CONTEXT.md` reflects reality as probed against app.chatwoot.com; trust it over
the wiki. `docs/adr/` records the load-bearing decisions (Maven coordinates, KMP+CMP,
media stack, visitor identity).

## Commands

```bash
./gradlew build                       # all targets + tests (requires macOS for iOS)
./gradlew :sdk:testAndroidHostTest    # JVM unit tests only (fastest loop)
./gradlew :sdk:iosSimulatorArm64Test  # common tests on the iOS simulator
./gradlew :sdk:publishToMavenLocal    # local publish, no signing needed
./gradlew :sample-app:installDebug    # Android demo on a connected device/emulator
./gradlew :sdk:assembleChatwootSDKReleaseXCFramework  # XCFramework for Swift consumers
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build   # iOS demo (or open in Xcode)
# NB: generic simulator destinations fail — they request ios_x64, which :sdk deliberately
# doesn't target (arm64-only). Use a concrete simulator or ARCHS=arm64.
```

Manual e2e testing needs Chatwoot credentials in `local.properties` (not committed):
`chatwoot.baseUrl=…` and `chatwoot.websiteToken=…` — the sample app injects them via
BuildConfig.

## Architecture

One KMP module, `:sdk` (targets: `androidLibrary` via AGP's
`com.android.kotlin.multiplatform.library`, `iosArm64`, `iosSimulatorArm64`). Data flows:

```
ChatPage → ChatScreen → ChatViewModel (multiplatform lifecycle ViewModel)
  → ChatRepository (session lifecycle, message StateFlow, send; dedupes REST + ws)
      → WidgetApi   (Ktor REST; bootstrap parses HTML via WidgetPageParser)
      → CableClient (websocket: subscribe/keepalive/backoff; pure frame logic in CableProtocol)
      → TokenStore  (multiplatform-settings; persists the cw_conversation JWT + active
                     identifier per website token)
```

Key invariants:
- The bootstrap endpoint returns **HTML**, not JSON — session tokens are regex-parsed
  (`WidgetPageParser`). There is no JSON alternative.
- First send of a fresh session goes through `POST /conversations`, later sends through
  `POST /messages`; after conversation creation the repository *refetches* messages rather
  than parsing the create response (its `message_type` is a string there, an int elsewhere).
- `CableProtocol` is pure functions (frame parse/build) so it stays unit-testable;
  `CableClient` owns the connection loop, 30s `update_presence` keepalive, and reconnect
  backoff — on reconnect the repository refreshes history to catch up missed events.
- `private: true` messages are agent notes — filter, never render. `message_type`: 0
  contact, 1 agent, 2 activity, 3 template.
- Visitor identity lives in `Chatwoot.identity` (a `StateFlow`); the repository flushes it
  after bootstrap (and on later changes) via `PATCH /widget/contact/set_user` when an
  identifier is set, else `PATCH /widget/contact`. HMAC `identifier_hash` is **host-supplied**
  (computed server-side) — never compute it in the SDK. A changed identifier clears the stored
  session so the next bootstrap starts a fresh contact. See ADR 0004.
- Platform code is minimal by design: expect/actual for the config fallback
  (manifest meta-data / Info.plist) and the Ktor engine comes from the classpath
  (OkHttp on Android, Darwin on iOS). Keep new code in `commonMain`.

## Build system conventions (mirrors the sibling `dependables` repo)

- Root `build.gradle.kts` centralises group + vanniktech maven-publish
  (`publishToMavenCentral(automaticRelease = true)`, signing only when
  `ORG_GRADLE_PROJECT_signingInMemoryKey` is set, config-cache opt-out for publish tasks).
  The `:sdk` module owns its own `version` and `pom {}`.
- Repositories only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`); versions only in
  `gradle/libs.versions.toml`. JVM target 21. The Compose compiler plugin version is the
  Kotlin version.
- `kotlin.daemon.jvmargs`/`kotlin.native.jvmArgs` are raised in `gradle.properties` —
  Kotlin/Native linking of Compose OOMs at the default heap; don't remove them.
- CI (`.github/workflows/`): `build.yml` runs `./gradlew build` on macOS;
  `publish.yml` publishes **only when `version = "…"` changes** in `sdk/build.gradle.kts`
  on master, then builds the XCFramework, attaches it to a `sdk-vX.Y.Z` GitHub release and
  rewrites `Package.swift` (url + checksum) for SPM consumers.
- Publishing to Maven Central requires Chatwoot's verified Sonatype namespace
  (`com.chatwoot`) — expected to stay red until upstreaming (ADR 0001).
