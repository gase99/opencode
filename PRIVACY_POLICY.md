# Privacy Policy — ConfigHub

_Last updated: 2026-11-26 · Version 1.0_

## Summary (short version)

**ConfigHub collects nothing.** The app has no analytics, no telemetry, no crash
reporting service, and no third-party tracking SDKs. All data lives on your device.
Your configuration secrets are encrypted at rest and are never included in logs.

خلاصه‌ی فارسی در انتهای این سند آمده است.

## 1. Who this policy applies to

This policy applies to the ConfigHub Android application ("the app") as published
from the official project repository. If you obtained the app from another channel,
that distributor's policy may differ.

## 2. Data the app does NOT collect

The app does **not** collect, transmit, or sell:

* Personal data (name, email, phone number, contacts, photos, location).
* Your proxy configuration secrets (UUIDs, passwords, private keys, SS method/password).
* Usage analytics, device fingerprints, advertising IDs, or crash reports.
* The content of your traffic when a VPN session is active.

There is **no network connection initiated by the app that is not user-initiated or
a scheduled refresh/health-check of the public sources you explicitly configured.**

## 3. Data stored on your device

| Data | Where | Protection |
| --- | --- | --- |
| Config profiles (from sources you added) | Room database | Database encrypted with SQLCipher; key wrapped by Android Keystore |
| Favorites | Same encrypted database | Same |
| Health-check history (latency samples, timestamps) | Same encrypted database | Same |
| Settings & operator profiles | DataStore (preferences) | Non-sensitive; no secrets stored |
| Source Registry entries (URLs, notes, status) | Same encrypted database | Same |

Uninstalling the app removes all of the above.

## 4. Network activity performed by the app

The app performs network requests only in these cases:

1. **Source refresh** — fetching public configuration sources **you** explicitly added
   or enabled. Requests include a plain user-agent identifying the app.
2. **Health checks** — TCP connect / TLS handshake / DNS resolution probes against
   the servers of configs you chose to test. This happens only on demand or by the
   periodic health-check you enabled.
3. **VPN session** — while you explicitly connect, your device traffic is routed
   through the proxy server of the config you selected, exactly like any VPN client.

The app **never** probes or connects to anything you did not configure, and it
**refuses** to fetch from or probe local/private network addresses (SSRF protection).

## 5. Permissions and why we need them

| Permission | Purpose |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Fetch sources, run health checks, detect network type |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the visible VPN-session notification while connected |
| `POST_NOTIFICATIONS` | Show the mandatory VPN session notification (you can deny; connection still works, Android shows the notification itself) |
| `BIND_VPN_SERVICE` | Granted by the system so the app can create a VPN tunnel — **only** after you approve the standard Android VPN consent dialog |

There is **no** `RECEIVE_BOOT_COMPLETED`: the app never starts a VPN automatically.
There is **no** `READ_PHONE_STATE`: operator detection uses the non-restricted
`TelephonyManager.getNetworkOperator()` API and falls back to a manual profile you select.

## 6. Operator detection

To rank configs for your mobile operator, the app reads the public MCC/MNC operator
code from the system. This value is processed **on-device only** and never leaves it.
If the code cannot be read, you pick an operator profile manually (MCI/Hamrah-e Aval,
Irancell, Rightel, or Other).

## 7. Diagnostics export

The in-app "Export diagnostics" feature creates a text report in which:

* Config secrets are fully redacted (never printed).
* Source URLs are reduced to scheme + host (token-like path segments removed),
  because subscription URLs often embed access tokens.

You choose where the report is shared. Nothing is uploaded automatically.

## 8. Children

The app is a technical tool and is not directed at children under 13.

## 9. Changes to this policy

Changes are published in the repository with an updated date. Material changes are
noted in release notes.

## 10. Contact

Open an issue in the official repository, or contact the repository owner through
GitHub. Do **not** include secrets in reports.

---

## خلاصه‌ی فارسی

* کانفیگ‌هاب **هیچ داده‌ای جمع‌آوری نمی‌کند**: بدون آنالیتیکس، بدون تلمتری، بدون گزارش خرابی، بدون SDK شخص ثالث.
* همه‌ی داده‌ها (کانفیگ‌ها، علاقه‌مندی‌ها، تاریخچه‌ی تست، تنظیمات) فقط روی گوشی شما ذخیره می‌شود و پایگاه‌داده با SQLCipher رمزنگاری شده است.
* رمزها و کلیدهای کانفیگ‌ها هرگز در لاگ‌ها و خروجی عیب‌یابی دیده نمی‌شوند (ماسک/حذف کامل).
* اتصال VPN فقط با اقدام صریح شما و بعد از تأیید در پنجره‌ی مجوز استاندارد اندروید شروع می‌شود؛ هرگز بعد از روشن‌شدن گوشی به‌صورت خودکار وصل نمی‌شود.
* درخواست‌های شبکه فقط برای رفرش منابعی است که خودتان اضافه کرده‌اید و تست سلامتی کانفیگ‌هایی که خودتان انتخاب کرده‌اید.
* تشخیص اپراتور فقط با کد عمومی MCC/MNC و روی خود دستگاه انجام می‌شود؛ در غیر این‌صورت پروفایل اپراتور را دستی انتخاب می‌کنید.
