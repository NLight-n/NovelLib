package com.drnishanth.novellib.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "avatar")
    val avatar: String? = null,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String? = null,

    @ColumnInfo(name = "password_enabled")
    val passwordEnabled: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_active_at")
    val lastActiveAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "blocked_tags")
    val blockedTags: List<String> = emptyList()
)
