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

    val deviceIdentityManager: com.drnishanth.novellib.core.sync.security.DeviceIdentityManager by lazy {
        com.drnishanth.novellib.core.sync.security.DeviceIdentityManager(this)
    }

    val syncRepository: com.drnishanth.novellib.core.sync.repository.SyncRepository by lazy {
        val conflictEngine = com.drnishanth.novellib.core.sync.engine.ConflictResolutionEngine(
            userProfileDao = database.userProfileDao(),
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readerPreferencesDao = database.readerPreferencesDao()
        )
        val syncClient = com.drnishanth.novellib.core.sync.client.LocalSyncClient()
        val syncServer = com.drnishanth.novellib.core.sync.server.LocalSyncServer(
            context = this,
            deviceIdentityManager = deviceIdentityManager,
            syncDeviceDao = database.syncDeviceDao(),
            conflictResolutionEngine = conflictEngine,
            localPayloadProvider = {
                val activeProfileId = profileRepository.activeProfile.value?.id
                if (activeProfileId != null) {
                    syncRepository.buildLocalPayload(activeProfileId)
                } else null
            }
        )
        val nsdManager = com.drnishanth.novellib.core.sync.discovery.NsdDiscoveryManager(this, deviceIdentityManager)
        com.drnishanth.novellib.core.sync.repository.SyncRepository(
            deviceIdentityManager = deviceIdentityManager,
            syncDeviceDao = database.syncDeviceDao(),
            userProfileDao = database.userProfileDao(),
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readerPreferencesDao = database.readerPreferencesDao(),
            conflictResolutionEngine = conflictEngine,
            localSyncClient = syncClient,
            localSyncServer = syncServer,
            nsdDiscoveryManager = nsdManager
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
