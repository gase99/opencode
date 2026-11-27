# Known Limitations (v0.1.0 — honest list)

## Connection engine

1. **No real proxy core is integrated yet.** The shipped core is a stub
   (`MockProxyCore`) that reports "not integrated" and never opens a tunnel. The
   whole engine, consent flow and UI work; connecting for real requires the
   integration described in `docs/CORE_INTEGRATION.md`.
2. No per-app routing (split tunneling) yet — future, opt-in only.
3. No traffic statistics UI (the interface exists; the stub reports none).

## Health checks

4. **No ICMP/ping.** Android forbids raw sockets for normal apps; ConfigHub uses
   DNS/TCP/TLS timing — which is a better proxy-usability predictor anyway.
5. REALITY configs skip the TLS handshake probe (a generic handshake is meaningless
   against REALITY) — recorded as `tlsProbed=false`, not as a failure.
6. TLS probes always verify certificates; configs with `allowInsecure` may show
   TLS failures here even though the real client might accept them.
7. TCP latency depends on the CURRENT network only — see the operator honesty note
   in `docs/OPERATOR_PROFILES.md`.

## Parsing / sources

8. Clash YAML subscriptions are not parsed (text/base64/JSON are). Planned.
9. Double-encoded base64 payloads are handled to 2 layers; exotic encodings may fail.
10. Configs removed from a source remain in the cache (only source deletion clears
    them). A "stale" marker is planned.

## Storage

11. If AndroidKeyStore or SQLCipher is unavailable on a device, the app degrades to
    an **unencrypted** local database and warns in Settings (documented fallback in
    `SecretStore`). Field-level encryption falls back the same way.
12. Robolectric tests cannot exercise SQLCipher/Keystore; the encryption-at-rest path
    is verified on real devices per `docs/RELEASE_CHECKLIST.md`.

## Platform

13. `lint` warnings-as-errors is off; the warning backlog is a roadmap item.
14. Instrumented (on-device) tests are not yet automated in CI.
15. WorkManager periodic jobs are subject to OS battery optimization — the app does
    not fight it (no foreground persistent worker); manual refresh always works.
