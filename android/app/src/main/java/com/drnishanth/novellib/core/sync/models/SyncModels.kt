package com.drnishanth.novellib.core.sync.models

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String, // phone, tablet, eink
    val hostAddress: String,
    val port: Int,
    val publicKeyBase64: String,
    val isTrusted: Boolean = false
)

@Serializable
data class PairingRequest(
    val senderDeviceId: String,
    val senderDeviceName: String,
    val senderDeviceType: String,
    val senderPublicKey: String,
    val nonce: String
)

@Serializable
data class PairingResponse(
    val accepted: Boolean,
    val receiverDeviceId: String,
    val receiverDeviceName: String,
    val receiverDeviceType: String,
    val receiverPublicKey: String,
    val nonce: String,
    val sasCode: String,
    val errorMessage: String? = null
)

@Serializable
data class PairingConfirm(
    val senderDeviceId: String,
    val confirmed: Boolean
)

@Serializable
data class SyncNovelItem(
    val novelId: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String? = null,
    val status: String = "ongoing",
    val sources: List<SyncSourceItem> = emptyList(),
    val chapters: List<SyncChapterItem> = emptyList(),
    val libraryEntry: SyncLibraryEntryItem? = null,
    val readingProgress: SyncReadingProgressItem? = null
)

@Serializable
data class SyncSourceItem(
    val id: String,
    val sourceDefinitionId: String,
    val sourceUrl: String,
    val lastCheckedAt: Long? = null,
    val lastSuccessfulCheckAt: Long? = null
)

@Serializable
data class SyncChapterItem(
    val id: String,
    val chapterNumber: Int,
    val title: String,
    val sourceUrl: String,
    val publishedAt: Long? = null,
    val contentHash: String? = null,
    val downloadState: String = "not_downloaded"
)

@Serializable
data class SyncLibraryEntryItem(
    val addedAt: Long,
    val downloadMode: String = "hybrid",
    val downloadLimit: Int = 10,
    val autoDownloadEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val lastOpenedAt: Long? = null
)

@Serializable
data class SyncReadingProgressItem(
    val chapterId: String?,
    val chapterNumber: Int = 0,
    val position: Int = 0,
    val progressPercent: Float = 0f,
    val updatedAt: Long = 0L
)

@Serializable
data class SyncPreferencesItem(
    val theme: String = "light",
    val fontFamily: String = "serif",
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val contentWidth: Float = 1.0f,
    val pageNavigationMode: String = "scroll"
)

@Serializable
data class SyncProfileData(
    val username: String,
    val displayName: String,
    val avatar: String? = null,
    val isPasswordProtected: Boolean = false,
    val preferences: SyncPreferencesItem? = null
)

@Serializable
data class SyncPayload(
    val protocolVersion: Int = 1,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val profile: SyncProfileData,
    val novels: List<SyncNovelItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncResult(
    val success: Boolean,
    val novelsMerged: Int = 0,
    val chaptersMerged: Int = 0,
    val progressUpdated: Boolean = false,
    val profileImported: Boolean = false,
    val message: String = ""
)
