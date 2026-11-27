# Release Checklist

The **human owner** performs the signing and final publishing decisions. The agent
prepares everything else.

## 0. Prerequisites (owner)

- [ ] Create a release keystore **outside** the repository:
      `keytool -genkey -v -keystore confighub-release.keystore -alias confighub -keyalg RSA -keysize 4096 -validity 10000`
- [ ] Back the keystore up securely (password manager / offline vault).
- [ ] Add repository secrets (Settings → Secrets and variables → Actions):
      * `ANDROID_KEYSTORE_B64` — `base64 -w0 confighub-release.keystore`
      * `ANDROID_KEYSTORE_PASSWORD`
      * `ANDROID_KEY_ALIAS` (e.g. `confighub`)
      * `ANDROID_KEY_PASSWORD`
- Without these secrets, release artifacts are built **unsigned** (the workflow
  labels them `unsigned`) — useful for review only, never for distribution.

## 1. Pre-tag verification (agent/CI)

- [ ] `main` is green: build + lint + unit tests on the release commit.
- [ ] Version bumped: `app/build.gradle.kts` (`versionName`/`versionCode`).
- [ ] `docs/KNOWN_LIMITATIONS.md` reviewed and accurate.
- [ ] `PRIVACY_POLICY.md` date/version updated if behavior changed.
- [ ] No secrets in the tree (`git log -p | grep -i keystore` sanity check).

## 2. Tag & build

```bash
git tag -a vX.Y.Z -m "ConfigHub vX.Y.Z"
git push origin vX.Y.Z
```

The `Release` workflow builds `assembleDebug assembleRelease bundleRelease`, names
artifacts `ConfigHub-<version>-{debug,release-signed|unsigned}.{apk,aab}` and
publishes a GitHub Release with auto-generated notes.

## 3. Verify the release (owner)

- [ ] APK installs on a real device; first-run consent dialog appears on connect.
- [ ] Notification permission prompt behaves; denial still allows usage.
- [ ] Connect with the stub core → clear "not integrated" message, **no tunnel**.
- [ ] Diagnostics export contains no secrets (grep for a known UUID).
- [ ] Uninstall removes all data (fresh install sees empty state).
- [ ] (After real-core integration) per-operator smoke matrix: Wi-Fi, MCI, Irancell,
      Rightel — record measured-on labels behave.

## 4. Distribution decisions (owner)

- [ ] GitHub Releases only (current plan) — fine for source-available distribution.
- [ ] Google Play: NOT attempted until Play VPN policy review + local-law review are
      complete (see ROADMAP M3). Play requires a VPN declaration, privacy policy URL,
      and the app must comply with the "VPN apps" disclosure requirements.
