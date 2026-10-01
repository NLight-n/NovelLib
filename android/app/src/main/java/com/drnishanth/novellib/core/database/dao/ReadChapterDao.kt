package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drnishanth.novellib.core.database.entities.ReadChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadChapterDao {
    @Query("SELECT chapter_id FROM read_chapters WHERE profile_id = :profileId AND novel_id = :novelId AND is_read = 1")
    fun getReadChapterIds(profileId: String, novelId: String): Flow<List<String>>

    @Query("SELECT chapter_id FROM read_chapters WHERE profile_id = :profileId AND novel_id = :novelId AND is_read = 1")
    suspend fun getReadChapterIdsDirect(profileId: String, novelId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChapterRead(readChapter: ReadChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChaptersRead(readChapters: List<ReadChapterEntity>)

    @Query("DELETE FROM read_chapters WHERE profile_id = :profileId AND chapter_id = :chapterId")
    suspend fun markChapterUnread(profileId: String, chapterId: String)

    @Query("DELETE FROM read_chapters WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun clearNovelReadStatus(profileId: String, novelId: String)
}
