package com.drnishanth.novellib.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "read_chapters",
    primaryKeys = ["profile_id", "chapter_id"],
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
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profile_id", "novel_id"]),
        Index(value = ["novel_id"]),
        Index(value = ["chapter_id"])
    ]
)
data class ReadChapterEntity(
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "novel_id")
    val novelId: String,

    @ColumnInfo(name = "chapter_id")
    val chapterId: String,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = true,

    @ColumnInfo(name = "read_at")
    val readAt: Long = System.currentTimeMillis()
)
