# 0002 — Kotlin Multiplatform with shared Compose UI and Ktor

Date: 2026-06-12 · Status: accepted

## Context

The SDK must serve Android first but iOS support is a requirement, including the chat UI
itself. Alternatives considered: (a) Android-only Jetpack Compose, port later; (b) KMP core
(networking/state) with native UI per platform; (c) KMP + Compose Multiplatform sharing the
UI too.

## Decision

Full sharing (c): one `:sdk` KMP module — `androidLibrary`, `iosArm64`, `iosSimulatorArm64`
targets — with the entire stack in `commonMain`: Ktor (OkHttp engine on Android, Darwin on
iOS) for REST + the ActionCable websocket, kotlinx-serialization, multiplatform-settings
for token persistence, and `ChatPage` itself in Compose Multiplatform. iOS additionally
gets a `ChatPageViewController()` UIKit wrapper.

## Consequences

- One implementation of the chat UI, protocol, and reconnect logic; platform code is
  ~40 lines of expect/actual (config fallback) plus the UIViewController wrapper.
- iOS binary cost: Compose Multiplatform adds roughly 9 MB to the consuming app.
- CI must run on macOS for every build and publish (iOS targets compile there only), and
  pure-Swift apps consume a binary XCFramework via SPM (GitHub Releases) rather than source.
- The repo name "android-sdk" undersells the artifact; the Maven group
  `com.chatwoot.android` is kept regardless (ADR 0001).
