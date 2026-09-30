package com.drnishanth.novellib.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drnishanth.novellib.core.database.entities.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("SELECT * FROM notifications WHERE profile_id = :profileId ORDER BY created_at DESC")
    fun getNotificationsForProfile(profileId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE profile_id = :profileId ORDER BY created_at DESC")
    suspend fun getNotificationsForProfileDirect(profileId: String): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications WHERE profile_id = :profileId AND read = 0")
    fun getUnreadCount(profileId: String): Flow<Int>

    @Query("UPDATE notifications SET read = 1 WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: String)

    @Query("UPDATE notifications SET read = 1 WHERE profile_id = :profileId")
    suspend fun markAllAsRead(profileId: String)

    @Query("DELETE FROM notifications WHERE id = :notificationId")
    suspend fun deleteNotification(notificationId: String)

    @Query("DELETE FROM notifications WHERE profile_id = :profileId")
    suspend fun clearAllForProfile(profileId: String)
}
