package com.drnishanth.novellib.downloads

import com.drnishanth.novellib.core.database.entities.ChapterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RetentionPolicyEngineTest {

    @Test
    fun testCacheCleanupEvictsOldestCacheWhileProtectingOffline() = runBlocking {
        val fakeDao = FakeChapterDao()
        val engine = RetentionPolicyEngine(fakeDao)

        val tempDir = File.createTempFile("cache_test", "").apply {
            delete()
            mkdir()
        }

        try {
            // Create 3 cached chapters and 2 offline chapters
            for (i in 1..3) {
                val f = File(tempDir, "cached_$i.html").apply { writeText("Cached content $i") }
                fakeDao.chapters.add(
                    ChapterEntity(
                        id = "cache-$i",
                        novelId = "novel-1",
                        sourceId = "source-1",
                        chapterNumber = i,
                        title = "Cached Chapter $i",
                        sourceUrl = "https://example.com/c-$i",
                        downloadState = "available",
                        retentionPolicy = "cache",
                        filePath = f.absolutePath,
                        downloadedAt = i * 1000L // i=1 is oldest
                    )
                )
            }

            for (i in 4..5) {
                val f = File(tempDir, "offline_$i.html").apply { writeText("Offline content $i") }
                fakeDao.chapters.add(
                    ChapterEntity(
                        id = "offline-$i",
                        novelId = "novel-1",
                        sourceId = "source-1",
                        chapterNumber = i,
                        title = "Offline Chapter $i",
                        sourceUrl = "https://example.com/o-$i",
                        downloadState = "available",
                        retentionPolicy = "offline",
                        filePath = f.absolutePath,
                        downloadedAt = i * 1000L
                    )
                )
            }

            // Limit max cached chapters to 1
            val evictedCount = engine.cleanCache(maxCachedChapters = 1)

            assertEquals("Should evict 2 oldest cached chapters (cache-1 and cache-2)", 2, evictedCount)

            // Verify cache-1 and cache-2 files were deleted
            val cache1 = fakeDao.getChapterById("cache-1")!!
            val cache2 = fakeDao.getChapterById("cache-2")!!
            val cache3 = fakeDao.getChapterById("cache-3")!!

            assertEquals("not_downloaded", cache1.downloadState)
            assertEquals("not_downloaded", cache2.downloadState)
            assertEquals("available", cache3.downloadState) // Most recent cached chapter kept

            // Verify offline chapters 4 and 5 were NOT touched
            val off4 = fakeDao.getChapterById("offline-4")!!
            val off5 = fakeDao.getChapterById("offline-5")!!
            assertEquals("available", off4.downloadState)
            assertEquals("available", off5.downloadState)
            assertTrue(File(off4.filePath!!).exists())
            assertTrue(File(off5.filePath!!).exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
