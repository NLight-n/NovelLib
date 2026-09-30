package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderPreferencesDao {
    @Query("SELECT * FROM reader_preferences WHERE profile_id = :profileId LIMIT 1")
    fun getPreferences(profileId: String): Flow<ReaderPreferencesEntity?>

    @Query("SELECT * FROM reader_preferences WHERE profile_id = :profileId LIMIT 1")
    suspend fun getPreferencesDirect(profileId: String): ReaderPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(preferences: ReaderPreferencesEntity)
}
