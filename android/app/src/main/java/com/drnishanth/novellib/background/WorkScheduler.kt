package com.drnishanth.novellib.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.drnishanth.novellib.background.workers.CacheCleanupWorker
import com.drnishanth.novellib.background.workers.ChapterDownloadWorker
import com.drnishanth.novellib.background.workers.NovelUpdateWorker
import com.drnishanth.novellib.background.workers.SourceDefinitionUpdateWorker
import java.util.concurrent.TimeUnit

object WorkScheduler {

    const val TAG_NOVEL_UPDATES = "work_novel_updates"
    const val TAG_CACHE_CLEANUP = "work_cache_cleanup"
    const val TAG_SOURCE_UPDATES = "work_source_updates"
    const val TAG_DOWNLOAD_QUEUE = "work_download_queue"

    /**
     * Configures periodic background workers for novel updates, cache pruning, and source registry checks.
     * Preserves battery life by requiring unmetered or connected network and battery not low.
     */
    fun schedulePeriodicWork(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // 1. Novel updates: every 6 hours, connected to internet, battery not low
        val updateConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val novelUpdateWorkRequest = PeriodicWorkRequestBuilder<NovelUpdateWorker>(
            6, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(updateConstraints)
            .addTag(TAG_NOVEL_UPDATES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TAG_NOVEL_UPDATES,
            ExistingPeriodicWorkPolicy.KEEP,
            novelUpdateWorkRequest
        )

        // 2. Cache retention cleanup: once daily, device battery not low
        val cleanupConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val cacheCleanupWorkRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(cleanupConstraints)
            .addTag(TAG_CACHE_CLEANUP)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TAG_CACHE_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            cacheCleanupWorkRequest
        )

        // 3. Source definition updates: once daily, connected to internet
        val sourceConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val sourceUpdateWorkRequest = PeriodicWorkRequestBuilder<SourceDefinitionUpdateWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(sourceConstraints)
            .addTag(TAG_SOURCE_UPDATES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TAG_SOURCE_UPDATES,
            ExistingPeriodicWorkPolicy.KEEP,
            sourceUpdateWorkRequest
        )
    }

    /**
     * Immediately triggers background download processing for queued chapters.
     */
    fun enqueueImmediateDownload(context: Context, wifiOnly: Boolean = false) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setConstraints(constraints)
            .addTag(TAG_DOWNLOAD_QUEUE)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            TAG_DOWNLOAD_QUEUE,
            ExistingWorkPolicy.REPLACE,
            downloadRequest
        )
    }

    /**
     * Manually triggers an immediate novel update check.
     */
    fun triggerImmediateNovelUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = OneTimeWorkRequestBuilder<NovelUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(updateRequest)
    }
}
