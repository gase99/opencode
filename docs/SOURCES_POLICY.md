# Source Policy

ConfigHub aggregates **only what the user explicitly configures**. This document is
the contract for what may and may not become a source. PRs violating it are rejected.

## Allowed sources

A source may be added only if **all** of these hold:

1. **Public & lawful** — the data is published for public use and accessing it is
   legal in your jurisdiction.
2. **Automated access is permitted** — the source's terms, robots.txt, or API docs do
   not forbid automated fetching. GitHub raw files of public repos with a license
   that permits redistribution are the typical good case.
3. **Identified & attributable** — you record the source name and a license/terms
   note in the Source Registry entry.
4. **Rate-limited politely** — ConfigHub already enforces per-host intervals,
   conditional requests (ETag/Last-Modified), payload caps and honest User-Agent
   identification. Do not disable or bypass these.
5. **https** — plain `http://` sources are disabled by default (see Settings).

## Forbidden (hard rule)

* Private Telegram groups/channels, private accounts, or anything behind login.
* Paywalled or subscription-gated content.
* Sources that prohibit automated access in their terms/robots.txt.
* "Config dump" collections of unknown provenance (you can't verify legality).
* Anything requiring the app to impersonate a browser or hide its identity.

## Sample registry

`samples/sources.sample.json` contains **placeholders only**. Import them from
Settings → Sources → "Import sample placeholders"; they arrive **disabled** and must
be replaced with sources you are legally allowed to use.

JSON shape used by the app/import flow:

```json
{
  "sources": [
    {
      "name": "Human-readable name",
      "url": "https://host/path",
      "type": "GITHUB_RAW | SUBSCRIPTION_URL | JSON_API | CUSTOM",
      "licenseNote": "license / terms of the source",
      "refreshIntervalHours": 6
    }
  ]
}
```

## Reviewer checklist for source-related PRs

- [ ] Sample/default sources are placeholders or verifiably lawful and public.
- [ ] License/terms note is required in the UI.
- [ ] No URL shorteners that obscure the real destination.
- [ ] Fetching respects rate limits and identifies itself honestly (User-Agent).
- [ ] SSRF guard untouched (`PrivateAddressGuard`, `SafeDns`, `SafeUrlValidator`).
- [ ] Docs updated when policy changes.
