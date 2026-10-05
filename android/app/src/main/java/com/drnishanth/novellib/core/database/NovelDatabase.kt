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
import androidx.room.TypeConverters
import com.drnishanth.novellib.core.database.entities.NovelTagCrossRef
import com.drnishanth.novellib.core.database.entities.TagEntity
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
        ReadChapterEntity::class,
        TagEntity::class,
        NovelTagCrossRef::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `is_warning` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `novel_tag_cross_ref` (
                        `novel_id` TEXT NOT NULL,
                        `tag_id` TEXT NOT NULL,
                        PRIMARY KEY(`novel_id`, `tag_id`),
                        FOREIGN KEY(`novel_id`) REFERENCES `novels`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_tag_cross_ref_novel_id` ON `novel_tag_cross_ref` (`novel_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_tag_cross_ref_tag_id` ON `novel_tag_cross_ref` (`tag_id`)")

                db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `blocked_tags` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `addiction_limit` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `session_chapters_read` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `locked_until` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): NovelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovelDatabase::class.java,
                    "novellib.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
