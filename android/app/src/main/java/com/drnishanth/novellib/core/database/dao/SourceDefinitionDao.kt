package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drnishanth.novellib.core.database.entities.SourceDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDefinitionDao {
    @Query("SELECT * FROM source_definitions WHERE enabled = 1")
    fun getAllEnabledDefinitions(): Flow<List<SourceDefinitionEntity>>

    @Query("SELECT * FROM source_definitions WHERE id = :id LIMIT 1")
    suspend fun getDefinitionById(id: String): SourceDefinitionEntity?

    @Query("SELECT * FROM source_definitions")
    suspend fun getAllDefinitionsDirect(): List<SourceDefinitionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefinition(definition: SourceDefinitionEntity)

    @Update
    suspend fun updateDefinition(definition: SourceDefinitionEntity)
}
