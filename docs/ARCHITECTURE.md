# Architecture

ConfigHub is a single-module Android app organized by clean-architecture packages.
Everything under `core/` and `domain/` is framework-free (plain Kotlin/JVM) so the
most important logic is unit-testable without Android.

## Layer map

```
ui/          Compose screens + ViewModels (MVVM, StateFlow, Hilt)
vpn/         VpnService session engine + ProxyCore abstraction + MockProxyCore
data/        Room (SQLCipher) · DataStore settings · OkHttp fetcher · WorkManager workers
domain/      HealthChecker (TCP/TLS/DNS probes) · ScoreEngine · Operator profiles
core/        Framework-free: parsing, dedup hashing, validation, security helpers (masking,
             URL redaction, SSRF guard), SafeLog
di/          Hilt modules (database, network, crypto, health, fetch policy)
```

## Data flow

```
Source Registry (user-configured public sources)
        │  OkHttp + SafeDns (SSRF guard) + ETag/Last-Modified + rate limiting
        ▼
SubscriptionDecoder ──► ConfigLinkParser ──► ConfigValidator ──► DedupHasher
        │                                                      │
        ▼                                                      ▼
  candidates (URIs / JSON outbounds)            ConfigEntity (Room, secrets field-encrypted
                                                AES-GCM with a Keystore-wrapped key)
        │
        ▼
HealthChecker (DNS → TCP×3 → TLS)  ──►  HealthSampleEntity + ScoreEngine
        │                                       │
        ▼                                       ▼
  Home ranking (operator thresholds & weights)  Detail breakdown
        │
        ▼
VpnSessionManager ──(mandatory VpnService.prepare consent)──► ConfigHubVpnService
        └── ProxyCore (mock today; sing-box / Xray-core later — see CORE_INTEGRATION.md)
```

## Key decisions

| Decision | Why |
| --- | --- |
| Single Gradle module, package-based layering | Keeps CI simple; boundaries enforced by review & lint rules later |
| `core/` + `domain/` are pure JVM | Fast, honest unit tests; no Robolectric needed for parsing/scoring math |
| Secrets never stored as plain columns | Field-level AES-GCM encryption (Keystore-wrapped) + SQLCipher for the DB file |
| One manual `toString()` on `ConfigProfile` | Prevents accidental secret leaks via logs/exceptions |
| Stable dedup hash = SHA-256 over protocol+host+port+SHA-256(secret)+params | Dedup without exposing secrets; tag-only differences collapse |
| TCP/TLS/DNS probes (no ICMP) | Android forbids raw ICMP for normal apps; TCP/TLS latency predicts usability better |
| `ProxyCore` interface + mock | The VPN engine is complete and reviewable without native binaries |
| OkHttp only (no Retrofit) | Full control over conditional requests, rate limiting and size caps |
| WorkManager for refresh/health, never for VPN | VPN sessions are strictly user-initiated |

## Threading

* Repositories are `suspend`-based; network/DB on `Dispatchers.IO` where blocking.
* ViewModels expose `StateFlow` via `stateIn(WhileSubscribed(5s))`.
* The VPN service runs its own `CoroutineScope`; `onDestroy` cancels it.

## Test strategy

* **Unit (JVM)**: parsers, decoders, dedup, scoring, masking/redaction, SSRF guard,
  mock core, rate limiter.
* **Robolectric**: Room pipeline end-to-end (MockWebServer), settings store, health
  repository, diagnostics redaction, Compose component smoke tests.
* **CI**: `./gradlew assembleDebug testDebugUnitTest lintDebug` on every PR.
* Instrumented (device) tests are future work — see ROADMAP.
