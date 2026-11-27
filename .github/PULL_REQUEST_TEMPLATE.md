# Pull request checklist

- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes locally or in CI.
- [ ] Tests are added/updated for behavior changes (no fabricated results).
- [ ] No secrets (UUIDs, passwords, keys, keystores) are committed or logged.
- [ ] UI strings are added to **both** `values/strings.xml` and `values-fa/strings.xml`.
- [ ] Any new public source follows `docs/SOURCES_POLICY.md`.
- [ ] Privacy-respecting defaults are preserved (no telemetry, no auto-connect, no boot-start).
- [ ] Conventional commit messages are used (`feat:`, `fix:`, `docs:`, `chore:`, …).
