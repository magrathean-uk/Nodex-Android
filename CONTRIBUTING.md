# Contributing to Nodex-Android

Thank you for taking the time to contribute. Nodex-Android is an open-source Android client for direct SSH monitoring of Linux servers.

## License

By submitting a pull request you agree that your contribution is licensed under the [MIT License](./LICENSE) — the same license as the project. You retain copyright on your own contributions; you are simply granting everyone the same permissions the MIT License provides.

## Ground rules

These are the product boundaries that every contribution must respect:

- The app is **monitor-only**. It must not expose an interactive terminal or arbitrary command execution.
- The app must have **no telemetry**, analytics, or any outbound call except direct SSH to user-configured servers.
- The app must have **no billing, subscription, or paywall** of any kind.
- Credentials stay **on-device**. Do not add any code that transmits credentials or key material to external services.
- The repo is meant to stay fully public. Do not add closed-source dependencies, private endpoints, or internal documentation.

These rules exist because they are the core promise of the project to its users.

## How to contribute

### Reporting bugs

Open a GitHub issue. Include:

- Android version and device model (or emulator API level)
- Steps to reproduce
- What you expected vs. what happened
- Relevant logs if available (filter by `Nodex` tag in Logcat)

### Suggesting features

Open a GitHub issue describing the feature and the problem it solves. Check that it fits within the product boundaries above before submitting.

### Submitting code

1. Fork the repo and create a branch from `main`.
2. Read [ARCHITECTURE.md](./ARCHITECTURE.md) and [AGENTS.md](./AGENTS.md) before writing code.
3. Keep changes scoped. One logical change per pull request.
4. Preserve the package split: `core`, `data`, `domain`, `di`, `ui`, `testing`.
5. Do not weaken lifecycle-aware polling or sudo-over-stdin behaviour without covering tests.
6. Run tests before opening a PR:

   ```bash
   source ../build-env.sh
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

7. Open a pull request against `main`. Describe what changed and why.

## Code style

- Kotlin only — no Java source files in `app/src/main`.
- Follow the existing naming and structure conventions visible in the codebase.
- No copyright headers in individual source files; the root `LICENSE` covers the whole repo.
- No comments that explain *what* the code does — only add a comment when the *why* is non-obvious.

## What we will not merge

- Telemetry, analytics, or crash reporting SDKs
- Billing or subscription libraries
- Interactive terminal / shell execution features
- Closed-source or proprietary dependencies
- Changes that move credentials off-device
- Signing files, API keys, or machine-local configuration

## Questions

Open a GitHub issue with the `question` label if you are unsure about anything before starting work.
