package com.drnishanth.novellib.data.repository

import android.content.Context
import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.dao.NovelDao
import com.drnishanth.novellib.core.database.dao.NovelWithEntry
import com.drnishanth.novellib.core.database.dao.ReaderPreferencesDao
import com.drnishanth.novellib.core.database.dao.ReadingProgressDao
import com.drnishanth.novellib.core.database.dao.SourceDefinitionDao
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.core.database.entities.ReadingProgressEntity
import com.drnishanth.novellib.core.database.entities.SourceEntity
import com.drnishanth.novellib.scraping.engine.DefaultSourceDefinitions
import com.drnishanth.novellib.scraping.engine.RollbackManager
import com.drnishanth.novellib.scraping.engine.SourceDefinitionEngine
import com.drnishanth.novellib.scraping.models.SourceDefinition
import com.drnishanth.novellib.downloads.DownloadManager
import com.drnishanth.novellib.downloads.StoragePolicyManager
import com.drnishanth.novellib.downloads.models.RetentionPolicy
import com.drnishanth.novellib.downloads.models.StoragePolicy
import com.drnishanth.novellib.core.database.dao.NotificationDao
import com.drnishanth.novellib.core.database.entities.NotificationEntity
import com.drnishanth.novellib.core.notifications.NovelNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class NovelRepository(
    private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readerPreferencesDao: ReaderPreferencesDao,
    private val sourceDefinitionDao: SourceDefinitionDao,
    private val notificationDao: NotificationDao? = null,
    private val scraperEngine: SourceDefinitionEngine = SourceDefinitionEngine(),
    private val rollbackManager: RollbackManager? = null,
    val downloadManager: DownloadManager? = null,
    val storagePolicyManager: StoragePolicyManager? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getLibraryNovels(profileId: String): Flow<List<NovelWithEntry>> {
        return novelDao.getNovelsForProfile(profileId)
    }

    suspend fun getNovel(novelId: String): NovelEntity? {
        return novelDao.getNovelById(novelId)
    }

    fun getChapters(novelId: String): Flow<List<ChapterEntity>> {
        return chapterDao.getChaptersForNovel(novelId)
    }

    suspend fun getChapter(chapterId: String): ChapterEntity? {
        return chapterDao.getChapterById(chapterId)
    }

    fun getReadingProgress(profileId: String, novelId: String): Flow<ReadingProgressEntity?> {
        return readingProgressDao.getProgress(profileId, novelId)
    }

    suspend fun getLibraryEntry(profileId: String, novelId: String): LibraryEntryEntity? {
        return novelDao.getLibraryEntry(profileId, novelId)
    }

    suspend fun updateDownloadPolicy(
        profileId: String,
        novelId: String,
        mode: String,
        limit: Int,
        autoDownload: Boolean
    ) {
        novelDao.updateDownloadPolicy(profileId, novelId, mode, limit, autoDownload)
        if (autoDownload) {
            syncNovelStoragePolicy(profileId, novelId)
        }
    }

    suspend fun syncNovelStoragePolicy(profileId: String, novelId: String) {
        val entry = novelDao.getLibraryEntry(profileId, novelId) ?: return
        if (storagePolicyManager == null || downloadManager == null) return

        val progress = readingProgressDao.getProgressDirect(profileId, novelId)
        val lastReadNum = progress?.chapterId?.let { chapterDao.getChapterById(it)?.chapterNumber } ?: 0

        val policy = StoragePolicy.fromString(entry.downloadMode)
        val chaptersToDownload = storagePolicyManager.resolveChaptersToDownload(
            novelId = novelId,
            storagePolicy = policy,
            limit = entry.downloadLimit,
            lastReadChapterNumber = lastReadNum
        )

        if (chaptersToDownload.isNotEmpty()) {
            downloadManager.enqueueChapters(chaptersToDownload.map { it.id }, RetentionPolicy.OFFLINE)
        }
    }

    suspend fun saveReadingProgress(
        profileId: String,
        novelId: String,
        chapterId: String,
        position: Int,
        progressPercent: Float
    ) {
        readingProgressDao.saveProgress(
            ReadingProgressEntity(
                profileId = profileId,
                novelId = novelId,
                chapterId = chapterId,
                position = position,
                progressPercent = progressPercent,
                updatedAt = System.currentTimeMillis()
            )
        )
        novelDao.updateLastOpened(profileId, novelId)

        // Trigger automatic downloading of next chapters if configured
        val entry = novelDao.getLibraryEntry(profileId, novelId)
        if (entry != null && entry.autoDownloadEnabled && storagePolicyManager != null && downloadManager != null) {
            val chapter = chapterDao.getChapterById(chapterId)
            val currentChapterNum = chapter?.chapterNumber ?: 0
            val policy = StoragePolicy.fromString(entry.downloadMode)
            val toDownload = storagePolicyManager.resolveChaptersToDownload(
                novelId = novelId,
                storagePolicy = policy,
                limit = entry.downloadLimit,
                lastReadChapterNumber = currentChapterNum
            )
            if (toDownload.isNotEmpty()) {
                downloadManager.enqueueChapters(toDownload.map { it.id }, RetentionPolicy.OFFLINE)
            }
        }
    }

    fun getReaderPreferences(profileId: String): Flow<ReaderPreferencesEntity?> {
        return readerPreferencesDao.getPreferences(profileId)
    }

    suspend fun saveReaderPreferences(prefs: ReaderPreferencesEntity) {
        readerPreferencesDao.savePreferences(prefs)
    }

    suspend fun removeFromLibrary(profileId: String, novelId: String) {
        novelDao.removeNovelFromLibrary(profileId, novelId)
    }

    /**
     * Resolves matching source definition and scrapes novel details + chapter index,
     * persisting them to Room and linking to the current user profile.
     */
    suspend fun importNovelFromUrl(url: String, profileId: String): Result<NovelEntity> = withContext(Dispatchers.IO) {
        try {
            val definition = findMatchingDefinition(url)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("No compatible source definition found for URL: $url")
                )

            // Scrape novel info & chapters using the declarative engine
            val (scrapedNovel, scrapedChapters) = scraperEngine.scrapeNovel(url, definition)

            // Check if source already exists
            val existingSource = novelDao.getSourceByUrl(url)
            val novelId = existingSource?.novelId ?: UUID.randomUUID().toString()
            val sourceId = existingSource?.id ?: UUID.randomUUID().toString()

            val novelEntity = NovelEntity(
                id = novelId,
                title = scrapedNovel.title,
                author = scrapedNovel.author,
                description = scrapedNovel.description,
                coverUrl = scrapedNovel.coverUrl,
                status = scrapedNovel.status,
                updatedAt = System.currentTimeMillis()
            )
            novelDao.insertNovel(novelEntity)

            val sourceEntity = SourceEntity(
                id = sourceId,
                novelId = novelId,
                sourceDefinitionId = definition.id,
                sourceUrl = url,
                lastCheckedAt = System.currentTimeMillis(),
                lastSuccessfulCheckAt = System.currentTimeMillis()
            )
            novelDao.insertSource(sourceEntity)

            // Map and persist chapters
            val chapterEntities = scrapedChapters.map { item ->
                ChapterEntity(
                    id = UUID.randomUUID().toString(),
                    novelId = novelId,
                    sourceId = sourceId,
                    chapterNumber = item.number,
                    title = item.title,
                    sourceUrl = item.url,
                    publishedAt = item.publishedAt,
                    discoveredAt = System.currentTimeMillis()
                )
            }
            chapterDao.insertChapters(chapterEntities)

            // Add novel to the user's library
            val libraryEntry = LibraryEntryEntity(
                profileId = profileId,
                novelId = novelId,
                addedAt = System.currentTimeMillis()
            )
            novelDao.insertLibraryEntry(libraryEntry)

            rollbackManager?.recordSuccess(definition.id)
            Result.success(novelEntity)
        } catch (e: Exception) {
            findMatchingDefinition(url)?.let { def ->
                rollbackManager?.recordFailure(def.id)
            }
            Result.failure(e)
        }
    }

    /**
     * Retrieves chapter text. If already downloaded, loads from local private storage;
     * otherwise scrapes online on-demand.
     */
    suspend fun loadChapterContent(chapterId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chapter = chapterDao.getChapterById(chapterId)
                ?: return@withContext Result.failure(IllegalArgumentException("Chapter not found"))

            // Check if downloaded file exists
            if (!chapter.filePath.isNullOrBlank()) {
                val file = File(chapter.filePath)
                if (file.exists()) {
                    return@withContext Result.success(file.readText(Charsets.UTF_8))
                }
            }

            // Scrape on demand
            val definition = findMatchingDefinition(chapter.sourceUrl)
                ?: return@withContext Result.failure(
                    IllegalStateException("No source definition available to read chapter")
                )

            val scraped = scraperEngine.scrapeChapterContent(chapter.sourceUrl, definition)

            // Save to private internal storage
            val chaptersDir = File(context.filesDir, "chapters/${chapter.novelId}")
            if (!chaptersDir.exists()) chaptersDir.mkdirs()

            val targetFile = File(chaptersDir, "${scraped.contentHash}.html")
            targetFile.writeText(scraped.htmlContent, Charsets.UTF_8)

            // Update chapter status in DB
            chapterDao.updateDownloadState(
                chapterId = chapter.id,
                state = "available",
                filePath = targetFile.absolutePath,
                contentHash = scraped.contentHash,
                downloadedAt = System.currentTimeMillis()
            )

            rollbackManager?.recordSuccess(definition.id)
            Result.success(scraped.htmlContent)
        } catch (e: Exception) {
            val chapter = chapterDao.getChapterById(chapterId)
            if (chapter != null) {
                findMatchingDefinition(chapter.sourceUrl)?.let { def ->
                    rollbackManager?.recordFailure(def.id)
                }
            }
            Result.failure(e)
        }
    }

    private suspend fun findMatchingDefinition(url: String): SourceDefinition? {
        // First check custom definitions in Room
        val customDefs = sourceDefinitionDao.getAllDefinitionsDirect()
        for (entity in customDefs) {
            try {
                val parsed = json.decodeFromString<SourceDefinition>(entity.jsonContent)
                if (scraperEngine.matches(url, parsed)) return parsed
            } catch (_: Exception) {}
        }

        // Fallback to built-in definitions
        return DefaultSourceDefinitions.BUILTIN_DEFINITIONS.firstOrNull {
            scraperEngine.matches(url, it)
        }
    }

    suspend fun updateNotificationPreference(profileId: String, novelId: String, enabled: Boolean) {
        novelDao.updateNotificationPreference(profileId, novelId, enabled)
    }

    fun getNotifications(profileId: String): Flow<List<NotificationEntity>> {
        return notificationDao?.getNotificationsForProfile(profileId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun markNotificationAsRead(id: String) {
        notificationDao?.markAsRead(id)
    }

    suspend fun clearAllNotifications(profileId: String) {
        notificationDao?.clearAllForProfile(profileId)
    }

    /**
     * Checks a specific novel for newly published chapters from its connected sources.
     * Inserts new chapters, notifies interested profiles, and auto-queues downloads if configured.
     */
    suspend fun checkNovelUpdates(novelId: String): Result<List<ChapterEntity>> = withContext(Dispatchers.IO) {
        try {
            val novel = novelDao.getNovelById(novelId)
                ?: return@withContext Result.failure(IllegalArgumentException("Novel not found"))
            val sources = novelDao.getSourcesForNovel(novelId)
            if (sources.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val allNewChapters = mutableListOf<ChapterEntity>()
            val existingChapters = chapterDao.getChaptersListForNovel(novelId)
            val existingUrls = existingChapters.map { it.sourceUrl }.toSet()
            val existingNumbers = existingChapters.map { it.chapterNumber }.toSet()

            for (source in sources) {
                val def = findMatchingDefinition(source.sourceUrl) ?: continue
                val (_, scrapedChapters) = scraperEngine.scrapeNovel(source.sourceUrl, def)

                val newScraped = scrapedChapters.filter {
                    it.url !in existingUrls && it.number !in existingNumbers
                }

                if (newScraped.isNotEmpty()) {
                    val newEntities = newScraped.map { item ->
                        ChapterEntity(
                            id = UUID.randomUUID().toString(),
                            novelId = novelId,
                            sourceId = source.id,
                            chapterNumber = item.number,
                            title = item.title,
                            sourceUrl = item.url,
                            publishedAt = item.publishedAt,
                            discoveredAt = System.currentTimeMillis()
                        )
                    }
                    chapterDao.insertChapters(newEntities)
                    allNewChapters.addAll(newEntities)
                }

                novelDao.insertSource(
                    source.copy(
                        lastCheckedAt = System.currentTimeMillis(),
                        lastSuccessfulCheckAt = System.currentTimeMillis()
                    )
                )
                rollbackManager?.recordSuccess(def.id)
            }

            if (allNewChapters.isNotEmpty()) {
                val libraryEntries = novelDao.getLibraryEntriesForNovel(novelId)
                for (entry in libraryEntries) {
                    if (entry.notificationsEnabled) {
                        NovelNotificationManager.showNewChaptersNotification(
                            context = context,
                            profileId = entry.profileId,
                            novelId = novelId,
                            novelTitle = novel.title,
                            newChaptersCount = allNewChapters.size,
                            firstChapterTitle = allNewChapters.firstOrNull()?.title
                        )

                        notificationDao?.insertNotification(
                            NotificationEntity(
                                id = UUID.randomUUID().toString(),
                                profileId = entry.profileId,
                                novelId = novelId,
                                chapterId = allNewChapters.firstOrNull()?.id,
                                type = "new_chapter",
                                title = "New Chapters: ${novel.title}",
                                message = "${allNewChapters.size} new chapter(s) released: ${allNewChapters.firstOrNull()?.title ?: ""}",
                                read = false,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }

                    if (entry.autoDownloadEnabled) {
                        syncNovelStoragePolicy(entry.profileId, novelId)
                    }
                }
            }

            Result.success(allNewChapters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks all tracked novels in the library for chapter updates, respecting rate limits.
     */
    suspend fun checkAllNovelsUpdates(): Map<String, List<ChapterEntity>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, List<ChapterEntity>>()
        val trackedNovels = novelDao.getAllNovels()

        for (novel in trackedNovels) {
            try {
                val newChapters = checkNovelUpdates(novel.id).getOrDefault(emptyList())
                if (newChapters.isNotEmpty()) {
                    results[novel.id] = newChapters
                }
                // Polite delay between novel sources (1.2s)
                delay(1200)
            } catch (_: Exception) {}
        }
        results
    }
}
