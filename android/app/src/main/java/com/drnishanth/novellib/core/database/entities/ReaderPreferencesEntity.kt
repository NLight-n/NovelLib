package com.drnishanth.novellib.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "reader_preferences",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReaderPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "theme")
    val theme: String = "light", // light, dark, sepia, eink

    @ColumnInfo(name = "font_family")
    val fontFamily: String = "serif", // serif, sans, mono

    @ColumnInfo(name = "font_size")
    val fontSize: Float = 18f,

    @ColumnInfo(name = "line_height")
    val lineHeight: Float = 1.6f,

    @ColumnInfo(name = "content_width")
    val contentWidth: Float = 1.0f,

    @ColumnInfo(name = "paragraph_spacing")
    val paragraphSpacing: Float = 12f,

    @ColumnInfo(name = "margins")
    val margins: Int = 16,

    @ColumnInfo(name = "animation_enabled")
    val animationEnabled: Boolean = false, // false for E-Ink friendliness!

    @ColumnInfo(name = "page_navigation_mode")
    val pageNavigationMode: String = "scroll", // scroll, paging

    @ColumnInfo(name = "eink_full_refresh_interval")
    val einkFullRefreshInterval: Int = 10, // 0 = disabled, 1, 5, 10, 20 pages

    @ColumnInfo(name = "volume_keys_navigation")
    val volumeKeysNavigation: Boolean = true // Navigate pages via physical volume/e-reader buttons
)
