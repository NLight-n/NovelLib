package com.drnishanth.novellib.scraping.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SourceRegistryIndex(
    val version: Int,
    @SerialName("updated_at")
    val updatedAt: Long,
    val sources: List<RegistrySourceItem>
)

@Serializable
data class RegistrySourceItem(
    val id: String,
    val version: Int,
    val name: String,
    val description: String? = null,
    @SerialName("minimum_engine_version")
    val minimumEngineVersion: Int = 1,
    val url: String? = null,
    @SerialName("relative_path")
    val relativePath: String? = null,
    val checksum: String,
    val signature: String? = null
)

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
