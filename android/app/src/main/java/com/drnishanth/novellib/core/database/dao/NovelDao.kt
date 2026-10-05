package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.NovelTagCrossRef
import com.drnishanth.novellib.core.database.entities.SourceEntity
import com.drnishanth.novellib.core.database.entities.TagEntity
import com.drnishanth.novellib.core.database.models.NovelWithTags
import kotlinx.coroutines.flow.Flow

data class NovelWithEntry(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String?,
    val status: String,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val downloadMode: String,
    val addictionLimit: Int = 0,
    val sessionChaptersRead: Int = 0,
    val lockedUntil: Long = 0L
)

@Dao
interface NovelDao {
    @Query("""
        SELECT n.id, n.title, n.author, n.description, n.cover_url AS coverUrl, 
               n.status, l.added_at AS addedAt, l.last_opened_at AS lastOpenedAt, 
               l.download_mode AS downloadMode,
               l.addiction_limit AS addictionLimit,
               l.session_chapters_read AS sessionChaptersRead,
               l.locked_until AS lockedUntil
        FROM novels n
        INNER JOIN library_entries l ON n.id = l.novel_id
        WHERE l.profile_id = :profileId
        ORDER BY COALESCE(l.last_opened_at, l.added_at) DESC
    """)
    fun getNovelsForProfile(profileId: String): Flow<List<NovelWithEntry>>

    @Query("""
        SELECT n.id, n.title, n.author, n.description, n.cover_url AS coverUrl, 
               n.status, l.added_at AS addedAt, l.last_opened_at AS lastOpenedAt, 
               l.download_mode AS downloadMode,
               l.addiction_limit AS addictionLimit,
               l.session_chapters_read AS sessionChaptersRead,
               l.locked_until AS lockedUntil
        FROM novels n
        INNER JOIN library_entries l ON n.id = l.novel_id
        WHERE l.profile_id = :profileId
          AND n.id NOT IN (
              SELECT r.novel_id 
              FROM novel_tag_cross_ref r
              INNER JOIN tags t ON r.tag_id = t.id
              WHERE t.id IN (:excludedTagIds) OR LOWER(t.name) IN (:excludedTagNamesLower)
          )
        ORDER BY COALESCE(l.last_opened_at, l.added_at) DESC
    """)
    fun getNovelsForProfileExcludingTags(
        profileId: String,
        excludedTagIds: List<String>,
        excludedTagNamesLower: List<String>
    ): Flow<List<NovelWithEntry>>

    @Transaction
    @Query("SELECT * FROM novels WHERE id = :novelId LIMIT 1")
    suspend fun getNovelWithTags(novelId: String): NovelWithTags?

    @Transaction
    @Query("SELECT * FROM novels WHERE id = :novelId LIMIT 1")
    fun getNovelWithTagsFlow(novelId: String): Flow<NovelWithTags?>

    @Transaction
    @Query("SELECT * FROM novels")
    fun getAllNovelsWithTags(): Flow<List<NovelWithTags>>

    @Transaction
    @Query("""
        SELECT * FROM novels
        WHERE id NOT IN (
            SELECT r.novel_id 
            FROM novel_tag_cross_ref r
            INNER JOIN tags t ON r.tag_id = t.id
            WHERE t.id IN (:excludedTagIds) OR LOWER(t.name) IN (:excludedTagNamesLower)
        )
    """)
    fun getNovelsExcludingTags(
        excludedTagIds: List<String>,
        excludedTagNamesLower: List<String>
    ): Flow<List<NovelWithTags>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNovelTagCrossRef(crossRef: NovelTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNovelTagCrossRefs(crossRefs: List<NovelTagCrossRef>)

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN novel_tag_cross_ref r ON t.id = r.tag_id
        WHERE r.novel_id = :novelId
    """)
    suspend fun getTagsForNovel(novelId: String): List<TagEntity>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN novel_tag_cross_ref r ON t.id = r.tag_id
        WHERE r.novel_id = :novelId
    """)
    fun getTagsForNovelFlow(novelId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTags(): List<TagEntity>

    @Query("DELETE FROM novel_tag_cross_ref WHERE novel_id = :novelId")
    suspend fun deleteTagsForNovel(novelId: String)

    @Query("SELECT * FROM novels WHERE id = :novelId LIMIT 1")
    suspend fun getNovelById(novelId: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNovel(novel: NovelEntity)

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: SourceEntity)

    @Update
    suspend fun updateSource(source: SourceEntity)

    @Query("UPDATE sources SET last_checked_at = :lastCheckedAt, last_successful_check_at = :lastSuccessfulCheckAt WHERE id = :sourceId")
    suspend fun updateSourceCheckTime(sourceId: String, lastCheckedAt: Long, lastSuccessfulCheckAt: Long)

    @Query("SELECT * FROM sources WHERE source_url = :sourceUrl LIMIT 1")
    suspend fun getSourceByUrl(sourceUrl: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE novel_id = :novelId")
    suspend fun getSourcesForNovel(novelId: String): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryEntry(entry: LibraryEntryEntity)

    @Query("DELETE FROM library_entries WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun removeNovelFromLibrary(profileId: String, novelId: String)

    @Query("SELECT COUNT(*) FROM library_entries WHERE novel_id = :novelId")
    suspend fun getLibraryEntryCountForNovel(novelId: String): Int

    @Query("DELETE FROM novels WHERE id = :novelId")
    suspend fun deleteNovel(novelId: String)

    @Query("SELECT COUNT(*) FROM library_entries WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun isNovelInLibrary(profileId: String, novelId: String): Int

    @Query("SELECT * FROM library_entries WHERE profile_id = :profileId AND novel_id = :novelId LIMIT 1")
    suspend fun getLibraryEntry(profileId: String, novelId: String): LibraryEntryEntity?

    @Query("""
        UPDATE library_entries 
        SET download_mode = :mode, download_limit = :limit, auto_download_enabled = :autoDownload 
        WHERE profile_id = :profileId AND novel_id = :novelId
    """)
    suspend fun updateDownloadPolicy(
        profileId: String,
        novelId: String,
        mode: String,
        limit: Int,
        autoDownload: Boolean
    )

    @Query("UPDATE library_entries SET last_opened_at = :timestamp WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun updateLastOpened(profileId: String, novelId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM novels")
    suspend fun getAllNovels(): List<NovelEntity>

    @Query("SELECT * FROM library_entries")
    suspend fun getAllLibraryEntries(): List<LibraryEntryEntity>

    @Query("SELECT * FROM library_entries WHERE novel_id = :novelId")
    suspend fun getLibraryEntriesForNovel(novelId: String): List<LibraryEntryEntity>

    @Query("UPDATE library_entries SET notifications_enabled = :enabled WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun updateNotificationPreference(profileId: String, novelId: String, enabled: Boolean)

    @Query("UPDATE library_entries SET addiction_limit = :limit, session_chapters_read = 0, locked_until = 0 WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun updateAddictionLimit(profileId: String, novelId: String, limit: Int)

    @Query("UPDATE library_entries SET session_chapters_read = :chaptersRead, locked_until = :lockedUntil WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun updateAddictionSession(profileId: String, novelId: String, chaptersRead: Int, lockedUntil: Long)

    @Query("UPDATE library_entries SET session_chapters_read = 0, locked_until = 0 WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun resetAddictionSession(profileId: String, novelId: String)
}
