package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drnishanth.novellib.core.database.entities.SyncDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SyncDeviceEntity)

    @Update
    suspend fun updateDevice(device: SyncDeviceEntity)

    @Query("SELECT * FROM sync_devices WHERE device_id = :deviceId LIMIT 1")
    suspend fun getDeviceByDeviceId(deviceId: String): SyncDeviceEntity?

    @Query("SELECT * FROM sync_devices ORDER BY last_seen_at DESC")
    fun getAllDevices(): Flow<List<SyncDeviceEntity>>

    @Query("SELECT * FROM sync_devices WHERE trusted = 1 ORDER BY last_seen_at DESC")
    fun getTrustedDevices(): Flow<List<SyncDeviceEntity>>

    @Query("SELECT * FROM sync_devices WHERE trusted = 1 ORDER BY last_seen_at DESC")
    suspend fun getTrustedDevicesDirect(): List<SyncDeviceEntity>

    @Query("UPDATE sync_devices SET trusted = :trusted WHERE device_id = :deviceId")
    suspend fun setTrusted(deviceId: String, trusted: Boolean)

    @Query("UPDATE sync_devices SET last_seen_at = :timestamp WHERE device_id = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_devices WHERE device_id = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("DELETE FROM sync_devices")
    suspend fun clearAll()
}
