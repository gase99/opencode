package com.confighub.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.confighub.app.MainActivity
import com.confighub.app.R
import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.util.SafeLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground VpnService hosting the session.
 *
 * * started ONLY by [VpnSessionManager] after user consent;
 * * START_NOT_STICKY → the system never silently restarts it;
 * * with the stub core it shows an honest message and closes — NO tunnel is ever
 *   opened (`establish()` is unreachable while `core.isReal == false`);
 * * the real-core path below is fully written and becomes active once a real
 *   ProxyCore implementation is registered (docs/CORE_INTEGRATION.md).
 */
@AndroidEntryPoint
class ConfigHubVpnService : VpnService() {

    @Inject
    lateinit var sessionManager: VpnSessionManager

    @Inject
    lateinit var coreRegistry: CoreRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnel: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.vpn_state_connecting)),
                )
                val hash = intent.getStringExtra(EXTRA_HASH)
                scope.launch { handleConnect(hash) }
            }

            ACTION_DISCONNECT -> scope.launch { handleDisconnect() }

            else -> stopSelf()
        }
        // NEVER restart silently — a dead session stays dead until the user acts.
        return START_NOT_STICKY
    }

    private suspend fun handleConnect(hash: String?) {
        if (hash == null) {
            finishWithFailure("missing config hash")
            return
        }
        val profile = sessionManager.resolveProfile(hash)
        if (profile == null) {
            finishWithFailure("config not found")
            return
        }
        updateNotification(sessionTitle(profile))

        val core = coreRegistry.get()
        if (!core.isReal) {
            // Stub path: run the mock to surface its honest "not integrated" result.
            val stubTun = TunParams(
                vpnFd = -1,
                mtu = 0,
                sessionName = profile.host,
                vpnAddress = "",
                vpnDns = "",
            )
            val result = core.start(profile, stubTun) { event ->
                SafeLog.d(TAG, "core event ${event.type} ${event.message ?: ""}")
            }
            when (result) {
                is CoreStartResult.NotAvailable -> finishWithFailure(result.reason)
                is CoreStartResult.Failure -> finishWithFailure(result.error.message ?: "core failure")
                CoreStartResult.Started -> {
                    // Unreachable for the mock — kept for symmetry.
                    sessionManager.onCoreStarted(hash, sessionTitle(profile))
                }
            }
            return
        }

        // ---- Real-core path (active only when a real ProxyCore is registered) ----
        val tun = establishTunnel(profile)
        if (tun == null) {
            finishWithFailure("VPN interface was rejected (consent revoked?)")
            return
        }
        tunnel = tun
        val params = TunParams(
            vpnFd = tun.fd,
            mtu = MTU,
            sessionName = "ConfigHub · ${profile.host}",
            vpnAddress = VPN_ADDRESS,
            vpnDns = VPN_DNS,
        )
        val started = core.start(profile, params) { event ->
            when (event.type) {
                CoreEventType.ERROR -> SafeLog.w(TAG, "core error: ${event.message ?: "-"}")
                else -> SafeLog.d(TAG, "core event ${event.type}")
            }
        }
        when (started) {
            CoreStartResult.Started -> sessionManager.onCoreStarted(hash, sessionTitle(profile))
            is CoreStartResult.NotAvailable -> finishWithFailure(started.reason)
            is CoreStartResult.Failure -> finishWithFailure(started.error.message ?: "core failure")
        }
    }

    /**
     * Opens the VPN interface. ONLY reachable when a real core is registered
     * (`core.isReal == true`) — the mock path above returns earlier.
     */
    private fun establishTunnel(profile: ConfigProfile): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("ConfigHub · ${profile.host}")
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute("0.0.0.0", 0)
        // Per-app routing is intentionally NOT implemented in v0.1; it must be added
        // only behind an explicit, user-configured setting (docs/ROADMAP.md).
        return runCatching { builder.establish() }.getOrNull()
    }

    private suspend fun handleDisconnect() {
        val core = coreRegistry.get()
        runCatching { core.stop() }
        runCatching { tunnel?.close() }
        tunnel = null
        sessionManager.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun finishWithFailure(reason: String) {
        sessionManager.onCoreFailed(reason)
        updateNotification(reason)
        delay(1500)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sessionTitle(profile: ConfigProfile): String =
        "${profile.protocol.wireName.uppercase()} · ${profile.host}"

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = Intent(this, ConfigHubVpnService::class.java)
            .setAction(ACTION_DISCONNECT)
        val disconnectPending = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.confighub.app.R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.vpn_notification_disconnect), disconnectPending)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        // If the service dies unexpectedly the session state is reset — never left
        // pretending to be connected, and never re-spawned (START_NOT_STICKY).
        sessionManager.onDisconnected()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.confighub.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.confighub.app.action.DISCONNECT"
        const val EXTRA_HASH = "hash"
        private const val CHANNEL_ID = "vpn_session"
        private const val NOTIFICATION_ID = 42
        private const val MTU = 1500
        private const val VPN_ADDRESS = "10.111.222.3"
        private const val VPN_DNS = "10.111.222.4"
        private const val TAG = "ConfigHubVpnService"
    }
}
