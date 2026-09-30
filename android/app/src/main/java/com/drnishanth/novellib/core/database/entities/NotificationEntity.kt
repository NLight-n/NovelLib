package com.drnishanth.novellib.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profile_id"])]
)
data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "novel_id")
    val novelId: String? = null,

    @ColumnInfo(name = "chapter_id")
    val chapterId: String? = null,

    @ColumnInfo(name = "type")
    val type: String, // new_chapter, download_completed, download_failed, source_update_failed, sync_completed, sync_failed

    @ColumnInfo(name = "title")
    val title: String = "NovelLib",

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "read")
    val read: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
