# Architecture

## App entry

`MainActivity` boots the Compose shell and hands off startup routing to `AppEntryViewModel`. That view model owns onboarding, demo mode, theme, and startup routing so the entry surface stays deterministic.

## Code layout

- `app/src/main/java/com/nodex/client/core/` - SSH transport, parsers, polling helpers, and shared runtime utilities
- `app/src/main/java/com/nodex/client/data/` - Room, DataStore, repositories, and other persisted state
- `app/src/main/java/com/nodex/client/domain/` - app models and use-case boundaries
- `app/src/main/java/com/nodex/client/di/` - Hilt modules and dependency wiring
- `app/src/main/java/com/nodex/client/ui/` - Compose screens, navigation, and presentation models
- `app/src/main/java/com/nodex/client/testing/` - test-only helpers and fixtures
- `app/schemas/` - exported Room schemas

## Runtime behavior

- Polling and Docker refresh are lifecycle-aware so background work stops when the relevant UI surface stops.
- Elevated commands send the sudo password over SSH standard input instead of embedding it in shell command text.
- The app stays monitor-only. There is no billing, subscription, or paywall path.
- The app sends no telemetry.

## Build variants

- `debug` - local debug build for app iteration
- `dev` - debug-compatible variant used for local app iteration
- `releaseProof` - locally installable proof build
- `release` - minified, resource-shrunk, and locally signed release build

## Product boundary

- This app is a direct SSH monitor. It is not a server-side agent.
- The app does not expose an interactive terminal.
- Signing, auth material, and other local secrets must stay outside the repo.
