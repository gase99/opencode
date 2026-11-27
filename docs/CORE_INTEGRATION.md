# Integrating a real proxy core (sing-box / Xray-core)

The v0.1 builds ship an **honest stub core** (`MockProxyCore`): the entire VPN UX,
consent flow and service lifecycle work, but **no tunnel is ever opened** and the UI
says so. Replacing the stub is a contained, well-defined task because the VPN layer
only talks to the `ProxyCore` interface (`vpn/ProxyCore.kt`).

## Contract recap

```kotlin
interface ProxyCore {
    val id: String            // e.g. "sing-box"
    val isReal: Boolean       // true — enables the real tunnel path in the service
    val status: StateFlow<CoreStatus>
    suspend fun start(profile: ConfigProfile, tun: TunParams, listener: (CoreEvent) -> Unit): CoreStartResult
    suspend fun stop()
    fun trafficStats(): Flow<TrafficStats>?
    fun lastError(): CoreError?
}
```

`TunParams.vpnFd` is the TUN file descriptor produced by
`VpnService.Builder.establish()`; `ConfigHubVpnService` already:

1. runs the mandatory consent flow,
2. calls `establish()` **only** when `core.isReal == true`,
3. passes the fd + MTU + address/DNS to `core.start(...)`,
4. exposes `VpnService.protect(socket)` to the core via your implementation
   (pass the `VpnService` instance into your core's constructor when you register it
   in DI, or inject an interface exposing `protect()`).

## Step-by-step (sing-box example)

1. **Licensing**: sing-box is GPL-3.0. Linking a GPL-3 core affects distribution terms
   of the combined app — consult the project owner before shipping. Xray-core is
   MPL-2.0 (more permissive for linking).
2. **Native build**: build the core library for `arm64-v8a`, `armeabi-v7a`, `x86_64`
   (and optionally `x86`) with JNI bindings, e.g. `libcoresingbox.so`, and place the
   `.so` files under `app/src/main/jniLibs/<abi>/`.
3. **Kotlin wrapper**: implement `SingBoxCore : ProxyCore`:
   * `start()` → `System.loadLibrary("coresingbox")` once, serialize the config
     profile to the engine's JSON format (build it from `ConfigProfile` — never
     persist it), call the JNI entry with `tun.vpnFd`, `mtu`, and the config.
   * Report `CoreEvent`s; map failures to sanitized `CoreError`s.
   * `stop()` → engine shutdown; set `CoreStatus.IDLE`.
4. **DI**: `@Binds abstract fun bindCore(impl: SingBoxCore): ProxyCore` and make
   `CoreRegistry` take the real core (keep the mock registered for CI/emulators —
   a build flavor `mockCore` is a nice pattern).
5. **Protect sockets**: call `VpnService.protect()` for every outbound socket the
   engine creates — otherwise traffic loops.
6. **Testing**: emulator smoke test (connect → `ipinfo`-style check in browser),
   disconnect/reconnect, consent revoked mid-flight, airplane mode, battery saver.

## Acceptance criteria for replacing the stub

- [ ] `core.isReal == true` only when native libs actually load (fail → mock fallback).
- [ ] No tunnel is established unless `core.start` reports success — otherwise
      `establish()` would blackhole all traffic.
- [ ] Config JSON is built in memory; secrets are never written to disk/logs.
- [ ] `CoreError` messages are sanitized (no credentials, no full config dump).
- [ ] START_NOT_STICKY behavior preserved (no silent reconnect).
- [ ] Tests: wrapper unit tests with a fake engine + manual device matrix in
      `docs/RELEASE_CHECKLIST.md`.
