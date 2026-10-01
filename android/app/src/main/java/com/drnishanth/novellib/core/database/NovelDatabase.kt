package com.drnishanth.novellib.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.drnishanth.novellib.core.database.dao.ReadChapterDao
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.ReadChapterEntity
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
        SyncDeviceEntity::class,
        ReadChapterEntity::class
    ],
    version = 5,
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
    abstract fun readChapterDao(): ReadChapterDao

    companion object {
        @Volatile
        private var INSTANCE: NovelDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reader_preferences ADD COLUMN text_brightness REAL NOT NULL DEFAULT 0.85")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `read_chapters` (
                        `profile_id` TEXT NOT NULL,
                        `novel_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL,
                        `is_read` INTEGER NOT NULL DEFAULT 1,
                        `read_at` INTEGER NOT NULL,
                        PRIMARY KEY(`profile_id`, `chapter_id`),
                        FOREIGN KEY(`profile_id`) REFERENCES `user_profiles`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`novel_id`) REFERENCES `novels`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_read_chapters_profile_id_novel_id` ON `read_chapters` (`profile_id`, `novel_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_read_chapters_novel_id` ON `read_chapters` (`novel_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_read_chapters_chapter_id` ON `read_chapters` (`chapter_id`)")
            }
        }

        fun getInstance(context: Context): NovelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovelDatabase::class.java,
                    "novellib.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
