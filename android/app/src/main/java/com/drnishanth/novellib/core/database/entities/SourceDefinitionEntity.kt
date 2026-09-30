package com.drnishanth.novellib.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_definitions")
data class SourceDefinitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "version")
    val version: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "minimum_engine_version")
    val minimumEngineVersion: Int = 1,

    @ColumnInfo(name = "definition_url")
    val definitionUrl: String? = null,

    @ColumnInfo(name = "checksum")
    val checksum: String? = null,

    @ColumnInfo(name = "signature")
    val signature: String? = null,

    @ColumnInfo(name = "json_content")
    val jsonContent: String,

    // Rollback and health tracking fields
    @ColumnInfo(name = "previous_version")
    val previousVersion: Int? = null,

    @ColumnInfo(name = "previous_json_content")
    val previousJsonContent: String? = null,

    @ColumnInfo(name = "previous_checksum")
    val previousChecksum: String? = null,

    @ColumnInfo(name = "consecutive_failures")
    val consecutiveFailures: Int = 0,

    @ColumnInfo(name = "installed_at")
    val installedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
