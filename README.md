# Nodex-Android

Nodex-Android is an open-source Android client for direct SSH monitoring of Linux servers. It covers live metrics, services, alerts, history, and Docker or Podman status. No billing. No telemetry. No account lock-in.

## Canonical docs

- [Agent guide](./AGENTS.md)
- [Architecture](./ARCHITECTURE.md)
- [Product contract](./docs/PRODUCT_CONTRACT.md)
- [Android/iOS parity notes](./docs/ANDROID_IOS_PARITY.md)
- [Smoke checklist](./docs/SMOKE_CHECKLIST.md)
- [Validation](./docs/VALIDATION.md)

## Repo map

- `app/` - Android app, tests, Room schemas, and build variants
- `app/src/main/java/com/nodex/client/core/` - SSH, parsing, and shared runtime code
- `app/src/main/java/com/nodex/client/data/` - persistence and repositories
- `app/src/main/java/com/nodex/client/domain/` - domain models and use cases
- `app/src/main/java/com/nodex/client/di/` - Hilt wiring
- `app/src/main/java/com/nodex/client/ui/` - Compose screens and app entry flow
- `scripts/` - local emulator QA helpers
- `branding/` - product art only

## Local build

```bash
source ../build-env.sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Notes

- Build variants are `debug`, `dev`, `releaseProof`, and `release`.
- `release` is minified and shrunk. Use your own local signing setup outside source control.
- The app is monitor-only. It does not expose an interactive terminal.
- The app has no paywall, no subscriptions, and no billing flow.
- The app sends no telemetry.
- Credentials stay on-device. Keep signing files, `.env` files, API keys, and machine-local config out of the repo.
- The repo is meant to stay public. Do not add closed-source dependencies, endpoints, or docs.
