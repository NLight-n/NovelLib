package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drnishanth.novellib.core.database.entities.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE profile_id = :profileId AND novel_id = :novelId LIMIT 1")
    fun getProgress(profileId: String, novelId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE profile_id = :profileId AND novel_id = :novelId LIMIT 1")
    suspend fun getProgressDirect(profileId: String, novelId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE profile_id = :profileId AND novel_id = :novelId")
    suspend fun deleteProgress(profileId: String, novelId: String)
}
