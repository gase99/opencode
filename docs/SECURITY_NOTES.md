# Security Notes & Threat Model

## Threat model

| Adversary | Mitigation |
| --- | --- |
| Malicious/compromised source | Schema validation, host/port sanity, unencrypted-cipher rejection, private-address rejection (SSRF guard on URLs AND resolved IPs AND redirects), payload size caps |
| Local attacker reading app storage | SQLCipher DB + Keystore-wrapped keys; field-level AES-GCM for credentials; `allowBackup=false` |
| Log/diagnostics reader | `SafeLog` sanitizes everything; `ConfigProfile.toString()` excludes secrets; diagnostics never read credentials; URLs redacted (`UrlRedactor`) |
| Silent/background VPN abuse | No boot receiver, no auto-reconnect, START_NOT_STICKY service, mandatory consent dialog on every fresh grant, mock core cannot tunnel |
| Traffic analysis by third parties | No analytics/telemetry/crash SDKs at all; honest User-Agent on source fetches only |
| Redirect-based SSRF | SafeDns applied to the OkHttp client → every hop's DNS answers are filtered |

## Invariants (review-blocking if violated)

1. Secrets never appear in logs, exceptions, UI, or exports.
2. Credentials are field-encrypted; DB file encrypted when the platform allows.
3. VPN sessions are user-initiated only; consent dialog always honored; disconnect
   is final; no boot start; no silent restart (START_NOT_STICKY).
4. No telemetry, no third-party SDKs in the release dependency graph.
5. SSRF guard (`PrivateAddressGuard`/`SafeDns`/`SafeUrlValidator`) applies to all
   fetching and probing.
6. TLS probes and fetches use system trust + hostname verification.

## Key management

* `confighub_master` (Keystore AES-256-GCM): wraps the SQLCipher passphrase.
* `confighub_fields` (Keystore AES-256-GCM): per-field credential encryption with
  fresh IVs.
* Passphrase blob (`iv||ct`) lives in `getDir("keys")` — private to the app.
* Loss scenario: if Keystore keys are destroyed (rare factory situations), the DB
  can no longer be decrypted → the app detects `BadPadding`-class failures on open
  and treats the cache as rebuildable (configs re-fetch from sources). No user
  secrets are ever transmitted elsewhere to "recover".

## Dependency policy

* Dependabot (gradle + actions) weekly.
* No third-party analytics/crash/ads libraries are permitted in PRs.
* SQLCipher artifact: `net.zetetic:sqlcipher-android` (BSD-style license, see NOTICE
  in the artifact).

## Reporting

See `SECURITY.md` — use GitHub Security Advisories, do not open public issues for
vulnerabilities.
