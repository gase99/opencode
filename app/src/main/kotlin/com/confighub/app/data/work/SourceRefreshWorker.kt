package com.confighub.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
import com.confighub.app.data.repo.SourceRepository
import com.confighub.app.data.repo.UrlPolicy
import com.confighub.app.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh of enabled sources.
 *
 * INVARIANT: this worker only refreshes/analyzes data. It NEVER starts a VPN session —
 * VPN sessions are exclusively user-initiated.
 */
@HiltWorker
class SourceRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sourceRepository: SourceRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settingsOnce()
        runCatching {
            sourceRepository.refreshAll(
                settings = settings,
                urlPolicy = UrlPolicy.fromSettings(settings.allowInsecureHttpSources),
                force = false,
            )
        }
        // Graceful: network hiccups are handled by per-source status; periodic work continues.
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "confighub_source_refresh"
    }
}

/** Schedules/updates periodic background work from settings. */
@javax.inject.Singleton
class WorkScheduler @javax.inject.Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) {

    fun schedulePeriodicRefresh(intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<SourceRefreshWorker>(
            intervalHours.coerceAtLeast(1).toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SourceRefreshWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun schedulePeriodicHealthCheck(intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<HealthCheckWorker>(
            intervalHours.coerceAtLeast(1).toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HealthCheckWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
