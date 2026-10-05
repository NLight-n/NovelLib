package com.drnishanth.novellib.scraping.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SourceRegistryIndex(
    val version: Int = 1,
    @SerialName("registry_version")
    val registryVersion: Int = 1,
    @SerialName("updated_at")
    val updatedAt: Long = 0L,
    @SerialName("last_updated")
    val lastUpdated: String? = null,
    val sources: List<RegistrySourceItem> = emptyList()
) {
    val effectiveVersion: Int
        get() = maxOf(version, registryVersion)
}

@Serializable
data class RegistrySourceItem(
    val id: String,
    val version: Int = 1,
    val name: String,
    val description: String? = null,
    @SerialName("minimum_engine_version")
    val minimumEngineVersion: Int = 1,
    @SerialName("min_engine_version")
    val minEngineVersion: Int = 1,
    val url: String? = null,
    @SerialName("relative_path")
    val relativePath: String? = null,
    val checksum: String = "",
    val sha256: String = "",
    val signature: String? = null
) {
    val effectiveChecksum: String
        get() = checksum.ifBlank { sha256 }

    val effectiveMinEngineVersion: Int
        get() = maxOf(minimumEngineVersion, minEngineVersion)
}

data class SourceUpdateStatus(
    val sourceId: String,
    val name: String,
    val currentVersion: Int,
    val latestVersion: Int,
    val hasUpdate: Boolean,
    val enabled: Boolean,
    val consecutiveFailures: Int = 0,
    val canRollback: Boolean = false
)
