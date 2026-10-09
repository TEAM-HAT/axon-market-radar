package inc.axon.radar

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Fetches the brief, caches it, and redraws every placed widget. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Brief.save(applicationContext, Brief.fetch())
            RadarWidget.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            RadarWidget.updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object Refresh {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Hourly, so the morning's update reaches the home screen soon after the daily sweep publishes it. */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS, 20, TimeUnit.MINUTES).setConstraints(online).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("radar-periodic", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** One fetch now. A tap on "Updated" forces it, replacing anything queued, so it fails fast when offline. */
    fun now(ctx: Context, force: Boolean = false) {
        val b = OneTimeWorkRequestBuilder<RefreshWorker>()
        if (!force) b.setConstraints(online)
        WorkManager.getInstance(ctx).enqueueUniqueWork("radar-now", if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, b.build())
    }
}
