# 0001 — Maven coordinates `com.chatwoot.android:sdk` under Chatwoot's namespace

Date: 2026-06-12 · Status: accepted

## Context

This SDK is developed in the `Lascade-Co/android-sdk` fork but is intended as an upstream
contribution to `chatwoot/android-sdk`. Maven coordinates are effectively permanent: Maven
Central forbids republishing a GAV and consumers hard-code the group in their builds. The
Sonatype Central Portal additionally requires the publisher to own a *verified namespace*
matching the group.

## Decision

Publish as `com.chatwoot.android:sdk`, with POM url/scm pointing at
`github.com/chatwoot/android-sdk`. Lascade does not create a parallel coordinate under its
own namespace.

## Consequences

- CI publishing stays red until the project lands upstream and Chatwoot's Sonatype
  namespace credentials are added as repo secrets (`MAVEN_CENTRAL_USERNAME/PASSWORD`,
  `SIGNING_KEY*`). This is expected, not a bug.
- Local development is unaffected — `./gradlew :sdk:publishToMavenLocal` needs no
  credentials.
- If upstreaming falls through, the group must change *before* any public release, never
  after.
