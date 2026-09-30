package com.drnishanth.novellib.background.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drnishanth.novellib.NovelLibApplication

class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NovelLibApplication ?: return Result.failure()
        return try {
            app.retentionPolicyEngine.cleanCache()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
