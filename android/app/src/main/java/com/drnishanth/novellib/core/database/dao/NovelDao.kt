package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.SourceEntity
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
    val downloadMode: String
)

@Dao
interface NovelDao {
    @Query("""
        SELECT n.id, n.title, n.author, n.description, n.cover_url AS coverUrl, 
               n.status, l.added_at AS addedAt, l.last_opened_at AS lastOpenedAt, 
               l.download_mode AS downloadMode
        FROM novels n
        INNER JOIN library_entries l ON n.id = l.novel_id
        WHERE l.profile_id = :profileId
        ORDER BY COALESCE(l.last_opened_at, l.added_at) DESC
    """)
    fun getNovelsForProfile(profileId: String): Flow<List<NovelWithEntry>>

    @Query("SELECT * FROM novels WHERE id = :novelId LIMIT 1")
    suspend fun getNovelById(novelId: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Query("SELECT * FROM sources WHERE source_url = :sourceUrl LIMIT 1")
    suspend fun getSourceByUrl(sourceUrl: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE novel_id = :novelId")
    suspend fun getSourcesForNovel(novelId: String): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryEntry(entry: LibraryEntryEntity)

    @Query("DELETE FROM library_entries WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun removeNovelFromLibrary(profileId: String, novelId: String)

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
}
