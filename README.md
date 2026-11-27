# ConfigHub

**Aggregate • Validate • Connect** — a privacy-first Android client that collects
proxy/VPN configuration profiles from a **configurable registry of public, lawful
sources**, validates and analyzes them, measures real network health (DNS + TCP + TLS —
no fake ICMP "ping"), ranks them with operator-aware scoring, and connects through an
Android `VpnService`-based session behind a swappable proxy-core interface.

> ### ⚠️ Legal notice — read this first
> * ConfigHub is a **generic client tool**. It ships **no** proxy configs and does
>   **not** claim to index "all configs on the internet". It fetches only from
>   public, lawful, publicly accessible sources **you** explicitly add or enable
>   (see [docs/SOURCES_POLICY.md](docs/SOURCES_POLICY.md)).
> * You are responsible for what you configure and connect to. Bypassing
>   restrictions may be illegal where you live. Comply with the licenses, terms,
>   robots.txt and rate limits of every source you add.
> * The project intentionally does **not** publish to Google Play until all store
>   VPN policies and applicable local laws are satisfied ([ROADMAP M3](docs/ROADMAP.md)).
> * **No hidden VPN, ever**: every session is user-initiated, passes the mandatory
>   Android VPN consent dialog, runs with a visible notification, and the app never
>   starts or restarts a session by itself (no boot receiver, START_NOT_STICKY).
> * **v0.1.0 ships a stub proxy core** — the full engine/UX work, but connecting
>   reports honestly that no native core is integrated yet and **opens no tunnel**.
>   See [docs/CORE_INTEGRATION.md](docs/CORE_INTEGRATION.md).

## Features (implemented in v0.1.0)

| Area | What works |
| --- | --- |
| Source Registry | Add/edit/enable/remove public sources with license notes, per-source intervals, ETag/Last-Modified caching, per-host rate limiting, payload caps, honest User-Agent |
| Parsing | VMess (base64 JSON), VLESS, Trojan, Shadowsocks (SIP002 + legacy), V2Ray JSON outbounds, base64/text subscription blobs; Hysteria2/TUIC parsed behind an explicit opt-in gate |
| Validation | Host/port/secret policy, unencrypted SS cipher rejection, private-address rejection, REALITY completeness, stable dedup hashing (secrets only as SHA-256) |
| Health checks | DNS resolution time, TCP connect ×3 (mean/min/jitter), TLS handshake with SNI + hostname verification (REALITY skipped honestly), per-sample operator & network labels |
| Ranking | Score = latency · jitter · reliability · recency (· optional protocol), normalized configurable weights, per-operator thresholds (MCI / Irancell / Rightel / Other), honest "measured on current network" labeling |
| UI | Home with search/filters/sort/pull-to-refresh, details with masked secrets + score breakdown + history, settings (intervals, operator, weights, privacy toggles), source editor, **full Persian localization (RTL)** |
| Connection | VpnService foreground session, mandatory consent dialog, one-tap connect/disconnect, no auto-reconnect — with an honest stub core |
| Privacy | SQLCipher-encrypted DB + Keystore-wrapped field encryption, secrets masked in UI/logs/exports, zero telemetry, redacted diagnostics export |

## Why TCP/TLS latency instead of "ping"?

Android restricts raw socket operations for unprivileged apps: ICMP echo (ping)
requires root or unusual permissions. ConfigHub therefore measures **DNS resolution
time**, **TCP connect latency** and **TLS handshake latency** — a far better
predictor of whether a proxy will actually work. The UI labels every measurement as
*measured on the current network*; the app never pretends to measure another
operator's network without a real connection there
([details](docs/OPERATOR_PROFILES.md)).

## Architecture (one minute)

```
Sources ──fetch──► decode ──► parse ──► validate ──► dedup-hash ──► Room (SQLCipher,
                                                                     secrets field-encrypted)
                                        │
                    HealthChecker (DNS/TCP/TLS) ──► history + ScoreEngine ──► ranking
                                        │
                        VpnSessionManager ──consent──► ConfigHubVpnService ──► ProxyCore
                                                                              (mock │ sing-box/xray later)
```

Layers: `ui/` (Compose + MVVM) · `vpn/` (session engine) · `data/` (Room, DataStore,
OkHttp, WorkManager) · `domain/` (probes, scoring, operators) · `core/`
(framework-free parsing/security). Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Build & test

```bash
# Requirements: JDK 17, Android SDK (platform 35)
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit + Robolectric tests
./gradlew lintDebug              # Android lint
```

CI (GitHub Actions) runs build + lint + tests on every PR and attaches the debug APK;
version tags publish debug + release APK/AAB to
[GitHub Releases](../../releases) (signed only when the owner provides signing
secrets — [release checklist](docs/RELEASE_CHECKLIST.md)).

## Privacy at a glance

* No analytics, no telemetry, no crash-reporting SDKs — nothing phones home.
* Configs/favorites/history live in a SQLCipher database; credentials are
  additionally field-encrypted with an Android-Keystore-wrapped AES-GCM key.
* No `RECEIVE_BOOT_COMPLETED`, no `READ_PHONE_STATE`, no accessibility tricks, no root.
* Diagnostics export removes secrets and token-izes source URLs.

Full policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md) (includes a Persian summary) ·
data-flow table: [docs/PRIVACY_AND_DATA_FLOW.md](docs/PRIVACY_AND_DATA_FLOW.md) ·
security reporting: [SECURITY.md](SECURITY.md).

## Documentation index

| Document | Content |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, data flow, key decisions |
| [docs/SOURCES_POLICY.md](docs/SOURCES_POLICY.md) | What may/may not be a source + sample registry |
| [docs/PRIVACY_AND_DATA_FLOW.md](docs/PRIVACY_AND_DATA_FLOW.md) | Where every byte goes |
| [docs/OPERATOR_PROFILES.md](docs/OPERATOR_PROFILES.md) | Operator detection honesty, PLMN table |
| [docs/CORE_INTEGRATION.md](docs/CORE_INTEGRATION.md) | Plugging sing-box/Xray-core behind `ProxyCore` |
| [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) | Owner steps for signed releases |
| [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) | Honest list of what does not work yet |
| [docs/SECURITY_NOTES.md](docs/SECURITY_NOTES.md) | Threat model & design invariants |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Milestones M1–M4 |
| [samples/sources.sample.json](samples/sources.sample.json) | Placeholder source registry |

## Contributing

Issues and small, reviewable PRs — see [CONTRIBUTING.md](CONTRIBUTING.md) and the
[issue tracker](../../issues) for the epic/task board. Every change ships with tests;
test results are never fabricated (CI runs the real build).

## License

[Apache License 2.0](LICENSE). This project is developed with an AI-agent-assisted
workflow; all code is human-reviewable through the PR history.
