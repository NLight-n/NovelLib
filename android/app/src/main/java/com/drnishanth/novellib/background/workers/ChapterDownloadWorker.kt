package com.drnishanth.novellib.background.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.notifications.NovelNotificationManager

class ChapterDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NovelLibApplication ?: return Result.failure()
        return try {
            val queued = app.database.chapterDao().getQueuedChapters()
            if (queued.isEmpty()) {
                return Result.success()
            }

            val result = app.downloadManager.processQueuedDownloads()
            if (result.successCount > 0) {
                NovelNotificationManager.showDownloadCompletedNotification(
                    context = applicationContext,
                    completedCount = result.successCount,
                    novelTitle = null
                )
            }
            if (result.failureCount > 0) {
                NovelNotificationManager.showDownloadFailedNotification(
                    context = applicationContext,
                    failedCount = result.failureCount,
                    novelTitle = null
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
