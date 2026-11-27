package com.confighub.app.domain.scoring

import com.confighub.app.core.model.Protocol

/**
 * Pure scoring math — no Android dependencies, fully unit-tested.
 *
 * Score components (0..100 each):
 *  * latency  — TCP connect mean (50 ms → 100, 1000 ms → 0, linear),
 *  * jitter   — variance of consecutive samples (0 ms → 100, 50 ms → 0),
 *  * failure  — success rate over history (no history → neutral 60),
 *  * recency  — hours since last successful test (0h → 100, ≥72h → 0; never → 30),
 *  * protocol — neutral 75 (experimental 60); weight defaults to 0 (user can raise).
 *
 * Weights are normalized, so any non-negative values work.
 */
object ScoreEngine {

    data class Weights(
        val latency: Float = 0.45f,
        val jitter: Float = 0.15f,
        val failure: Float = 0.25f,
        val recency: Float = 0.15f,
        val protocol: Float = 0.0f,
    )

    data class Input(
        val tcpMeanMs: Long?,
        val jitterMs: Long?,
        val lastProbeOk: Boolean?,
        /** 0.0..1.0 over recent history; null when there is no history. */
        val successRate: Float?,
        /** Hours since last successful probe; null = never succeeded. */
        val hoursSinceLastSuccess: Double?,
        val protocol: Protocol,
    )

    data class Component(val name: String, val score: Double, val weight: Double, val contribution: Double)

    data class Result(val total: Int, val components: List<Component>)

    fun score(input: Input, weights: Weights): Result {
        val latency = latencyScore(input.tcpMeanMs)
        val jitter = jitterScore(input.jitterMs)
        val failure = failureScore(input.successRate)
        val recency = recencyScore(input.hoursSinceLastSuccess, input.lastProbeOk)
        val protocol = protocolScore(input.protocol)

        val named = listOf(
            "latency" to (latency to weights.latency.toDouble()),
            "jitter" to (jitter to weights.jitter.toDouble()),
            "failure" to (failure to weights.failure.toDouble()),
            "recency" to (recency to weights.recency.toDouble()),
            "protocol" to (protocol to weights.protocol.toDouble()),
        )
        val weightSum = named.sumOf { it.second.second }
        val total = if (weightSum <= 0.0) 0.0 else named.sumOf { (name, pair) -> pair.first * pair.second } / weightSum
        val components = named.map { (name, pair) ->
            Component(
                name = name,
                score = pair.first,
                weight = pair.second,
                contribution = if (weightSum <= 0.0) 0.0 else pair.first * pair.second / weightSum,
            )
        }
        return Result(total = total.toInt().coerceIn(0, 100), components = components)
    }

    fun latencyScore(tcpMeanMs: Long?): Double = when (tcpMeanMs) {
        null -> 50.0
        else -> (100.0 - (tcpMeanMs - 50.0) * 100.0 / 950.0).coerceIn(0.0, 100.0)
    }

    fun jitterScore(jitterMs: Long?): Double = when (jitterMs) {
        null -> 60.0
        else -> (100.0 - jitterMs * 2.0).coerceIn(0.0, 100.0)
    }

    fun failureScore(successRate: Float?): Double = when (successRate) {
        null -> 60.0
        else -> (successRate.toDouble() * 100.0).coerceIn(0.0, 100.0)
    }

    fun recencyScore(hoursSinceLastSuccess: Double?, lastProbeOk: Boolean?): Double = when {
        hoursSinceLastSuccess == null -> if (lastProbeOk == true) 100.0 else 30.0
        else -> (100.0 - hoursSinceLastSuccess * 100.0 / 72.0).coerceIn(0.0, 100.0)
    }

    fun protocolScore(protocol: Protocol): Double = if (protocol.isExperimental) 60.0 else 75.0
}
