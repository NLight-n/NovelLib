package com.drnishanth.novellib

import android.app.Application
import com.drnishanth.novellib.core.database.NovelDatabase
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import com.drnishanth.novellib.downloads.DownloadManager
import com.drnishanth.novellib.downloads.RetentionPolicyEngine
import com.drnishanth.novellib.downloads.StoragePolicyManager
import com.drnishanth.novellib.scraping.engine.RollbackManager
import com.drnishanth.novellib.scraping.registry.SourceDefinitionRepositoryClient

class NovelLibApplication : Application() {

    val database: NovelDatabase by lazy {
        NovelDatabase.getInstance(this)
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(
            userProfileDao = database.userProfileDao(),
            readerPreferencesDao = database.readerPreferencesDao()
        )
    }

    val rollbackManager: RollbackManager by lazy {
        RollbackManager(sourceDefinitionDao = database.sourceDefinitionDao())
    }

    val sourceDefinitionRepositoryClient: SourceDefinitionRepositoryClient by lazy {
        SourceDefinitionRepositoryClient(sourceDefinitionDao = database.sourceDefinitionDao())
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            context = this,
            chapterDao = database.chapterDao(),
            sourceDefinitionDao = database.sourceDefinitionDao(),
            rollbackManager = rollbackManager
        )
    }

    val storagePolicyManager: StoragePolicyManager by lazy {
        StoragePolicyManager(chapterDao = database.chapterDao())
    }

    val retentionPolicyEngine: RetentionPolicyEngine by lazy {
        RetentionPolicyEngine(chapterDao = database.chapterDao())
    }

    val novelRepository: NovelRepository by lazy {
        NovelRepository(
            context = this,
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readerPreferencesDao = database.readerPreferencesDao(),
            sourceDefinitionDao = database.sourceDefinitionDao(),
            notificationDao = database.notificationDao(),
            rollbackManager = rollbackManager,
            downloadManager = downloadManager,
            storagePolicyManager = storagePolicyManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Android notification channels
        com.drnishanth.novellib.core.notifications.NovelNotificationManager.createNotificationChannels(this)

        // Schedule periodic background WorkManager tasks
        com.drnishanth.novellib.background.WorkScheduler.schedulePeriodicWork(this)
    }

    companion object {
        lateinit var instance: NovelLibApplication
            private set
    }
}
