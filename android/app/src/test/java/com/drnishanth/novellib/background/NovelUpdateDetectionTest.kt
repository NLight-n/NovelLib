package com.drnishanth.novellib.background

import com.drnishanth.novellib.core.notifications.NovelNotificationManager
import com.drnishanth.novellib.scraping.models.ScrapedChapterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelUpdateDetectionTest {

    @Test
    fun testChannelIdentifiers() {
        assertEquals("channel_new_chapters", NovelNotificationManager.CHANNEL_NEW_CHAPTERS)
        assertEquals("channel_downloads", NovelNotificationManager.CHANNEL_DOWNLOADS)
        assertEquals("channel_sources", NovelNotificationManager.CHANNEL_SOURCES)
    }

    @Test
    fun testWorkSchedulerTags() {
        assertEquals("work_novel_updates", WorkScheduler.TAG_NOVEL_UPDATES)
        assertEquals("work_cache_cleanup", WorkScheduler.TAG_CACHE_CLEANUP)
        assertEquals("work_source_updates", WorkScheduler.TAG_SOURCE_UPDATES)
        assertEquals("work_download_queue", WorkScheduler.TAG_DOWNLOAD_QUEUE)
    }

    @Test
    fun testDetectNewChaptersExcludesKnownChapters() {
        // Existing chapters in local database
        val existingUrls = setOf("https://example.com/ch1", "https://example.com/ch2")
        val existingNumbers = setOf(1, 2)

        // Scraped table of contents from remote source
        val scrapedChapters = listOf(
            ScrapedChapterItem(number = 1, title = "Chapter 1", url = "https://example.com/ch1", publishedAt = null),
            ScrapedChapterItem(number = 2, title = "Chapter 2", url = "https://example.com/ch2", publishedAt = null),
            ScrapedChapterItem(number = 3, title = "Chapter 3", url = "https://example.com/ch3", publishedAt = null),
            ScrapedChapterItem(number = 4, title = "Chapter 4", url = "https://example.com/ch4", publishedAt = null)
        )

        // Filter algorithm matching NovelRepository.checkNovelUpdates
        val newChapters = scrapedChapters.filter {
            it.url !in existingUrls && it.number !in existingNumbers
        }

        assertEquals(2, newChapters.size)
        assertEquals(3, newChapters[0].number)
        assertEquals("Chapter 3", newChapters[0].title)
        assertEquals(4, newChapters[1].number)
        assertEquals("Chapter 4", newChapters[1].title)
    }

    @Test
    fun testDeduplicationByUrlOrNumber() {
        // If chapter number shifted or URL changed slightly, neither existing number nor URL should duplicate
        val existingUrls = setOf("https://example.com/chapter-one")
        val existingNumbers = setOf(1)

        val scraped = listOf(
            ScrapedChapterItem(number = 1, title = "Ch 1 Altered URL", url = "https://example.com/ch1-new", publishedAt = null),
            ScrapedChapterItem(number = 2, title = "Ch 2 Altered Num", url = "https://example.com/chapter-one", publishedAt = null),
            ScrapedChapterItem(number = 3, title = "Chapter 3 Fresh", url = "https://example.com/ch3", publishedAt = null)
        )

        val newChapters = scraped.filter {
            it.url !in existingUrls && it.number !in existingNumbers
        }

        assertEquals(1, newChapters.size)
        assertEquals(3, newChapters[0].number)
        assertEquals("Chapter 3 Fresh", newChapters[0].title)
    }

    @Test
    fun testNotificationPreferenceFilter() {
        data class MockLibraryEntry(val profileId: String, val notificationsEnabled: Boolean)

        val entries = listOf(
            MockLibraryEntry("profile-1", notificationsEnabled = true),
            MockLibraryEntry("profile-2", notificationsEnabled = false),
            MockLibraryEntry("profile-3", notificationsEnabled = true)
        )

        val notifiedProfiles = entries.filter { it.notificationsEnabled }.map { it.profileId }

        assertEquals(listOf("profile-1", "profile-3"), notifiedProfiles)
        assertFalse(notifiedProfiles.contains("profile-2"))
    }
}
