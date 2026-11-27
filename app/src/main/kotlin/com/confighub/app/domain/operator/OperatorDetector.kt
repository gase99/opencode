package com.confighub.app.domain.operator

import android.content.Context
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the operator/network the device is CURRENTLY attached to.
 *
 * * Uses the non-restricted `TelephonyManager.getNetworkOperator()` API — the app does
 *   NOT request READ_PHONE_STATE.
 * * Falls back to a manual profile the user selects in Settings.
 * * NEVER pretends to measure a different operator than the current one.
 */
@Singleton
class OperatorDetector @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Source { AUTO_TELEPHONY, MANUAL, UNKNOWN }

    data class Detection(
        val operatorId: OperatorId,
        val source: Source,
        val networkType: NetworkType,
        val plmn: String?,
    )

    enum class NetworkType { WIFI, MOBILE, UNKNOWN }

    fun detect(manualOperatorId: String?): Detection {
        val networkType = detectNetworkType()
        if (manualOperatorId != null) {
            val manual = runCatching { OperatorId.valueOf(manualOperatorId) }.getOrNull()
                ?: OperatorId.OTHER
            return Detection(manual, Source.MANUAL, networkType, null)
        }
        val plmn = runCatching {
            context.getSystemService(TelephonyManager::class.java)?.networkOperator
        }.getOrNull()
        val operator = OperatorCatalog.fromPlmn(plmn) ?: OperatorId.OTHER
        return Detection(
            operatorId = operator,
            source = if (plmn.isNullOrBlank()) Source.UNKNOWN else Source.AUTO_TELEPHONY,
            networkType = networkType,
            plmn = plmn?.takeIf { it.isNotBlank() },
        )
    }

    private fun detectNetworkType(): NetworkType {
        return runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
                ?: return NetworkType.UNKNOWN
            val active = connectivity.activeNetwork ?: return NetworkType.UNKNOWN
            val capabilities = connectivity.getNetworkCapabilities(active)
                ?: return NetworkType.UNKNOWN
            when {
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                else -> NetworkType.UNKNOWN
            }
        }.getOrDefault(NetworkType.UNKNOWN)
    }
}
