package com.confighub.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.confighub.app.data.repo.ConfigRepository
import com.confighub.app.data.repo.HealthRepository
import com.confighub.app.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

/**
 * Periodic background health checks for the oldest-tested configs.
 *
 * Politeness rules: capped batch size, small delay between probes, network constraint,
 * per-probe timeouts (see HealthChecker). INVARIANT: never starts a VPN session.
 */
@HiltWorker
class HealthCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    private val configRepository: ConfigRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settingsOnce()
        val due = configRepository.dueForTest(limit = BATCH_LIMIT)
        for (entity in due) {
            runCatching { healthRepository.testNow(entity.hash) }
            delay(BETWEEN_PROBES_MS)
        }
        runCatching { healthRepository.prune(settings.keepHistoryDays) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "confighub_health_check"
        const val BATCH_LIMIT = 100
        const val BETWEEN_PROBES_MS = 300L
    }
}
