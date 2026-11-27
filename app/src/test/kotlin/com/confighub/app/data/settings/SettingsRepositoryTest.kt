package com.confighub.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    @Test
    fun `defaults, setters and manual operator round-trip`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)

        val defaults = repo.settingsOnce()
        assertThat(defaults.allowExperimentalProtocols).isFalse()
        assertThat(defaults.allowInsecureHttpSources).isFalse()
        assertThat(defaults.refreshIntervalHours).isEqualTo(6)
        assertThat(defaults.maxConfigsPerSource).isEqualTo(500)
        assertThat(defaults.manualOperatorId).isNull()

        repo.setAllowExperimentalProtocols(true)
        repo.setRefreshIntervalHours(24)
        repo.setManualOperatorId("MCI")
        repo.setWeights(0.5f, 0.1f, 0.3f, 0.1f)
        repo.setProtocolWeight(0.2f)

        val updated = repo.settingsOnce()
        assertThat(updated.allowExperimentalProtocols).isTrue()
        assertThat(updated.refreshIntervalHours).isEqualTo(24)
        assertThat(updated.manualOperatorId).isEqualTo("MCI")
        assertThat(updated.latencyWeight).isEqualTo(0.5f)
        assertThat(updated.protocolWeight).isEqualTo(0.2f)

        repo.setManualOperatorId(null)
        assertThat(repo.settingsOnce().manualOperatorId).isNull()
    }
}
