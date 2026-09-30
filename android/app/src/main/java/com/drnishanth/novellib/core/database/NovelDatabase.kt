package com.drnishanth.novellib.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.dao.NotificationDao
import com.drnishanth.novellib.core.database.dao.NovelDao
import com.drnishanth.novellib.core.database.dao.ReaderPreferencesDao
import com.drnishanth.novellib.core.database.dao.ReadingProgressDao
import com.drnishanth.novellib.core.database.dao.SourceDefinitionDao
import com.drnishanth.novellib.core.database.dao.SyncDeviceDao
import com.drnishanth.novellib.core.database.dao.UserProfileDao
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NotificationEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.core.database.entities.ReadingProgressEntity
import com.drnishanth.novellib.core.database.entities.SourceDefinitionEntity
import com.drnishanth.novellib.core.database.entities.SourceEntity
import com.drnishanth.novellib.core.database.entities.SyncDeviceEntity
import com.drnishanth.novellib.core.database.entities.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        NovelEntity::class,
        SourceEntity::class,
        ChapterEntity::class,
        LibraryEntryEntity::class,
        ReadingProgressEntity::class,
        ReaderPreferencesEntity::class,
        NotificationEntity::class,
        SourceDefinitionEntity::class,
        SyncDeviceEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readerPreferencesDao(): ReaderPreferencesDao
    abstract fun sourceDefinitionDao(): SourceDefinitionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun syncDeviceDao(): SyncDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: NovelDatabase? = null

        fun getInstance(context: Context): NovelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovelDatabase::class.java,
                    "novellib.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
