package com.confighub.app.domain.operator

/**
 * Iranian operator profiles.
 *
 * HONESTY NOTE: the thresholds below are editable *estimates* used for color bands and
 * ranking emphasis — they are NOT measurements taken on those networks. The app can
 * only measure on the network the device is currently attached to; results are always
 * labeled "measured on current network" or "manual profile".
 */
enum class OperatorId(val displayName: String, val plmnCodes: Set<String>) {
    MCI("MCI / Hamrah-e Aval", setOf("43211", "43219")),
    IRANCELL("Irancell", setOf("43235", "43232")),
    RIGHTEL("Rightel", setOf("43220")),
    OTHER("Other / Unknown", emptySet()),
}

data class OperatorThresholds(
    /** Latency below which a result is shown "good" (ms). */
    val goodBelowMs: Int = 150,
    /** Latency below which a result is shown "acceptable" (ms). */
    val acceptableBelowMs: Int = 350,
)

data class OperatorProfile(
    val id: OperatorId,
    val thresholds: OperatorThresholds,
    /** Optional per-operator weight overrides (null → global settings weights). */
    val weightsOverride: com.confighub.app.domain.scoring.ScoreEngine.Weights? = null,
)

object OperatorCatalog {

    /** Defaults are deliberately modest and user-editable; see class KDoc. */
    val defaultProfiles: Map<OperatorId, OperatorProfile> = mapOf(
        OperatorId.MCI to OperatorProfile(
            OperatorId.MCI,
            OperatorThresholds(goodBelowMs = 120, acceptableBelowMs = 300),
        ),
        OperatorId.IRANCELL to OperatorProfile(
            OperatorId.IRANCELL,
            OperatorThresholds(goodBelowMs = 150, acceptableBelowMs = 350),
        ),
        OperatorId.RIGHTEL to OperatorProfile(
            OperatorId.RIGHTEL,
            OperatorThresholds(goodBelowMs = 170, acceptableBelowMs = 400),
        ),
        OperatorId.OTHER to OperatorProfile(
            OperatorId.OTHER,
            OperatorThresholds(goodBelowMs = 150, acceptableBelowMs = 350),
        ),
    )

    fun profile(id: OperatorId): OperatorProfile = defaultProfiles.getValue(id)

    /** Maps a PLMN string (MCC+MNC, e.g. "43211") to an operator id, or null. */
    fun fromPlmn(plmn: String?): OperatorId? {
        val normalized = plmn?.trim() ?: return null
        if (normalized.isBlank()) return null
        return OperatorId.entries.firstOrNull { operator ->
            operator.plmnCodes.any { code ->
                normalized.startsWith(code) || normalized.startsWith(code.take(5))
            }
        }
    }
}
