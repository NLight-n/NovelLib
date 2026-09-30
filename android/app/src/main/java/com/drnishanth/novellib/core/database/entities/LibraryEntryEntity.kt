package com.drnishanth.novellib.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "library_entries",
    primaryKeys = ["profile_id", "novel_id"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novel_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["novel_id"])
    ]
)
data class LibraryEntryEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "novel_id")
    val novelId: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,

    @ColumnInfo(name = "download_mode")
    val downloadMode: String = "hybrid", // online, offline, hybrid

    @ColumnInfo(name = "download_limit")
    val downloadLimit: Int = 10,

    @ColumnInfo(name = "auto_download_enabled")
    val autoDownloadEnabled: Boolean = true,

    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long? = null
)
