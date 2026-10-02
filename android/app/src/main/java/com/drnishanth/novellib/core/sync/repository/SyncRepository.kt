package com.drnishanth.novellib.core.sync.repository

import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.dao.NovelDao
import com.drnishanth.novellib.core.database.dao.ReaderPreferencesDao
import com.drnishanth.novellib.core.database.dao.ReadingProgressDao
import com.drnishanth.novellib.core.database.dao.SyncDeviceDao
import com.drnishanth.novellib.core.database.dao.UserProfileDao
import com.drnishanth.novellib.core.database.entities.SyncDeviceEntity
import com.drnishanth.novellib.core.sync.client.LocalSyncClient
import com.drnishanth.novellib.core.sync.discovery.NsdDiscoveryManager
import com.drnishanth.novellib.core.sync.engine.ConflictResolutionEngine
import com.drnishanth.novellib.core.sync.models.DiscoveredDevice
import com.drnishanth.novellib.core.sync.models.PairingConfirm
import com.drnishanth.novellib.core.sync.models.PairingRequest
import com.drnishanth.novellib.core.sync.models.PairingResponse
import com.drnishanth.novellib.core.sync.models.SyncChapterItem
import com.drnishanth.novellib.core.sync.models.SyncLibraryEntryItem
import com.drnishanth.novellib.core.sync.models.SyncNovelItem
import com.drnishanth.novellib.core.sync.models.SyncPayload
import com.drnishanth.novellib.core.sync.models.SyncPreferencesItem
import com.drnishanth.novellib.core.sync.models.SyncProfileData
import com.drnishanth.novellib.core.sync.models.SyncReadingProgressItem
import com.drnishanth.novellib.core.sync.models.SyncResult
import com.drnishanth.novellib.core.sync.models.SyncSourceItem
import com.drnishanth.novellib.core.sync.security.DeviceIdentityManager
import com.drnishanth.novellib.core.sync.server.IncomingPairingEvent
import com.drnishanth.novellib.core.sync.server.LocalSyncServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

class SyncRepository(
    val deviceIdentityManager: DeviceIdentityManager,
    private val syncDeviceDao: SyncDeviceDao,
    private val userProfileDao: UserProfileDao,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readerPreferencesDao: ReaderPreferencesDao,
    val conflictResolutionEngine: ConflictResolutionEngine,
    val localSyncClient: LocalSyncClient,
    val localSyncServer: LocalSyncServer,
    val nsdDiscoveryManager: NsdDiscoveryManager
) {
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = nsdDiscoveryManager.discoveredDevices
    val trustedDevices: Flow<List<SyncDeviceEntity>> = syncDeviceDao.getTrustedDevices()
    val incomingPairingEvents: SharedFlow<IncomingPairingEvent> = localSyncServer.incomingPairingEvents

    fun startSyncMode() {
        val port = localSyncServer.start()
        nsdDiscoveryManager.startAdvertising(port)
        nsdDiscoveryManager.startDiscovery()
    }

    fun stopSyncMode() {
        nsdDiscoveryManager.stopDiscovery()
        nsdDiscoveryManager.stopAdvertising()
        localSyncServer.stop()
    }

    suspend fun initiatePairing(device: DiscoveredDevice): Result<PairingResponse> = withContext(Dispatchers.IO) {
        val nonce = UUID.randomUUID().toString()
        val request = PairingRequest(
            senderDeviceId = deviceIdentityManager.deviceId,
            senderDeviceName = deviceIdentityManager.deviceName,
            senderDeviceType = deviceIdentityManager.deviceType,
            senderPublicKey = deviceIdentityManager.publicKeyBase64,
            nonce = nonce
        )
        localSyncClient.initiatePairing(device.hostAddress, device.port, request)
    }

    suspend fun acceptPairing(
        peerDeviceId: String,
        peerDeviceName: String,
        peerDeviceType: String,
        peerPublicKey: String
    ) = withContext(Dispatchers.IO) {
        syncDeviceDao.insertDevice(
            SyncDeviceEntity(
                id = UUID.randomUUID().toString(),
                deviceId = peerDeviceId,
                deviceName = peerDeviceName,
                deviceType = peerDeviceType,
                publicKey = peerPublicKey,
                appVersion = "0.1",
                trusted = true,
                lastSeenAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun confirmPairing(device: DiscoveredDevice, peerPublicKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val result = localSyncClient.confirmPairing(
            device.hostAddress,
            device.port,
            PairingConfirm(senderDeviceId = deviceIdentityManager.deviceId, confirmed = true)
        )
        if (result.isSuccess && result.getOrDefault(false)) {
            syncDeviceDao.insertDevice(
                SyncDeviceEntity(
                    id = UUID.randomUUID().toString(),
                    deviceId = device.deviceId,
                    deviceName = device.deviceName,
                    deviceType = device.deviceType,
                    publicKey = peerPublicKey.ifBlank { device.publicKeyBase64 },
                    appVersion = "0.1",
                    trusted = true,
                    lastSeenAt = System.currentTimeMillis()
                )
            )
        }
        result
    }

    suspend fun forgetDevice(deviceId: String) = withContext(Dispatchers.IO) {
        syncDeviceDao.deleteDevice(deviceId)
    }

    suspend fun executeSync(
        host: String,
        port: Int,
        profileId: String
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val payload = buildLocalPayload(profileId)
                ?: return@withContext Result.failure(IllegalStateException("No active profile data to sync"))

            // Send local payload to peer and retrieve remote payload
            val remotePayloadResult = localSyncClient.exchangeSync(host, port, payload)
            if (remotePayloadResult.isFailure) {
                return@withContext Result.failure(remotePayloadResult.exceptionOrNull()!!)
            }

            val remotePayload = remotePayloadResult.getOrNull()
            if (remotePayload != null) {
                val mergeResult = conflictResolutionEngine.mergePayload(remotePayload, destinationProfileId = profileId)
                Result.success(mergeResult)
            } else {
                Result.success(
                    SyncResult(
                        success = true,
                        novelsMerged = payload.novels.size,
                        message = "Local library metadata uploaded to peer device successfully"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun buildLocalPayload(profileId: String): SyncPayload? = withContext(Dispatchers.IO) {
        val profile = userProfileDao.getProfileById(profileId) ?: return@withContext null
        val prefs = readerPreferencesDao.getPreferencesDirect(profileId)

        val syncPrefs = prefs?.let {
            SyncPreferencesItem(
                theme = it.theme,
                fontFamily = it.fontFamily,
                fontSize = it.fontSize,
                lineHeight = it.lineHeight,
                contentWidth = it.contentWidth,
                pageNavigationMode = it.pageNavigationMode
            )
        }

        val profileData = SyncProfileData(
            username = profile.username,
            displayName = profile.displayName,
            avatar = profile.avatar,
            isPasswordProtected = profile.passwordEnabled,
            preferences = syncPrefs,
            blockedTags = profile.blockedTags
        )

        val libraryNovels = novelDao.getNovelsForProfile(profileId).firstOrNull() ?: emptyList()
        val syncNovels = mutableListOf<SyncNovelItem>()

        for (item in libraryNovels) {
            val novel = novelDao.getNovelById(item.id) ?: continue
            val sources = novelDao.getSourcesForNovel(novel.id).map {
                SyncSourceItem(
                    id = it.id,
                    sourceDefinitionId = it.sourceDefinitionId,
                    sourceUrl = it.sourceUrl,
                    lastCheckedAt = it.lastCheckedAt,
                    lastSuccessfulCheckAt = it.lastSuccessfulCheckAt
                )
            }

            val chapters = chapterDao.getChaptersListForNovel(novel.id).map {
                SyncChapterItem(
                    id = it.id,
                    chapterNumber = it.chapterNumber,
                    title = it.title,
                    sourceUrl = it.sourceUrl,
                    publishedAt = it.publishedAt,
                    contentHash = it.contentHash,
                    downloadState = it.downloadState
                )
            }

            val entry = novelDao.getLibraryEntry(profileId, novel.id)?.let {
                SyncLibraryEntryItem(
                    addedAt = it.addedAt,
                    downloadMode = it.downloadMode,
                    downloadLimit = it.downloadLimit,
                    autoDownloadEnabled = it.autoDownloadEnabled,
                    notificationsEnabled = it.notificationsEnabled,
                    lastOpenedAt = it.lastOpenedAt
                )
            }

            val progress = readingProgressDao.getProgressDirect(profileId, novel.id)?.let {
                val chapterNum = it.chapterId.let { cid -> chapterDao.getChapterById(cid)?.chapterNumber } ?: 0
                SyncReadingProgressItem(
                    chapterId = it.chapterId,
                    chapterNumber = chapterNum,
                    position = it.position,
                    progressPercent = it.progressPercent,
                    updatedAt = it.updatedAt
                )
            }

            val tags = novelDao.getTagsForNovel(novel.id).map {
                com.drnishanth.novellib.core.sync.models.SyncTagItem(
                    id = it.id,
                    name = it.name,
                    isWarning = it.isWarning
                )
            }

            syncNovels.add(
                SyncNovelItem(
                    novelId = novel.id,
                    title = novel.title,
                    author = novel.author,
                    description = novel.description,
                    coverUrl = novel.coverUrl,
                    status = novel.status,
                    sources = sources,
                    chapters = chapters,
                    libraryEntry = entry,
                    readingProgress = progress,
                    tags = tags
                )
            )
        }

        SyncPayload(
            senderDeviceId = deviceIdentityManager.deviceId,
            senderDeviceName = deviceIdentityManager.deviceName,
            profile = profileData,
            novels = syncNovels
        )
    }
}
