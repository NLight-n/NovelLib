package com.drnishanth.novellib.downloads

import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.downloads.models.HybridPolicyOption
import com.drnishanth.novellib.downloads.models.StoragePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoragePolicyManagerTest {

    private lateinit var fakeChapterDao: FakeChapterDao
    private lateinit var storagePolicyManager: StoragePolicyManager

    @Before
    fun setup() {
        fakeChapterDao = FakeChapterDao()
        storagePolicyManager = StoragePolicyManager(fakeChapterDao)

        // Seed 10 chapters
        val chapters = (1..10).map { i ->
            ChapterEntity(
                id = "chap-$i",
                novelId = "novel-1",
                sourceId = "source-1",
                chapterNumber = i,
                title = "Chapter $i",
                sourceUrl = "https://example.com/chap-$i",
                downloadState = if (i == 1) "available" else "not_downloaded"
            )
        }
        fakeChapterDao.chapters.addAll(chapters)
    }

    @Test
    fun testOnlinePolicyReturnsNoDownloads() = runBlocking {
        val result = storagePolicyManager.resolveChaptersToDownload(
            novelId = "novel-1",
            storagePolicy = StoragePolicy.ONLINE
        )
        assertTrue("Online mode should never auto-download chapters", result.isEmpty())
    }

    @Test
    fun testOfflinePolicyReturnsAllNonDownloadedChapters() = runBlocking {
        val result = storagePolicyManager.resolveChaptersToDownload(
            novelId = "novel-1",
            storagePolicy = StoragePolicy.OFFLINE
        )
        assertEquals(9, result.size) // Chapters 2-10 (Chapter 1 is already 'available')
    }

    @Test
    fun testHybridLatestNChapters() = runBlocking {
        val result = storagePolicyManager.resolveChaptersToDownload(
            novelId = "novel-1",
            storagePolicy = StoragePolicy.HYBRID,
            hybridOption = HybridPolicyOption.LATEST_N_CHAPTERS,
            limit = 3
        )
        assertEquals(3, result.size)
        val numbers = result.map { it.chapterNumber }
        assertEquals(listOf(10, 9, 8), numbers)
    }

    @Test
    fun testHybridNextNUnreadChapters() = runBlocking {
        // User read up to chapter 3
        val result = storagePolicyManager.resolveChaptersToDownload(
            novelId = "novel-1",
            storagePolicy = StoragePolicy.HYBRID,
            hybridOption = HybridPolicyOption.LATEST_N_UNREAD_CHAPTERS,
            limit = 4,
            lastReadChapterNumber = 3
        )
        assertEquals(4, result.size)
        val numbers = result.map { it.chapterNumber }
        assertEquals(listOf(4, 5, 6, 7), numbers)
    }
}

class FakeChapterDao : ChapterDao {
    val chapters = mutableListOf<ChapterEntity>()

    override fun getChaptersForNovel(novelId: String): Flow<List<ChapterEntity>> = flowOf(chapters)

    override suspend fun getChaptersListForNovel(novelId: String): List<ChapterEntity> =
        chapters.filter { it.novelId == novelId }

    override suspend fun getChapterById(chapterId: String): ChapterEntity? =
        chapters.firstOrNull { it.id == chapterId }

    override suspend fun getChapterByUrl(sourceUrl: String): ChapterEntity? =
        chapters.firstOrNull { it.sourceUrl == sourceUrl }

    override suspend fun insertChapters(chapters: List<ChapterEntity>) {
        this.chapters.addAll(chapters)
    }

    override suspend fun insertChapter(chapter: ChapterEntity) {
        this.chapters.add(chapter)
    }

    override suspend fun updateChapter(chapter: ChapterEntity) {}

    override suspend fun updateChapters(chapters: List<ChapterEntity>) {}

    override suspend fun updateDownloadState(
        chapterId: String,
        state: String,
        filePath: String?,
        contentHash: String?,
        downloadedAt: Long?,
        updatedAt: Long
    ) {
        val idx = chapters.indexOfFirst { it.id == chapterId }
        if (idx >= 0) {
            chapters[idx] = chapters[idx].copy(
                downloadState = state,
                filePath = filePath,
                contentHash = contentHash,
                downloadedAt = downloadedAt,
                updatedAt = updatedAt
            )
        }
    }

    override suspend fun updateRetentionPolicy(chapterId: String, policy: String) {
        val idx = chapters.indexOfFirst { it.id == chapterId }
        if (idx >= 0) {
            chapters[idx] = chapters[idx].copy(retentionPolicy = policy)
        }
    }

    override suspend fun getQueuedChapters(): List<ChapterEntity> =
        chapters.filter { it.downloadState == "queued" }

    override suspend fun getCachedChaptersSortedByDownloadedAt(): List<ChapterEntity> =
        chapters.filter { it.retentionPolicy == "cache" && it.downloadState == "available" }
            .sortedBy { it.downloadedAt ?: 0L }

    override suspend fun getChaptersAfterNumber(novelId: String, chapterNumber: Int, limit: Int): List<ChapterEntity> =
        chapters.filter { it.novelId == novelId && it.chapterNumber > chapterNumber }
            .sortedBy { it.chapterNumber }
            .take(limit)

    override suspend fun getLatestChapters(novelId: String, limit: Int): List<ChapterEntity> =
        chapters.filter { it.novelId == novelId }
            .sortedByDescending { it.chapterNumber }
            .take(limit)

    override suspend fun getChapterCountForNovel(novelId: String): Int = chapters.size

    override suspend fun getDownloadedChapterCount(novelId: String): Int =
        chapters.count { it.downloadState == "available" }
}
