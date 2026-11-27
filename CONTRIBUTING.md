# Contributing to ConfigHub

Thanks for your interest in contributing! This project is developed with an
AI-agent-assisted workflow, but every merged change is reviewable and traceable.

## Development environment

* JDK 17 (Temurin recommended)
* Android SDK with platform 35 (installed automatically by Android Studio, or set
  `ANDROID_HOME` / `local.properties`)
* `./gradlew assembleDebug testDebugUnitTest lintDebug` must pass before opening a PR.

## Workflow

1. Check the issue tracker; pick an existing issue or open a discussion first for
   bigger ideas.
2. Branch from `main`: `feat/<short-name>`, `fix/<short-name>`, or `docs/<short-name>`.
3. Keep PRs small and reviewable — one concern per PR.
4. Every behavior change needs a test. Never fabricate test results — CI runs the
   real build and tests.
5. Use conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`,
   `test:`, `ci:`.
6. Update documentation (`README.md`, `docs/*`) when behavior, architecture, or
   policy changes.

## Project rules (non-negotiable)

* **Privacy first**: no telemetry, no secrets in logs, no hidden VPN behavior.
* **Source policy**: new sources must satisfy `docs/SOURCES_POLICY.md`.
* **Strings**: user-facing text must exist in both English (`values/`) and Persian
  (`values-fa/`) resource files.
* **Licensing**: by contributing you agree your work is released under the project's
  Apache-2.0 license.

## Code style

* Kotlin official style; 4-space indent; trailing commas allowed.
* Clean architecture: `core/` (framework-free), `data/` (persistence/network),
  `domain/` (models, scoring, health), `ui/` (Compose), `vpn/` (connection engine).
* Prefer Flow + coroutines; inject dispatchers; keep classes constructor-injectable
  (Hilt) for testability.

## Reporting bugs & security issues

See `SECURITY.md` (security) — everything else goes through the issue templates.
