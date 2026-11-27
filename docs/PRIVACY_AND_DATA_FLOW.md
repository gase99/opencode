# Privacy & Data Flow

Companion to `PRIVACY_POLICY.md` (the user-facing policy). This document maps every
byte: where it comes from, where it is stored, and where it can go.

## Data inventory

| Data | Origin | Storage | Leaves the device? |
| --- | --- | --- | --- |
| Source Registry entries (name, URL, license note, status) | User input | Room (SQLCipher) | Only to the host of the URL when fetching |
| Config profiles (protocol, host, port, params) | Parsed from sources | Room (SQLCipher) | Only to the config's server during an explicit health probe or VPN session |
| Config credentials (UUID/password/key) | Parsed from sources | Room — field-encrypted AES-GCM (Keystore-wrapped) | Only to the config's server at connect time |
| Favorites | User taps | Room (SQLCipher) | Never |
| Health samples (latencies, ok/error, network type, PLMN) | Probes run by the app | Room (SQLCipher) | Never |
| Settings, operator profile, weights | User input | DataStore (no secrets) | Never |
| Diagnostics report | Generated on demand | App cache file | Only when the user shares it (redacted) |

## Network activity — exhaustive list

1. **Source refresh** (manual pull-to-refresh, per-source test, or periodic worker):
   HTTPS GET to sources the user configured, with `User-Agent: ConfigHub/<ver>`
   and conditional headers. Redirects are followed but SafeDns refuses private
   addresses at every hop.
2. **Health checks** (user-tapped "Test now" or periodic worker):
   DNS resolution + TCP connect ×3 + TLS handshake (when applicable) to the
   config's server. No payload is sent beyond the handshake.
3. **VPN session** (only after user consent + connect): traffic is routed through
   the selected config's server, like any VPN client.

Nothing else. No analytics, no telemetry, no crash reporting, no updates check.

## Redaction guarantees

* `SecretMasker.sanitize()` wraps ALL log output (`SafeLog` is the only logger).
* `ConfigProfile.toString()` never contains credential fields.
* `DiagnosticsExporter` never reads credentials at all; source URLs pass through
  `UrlRedactor` (query/fragment/token-like path segments removed).
* Exception messages surfaced in UI/history are sanitized.

## Permissions justification

| Permission | Why | What we do NOT use |
| --- | --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | fetch/probe/network-type | — |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | visible VPN session notification | — |
| `POST_NOTIFICATIONS` | show the session notification (runtime-requested; denial is graceful) | — |
| `BIND_VPN_SERVICE` | system-granted so `VpnService` can create a tunnel **after user consent** | — |

Not requested: `RECEIVE_BOOT_COMPLETED`, `READ_PHONE_STATE`, location, contacts,
storage, accessibility. There is no boot receiver: no VPN ever starts by itself.

## Operator detection

Uses the non-restricted `TelephonyManager.getNetworkOperator()` (MCC/MNC string).
If unavailable → the user selects a profile manually. The value never leaves the
device; it is stored only inside health samples to label **where** a measurement
was taken.
