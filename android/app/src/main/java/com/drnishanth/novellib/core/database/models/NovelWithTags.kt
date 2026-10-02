package com.drnishanth.novellib.core.database.models

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.NovelTagCrossRef
import com.drnishanth.novellib.core.database.entities.TagEntity

data class NovelWithTags(
    @Embedded
    val novel: NovelEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NovelTagCrossRef::class,
            parentColumn = "novel_id",
            entityColumn = "tag_id"
        )
    )
    val tags: List<TagEntity> = emptyList()
)
