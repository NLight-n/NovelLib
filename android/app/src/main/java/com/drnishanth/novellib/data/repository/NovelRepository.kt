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
import com.drnishanth.novellib.scraping.engine.SourceDefinitionEngine
import com.drnishanth.novellib.scraping.models.SourceDefinition
import kotlinx.coroutines.Dispatchers
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
    private val scraperEngine: SourceDefinitionEngine = SourceDefinitionEngine()
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

            Result.success(novelEntity)
        } catch (e: Exception) {
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

            Result.success(scraped.htmlContent)
        } catch (e: Exception) {
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
}
