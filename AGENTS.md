# AGENTS.md

Read in this order:

- [Root AGENTS](../AGENTS.md)
- [Agent index](../AGENT_INDEX.md)
- [README](./README.md)
- [Architecture](./ARCHITECTURE.md)
- [app build file](./app/build.gradle.kts)

Rules:

- Source `../build-env.sh` before Gradle work.
- Keep changes scoped to this repository and leave generated `build/` output alone.
- Preserve the package split under `core`, `data`, `domain`, `di`, `testing`, and `ui` unless a refactor is explicit.
- Keep `local.properties`, signing values, and machine-local state out of source control.
- Do not weaken lifecycle-aware polling or sudo-over-stdin behavior without tests.
- Use `app/schemas/`, `app/src/test`, and `app/src/androidTest` as the source of truth for persistence and coverage.
