package com.confighub.app.domain.scoring

import com.confighub.app.core.model.Protocol
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreEngineTest {

    @Test
    fun `latency score maps 50ms to 100 and 1000ms to 0`() {
        assertThat(ScoreEngine.latencyScore(50L)).isWithin(0.01).of(100.0)
        assertThat(ScoreEngine.latencyScore(1000L)).isWithin(0.01).of(0.0)
        assertThat(ScoreEngine.latencyScore(525L)).isWithin(0.5).of(50.0)
        assertThat(ScoreEngine.latencyScore(10L)).isWithin(0.01).of(100.0)
        assertThat(ScoreEngine.latencyScore(5000L)).isWithin(0.01).of(0.0)
        assertThat(ScoreEngine.latencyScore(null)).isWithin(0.01).of(50.0)
    }

    @Test
    fun `jitter score maps 0ms to 100 and 50ms to 0`() {
        assertThat(ScoreEngine.jitterScore(0L)).isWithin(0.01).of(100.0)
        assertThat(ScoreEngine.jitterScore(25L)).isWithin(0.01).of(50.0)
        assertThat(ScoreEngine.jitterScore(50L)).isWithin(0.01).of(0.0)
        assertThat(ScoreEngine.jitterScore(null)).isWithin(0.01).of(60.0)
    }

    @Test
    fun `failure score uses success rate with neutral fallback`() {
        assertThat(ScoreEngine.failureScore(1.0f)).isWithin(0.01).of(100.0)
        assertThat(ScoreEngine.failureScore(0.0f)).isWithin(0.01).of(0.0)
        assertThat(ScoreEngine.failureScore(null)).isWithin(0.01).of(60.0)
    }

    @Test
    fun `recency score decays over 72h`() {
        assertThat(ScoreEngine.recencyScore(0.0, true)).isWithin(0.01).of(100.0)
        assertThat(ScoreEngine.recencyScore(36.0, false)).isWithin(0.5).of(50.0)
        assertThat(ScoreEngine.recencyScore(72.0, false)).isWithin(0.01).of(0.0)
        assertThat(ScoreEngine.recencyScore(500.0, false)).isWithin(0.01).of(0.0)
        assertThat(ScoreEngine.recencyScore(null, false)).isWithin(0.01).of(30.0)
    }

    @Test
    fun `weights are normalized`() {
        val result = ScoreEngine.score(
            input = ScoreEngine.Input(
                // 50 ms latency + 0 ms jitter + perfect history → all used components = 100
                tcpMeanMs = 50L,
                jitterMs = 0L,
                lastProbeOk = true,
                successRate = 1.0f,
                hoursSinceLastSuccess = 0.0,
                protocol = Protocol.VLESS,
            ),
            weights = ScoreEngine.Weights(latency = 2f, jitter = 1f, failure = 1f, recency = 0f, protocol = 0f),
        )
        assertThat(result.total).isEqualTo(100)
        val weightSum = result.components.sumOf { it.weight }
        assertThat(weightSum).isWithin(0.001).of(4.0)
    }

    @Test
    fun `terrible latency drags score down`() {
        val result = ScoreEngine.score(
            input = ScoreEngine.Input(
                tcpMeanMs = 1000L,
                jitterMs = 40L,
                lastProbeOk = false,
                successRate = 0.0f,
                hoursSinceLastSuccess = 100.0,
                protocol = Protocol.VMESS,
            ),
            weights = ScoreEngine.Weights(),
        )
        assertThat(result.total).isLessThan(30)
    }

    @Test
    fun `experimental protocol scores lower on protocol component`() {
        assertThat(ScoreEngine.protocolScore(Protocol.HYSTERIA2)).isLessThan(ScoreEngine.protocolScore(Protocol.VLESS))
    }
}
