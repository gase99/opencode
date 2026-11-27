# Security Policy — ConfigHub

## Supported versions

| Version | Supported |
| --- | --- |
| 0.1.x | ✅ |

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Use GitHub's *Report a vulnerability* button (Security → Advisories) on the
repository, or contact the repository owner directly through GitHub.

You will receive a response within 7 days. Please include:

* Affected version / commit.
* Reproduction steps (with **redacted** logs — never paste real secrets).
* Impact assessment.

## Security design promises

The following are security-relevant invariants of this project. Violations of these
in a PR are considered security bugs:

1. **Secrets are never logged.** Config UUIDs, passwords, private keys, and SS
   method/password pairs are masked in the UI and fully redacted in logs and
   exported diagnostics.
2. **Secrets are encrypted at rest.** The Room database is encrypted with SQLCipher;
   the database key is wrapped with an Android Keystore AES-GCM key. Config secrets
   stored in the database are additionally encrypted per-field.
3. **No hidden VPN.** Every VPN session is user-initiated, passes through the
   mandatory Android `VpnService` consent dialog, and runs as a foreground service
   with a visible notification. There is no boot auto-start and no silent
   auto-reconnect after a user disconnect.
4. **No telemetry.** No analytics, crash reporting, or tracking SDKs.
5. **SSRF protection.** Source fetching and health probing refuse to target
   loopback, link-local, private (RFC 1918), CGNAT, and ULA addresses.
6. **Least data.** No `READ_PHONE_STATE`, no `RECEIVE_BOOT_COMPLETED`, no
   `ACCESS_FINE_LOCATION`. Operator detection uses the non-restricted
   `getNetworkOperator()` API with a manual-profile fallback.

## Out of scope

* Vulnerabilities introduced by sources the **user** explicitly added (the app
  cannot guarantee the safety of third-party published configs — review what you add).
* Issues requiring a compromised device or root.
* Issues in unmodified upstream dependencies; report those upstream and link us.
