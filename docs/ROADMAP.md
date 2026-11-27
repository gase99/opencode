# Roadmap

Status legend: ✅ done · 🚧 in progress · 📋 planned

## M1 — Foundation & data plane ✅ (v0.1.0)

- ✅ Project scaffold, CI (build/lint/tests), release workflow
- ✅ Source Registry: CRUD, fetch pipeline (ETag, rate limits, SSRF guard)
- ✅ Parsers: VMess/VLESS/Trojan/SS (+ Hysteria2/TUIC behind a gate), JSON outbounds
- ✅ Dedup hashing, validation, field-encrypted storage (SQLCipher + Keystore)
- ✅ Health probes (DNS/TCP/TLS), ScoreEngine, history
- ✅ Operator profiles (MCI/Irancell/Rightel/Other) + honest measurement labeling
- ✅ Compose UI (fa/en, RTL), settings, sources editor, redacted diagnostics
- ✅ VpnService engine + ProxyCore abstraction + honest stub core

## M2 — Real proxy core 📋

- [ ] Choose core: sing-box (GPL-3 — license review needed) vs Xray-core (MPL-2.0)
- [ ] JNI wrapper implementing `ProxyCore` (`docs/CORE_INTEGRATION.md`)
- [ ] Config builder from `ConfigProfile` (in-memory only)
- [ ] Traffic stats UI + connection log (redacted)
- [ ] Device test matrix + emulator CI smoke (stub core)

## M3 — Hardening & store readiness 📋

- [ ] Play "VPN apps" policy review + privacy declarations + local-law review
- [ ] Enable R8/minification + baseline profile
- [ ] Instrumented tests (device farm / emulator in CI)
- [ ] Clash YAML subscriptions
- [ ] Stale-config markers, per-source pruning UX
- [ ] a11y audit + larger-font layouts

## M4 — Community 📋

- [ ] F-Droid metadata (if policy-compatible)
- [ ] Per-app routing behind an explicit user setting
- [ ] Import/export of the Source Registry (JSON, secrets-free by definition)
