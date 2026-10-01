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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

    @Query("UPDATE chapters SET retention_policy = :policy WHERE id = :chapterId")
    suspend fun updateRetentionPolicy(chapterId: String, policy: String)

    @Query("SELECT * FROM chapters WHERE download_state = 'queued' ORDER BY discovered_at ASC")
    suspend fun getQueuedChapters(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE retention_policy = 'cache' AND download_state = 'available' ORDER BY downloaded_at ASC")
    suspend fun getCachedChaptersSortedByDownloadedAt(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE novel_id = :novelId AND chapter_number > :chapterNumber ORDER BY chapter_number ASC LIMIT :limit")
    suspend fun getChaptersAfterNumber(novelId: String, chapterNumber: Int, limit: Int): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE novel_id = :novelId ORDER BY chapter_number DESC LIMIT :limit")
    suspend fun getLatestChapters(novelId: String, limit: Int): List<ChapterEntity>

    @Query("SELECT COUNT(*) FROM chapters WHERE novel_id = :novelId")
    suspend fun getChapterCountForNovel(novelId: String): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE novel_id = :novelId AND download_state = 'available'")
    suspend fun getDownloadedChapterCount(novelId: String): Int
}
