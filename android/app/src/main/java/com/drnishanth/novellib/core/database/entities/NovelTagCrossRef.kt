package com.drnishanth.novellib.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "novel_tag_cross_ref",
    primaryKeys = ["novel_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novel_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["novel_id"]),
        Index(value = ["tag_id"])
    ]
)
data class NovelTagCrossRef(
    @ColumnInfo(name = "novel_id")
    val novelId: String,

    @ColumnInfo(name = "tag_id")
    val tagId: String
)
