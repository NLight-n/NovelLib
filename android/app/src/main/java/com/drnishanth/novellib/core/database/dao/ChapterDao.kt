package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novel_id = :novelId ORDER BY chapter_number ASC")
    fun getChaptersForNovel(novelId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novel_id = :novelId ORDER BY chapter_number ASC")
    suspend fun getChaptersListForNovel(novelId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    suspend fun getChapterById(chapterId: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE source_url = :sourceUrl LIMIT 1")
    suspend fun getChapterByUrl(sourceUrl: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("""
        UPDATE chapters 
        SET download_state = :state, file_path = :filePath, content_hash = :contentHash, downloaded_at = :downloadedAt, updated_at = :updatedAt
        WHERE id = :chapterId
    """)
    suspend fun updateDownloadState(
        chapterId: String,
        state: String,
        filePath: String?,
        contentHash: String?,
        downloadedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT COUNT(*) FROM chapters WHERE novel_id = :novelId")
    suspend fun getChapterCountForNovel(novelId: String): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE novel_id = :novelId AND download_state = 'available'")
    suspend fun getDownloadedChapterCount(novelId: String): Int
}
