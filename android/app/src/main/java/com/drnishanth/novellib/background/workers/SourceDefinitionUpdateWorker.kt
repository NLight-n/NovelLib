package com.drnishanth.novellib.background.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.notifications.NovelNotificationManager

class SourceDefinitionUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NovelLibApplication ?: return Result.failure()
        val client = app.sourceDefinitionRepositoryClient
        return try {
            val registryResult = client.fetchRegistryIndex()
            if (registryResult.isFailure) {
                return Result.retry()
            }
            val registry = registryResult.getOrNull()!!
            val installedMap = app.database.sourceDefinitionDao().getAllDefinitionsDirect().associateBy { it.id }

            var updatedCount = 0
            for (sourceItem in registry.sources) {
                val installed = installedMap[sourceItem.id]
                val currentVersion = installed?.version ?: 0
                if (sourceItem.version > currentVersion) {
                    val updateResult = client.installOrUpdate(sourceItem)
                    if (updateResult.isSuccess) {
                        updatedCount++
                    }
                }
            }

            if (updatedCount > 0) {
                NovelNotificationManager.showSourceUpdateNotification(
                    context = applicationContext,
                    updatedCount = updatedCount
                )
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
