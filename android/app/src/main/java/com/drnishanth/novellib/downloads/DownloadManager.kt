package com.drnishanth.novellib.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.dao.SourceDefinitionDao
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.downloads.models.ChapterDownloadStatus
import com.drnishanth.novellib.downloads.models.DownloadState
import com.drnishanth.novellib.downloads.models.RetentionPolicy
import com.drnishanth.novellib.scraping.engine.DefaultSourceDefinitions
import com.drnishanth.novellib.scraping.engine.RollbackManager
import com.drnishanth.novellib.scraping.engine.SourceDefinitionEngine
import com.drnishanth.novellib.scraping.models.ChapterExtractionException
import com.drnishanth.novellib.scraping.models.ExtractionFailureReason
import com.drnishanth.novellib.scraping.models.SourceDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class DownloadManager(
    private val context: Context,
    private val chapterDao: ChapterDao,
    private val sourceDefinitionDao: SourceDefinitionDao,
    private val scraperEngine: SourceDefinitionEngine = SourceDefinitionEngine(),
    private val rollbackManager: RollbackManager? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val semaphore = Semaphore(2) // Max 2 concurrent downloads to respect rate limits

    private val _downloadStatuses = MutableStateFlow<Map<String, ChapterDownloadStatus>>(emptyMap())
    val downloadStatuses: StateFlow<Map<String, ChapterDownloadStatus>> = _downloadStatuses.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    var wifiOnly: Boolean = false

    /**
     * Enqueues a single chapter for background downloading.
     */
    fun enqueueChapter(chapterId: String, retentionPolicy: RetentionPolicy = RetentionPolicy.OFFLINE) {
        enqueueChapters(listOf(chapterId), retentionPolicy)
    }

    /**
     * Enqueues a list of chapters for background downloading.
     */
    fun enqueueChapters(chapterIds: List<String>, retentionPolicy: RetentionPolicy = RetentionPolicy.OFFLINE) {
        scope.launch {
            val updatedMap = _downloadStatuses.value.toMutableMap()
            for (id in chapterIds) {
                chapterDao.updateDownloadState(
                    chapterId = id,
                    state = DownloadState.QUEUED.value,
                    filePath = null,
                    contentHash = null,
                    downloadedAt = null
                )
                chapterDao.updateRetentionPolicy(id, retentionPolicy.value)
                updatedMap[id] = ChapterDownloadStatus(chapterId = id, state = DownloadState.QUEUED)
            }
            _downloadStatuses.value = updatedMap

            // Launch download tasks
            for (id in chapterIds) {
                if (activeJobs[id]?.isActive != true) {
                    val job = launchDownloadJob(id)
                    activeJobs[id] = job
                }
            }
        }
    }

    data class BatchDownloadResult(val successCount: Int, val failureCount: Int)

    private fun launchDownloadJob(chapterId: String): Job = scope.launch {
        semaphore.withPermit {
            try {
                downloadChapterDirect(chapterId)
            } finally {
                activeJobs.remove(chapterId)
            }
        }
    }

    /**
     * Directly downloads a single chapter synchronously within the calling coroutine.
     * Returns true if download was successful or already available, false otherwise.
     */
    suspend fun downloadChapterDirect(chapterId: String): Boolean = withContext(Dispatchers.IO) {
        // Check Wi-Fi constraint if enabled
        if (wifiOnly && !isWifiConnected()) {
            updateStatus(chapterId, DownloadState.FAILED, "Waiting for Wi-Fi connection")
            return@withContext false
        }

        val chapter = chapterDao.getChapterById(chapterId) ?: return@withContext false
        if (chapter.downloadState == DownloadState.AVAILABLE.value) {
            updateStatus(chapterId, DownloadState.AVAILABLE)
            return@withContext true
        }

        updateStatus(chapterId, DownloadState.DOWNLOADING)
        chapterDao.updateDownloadState(
            chapterId = chapterId,
            state = DownloadState.DOWNLOADING.value,
            filePath = null,
            contentHash = null,
            downloadedAt = null
        )

        try {
            // Rate-limit delay (500ms) between chapter scrapes
            delay(500)

            val definition = findMatchingDefinition(chapter.sourceUrl)
                ?: throw IllegalStateException("No compatible source definition for ${chapter.sourceUrl}")

            val scraped = scraperEngine.scrapeChapterContent(chapter.sourceUrl, definition)

            // Save to private internal storage
            val chaptersDir = File(context.filesDir, "chapters/${chapter.novelId}")
            if (!chaptersDir.exists()) chaptersDir.mkdirs()

            val targetFile = File(chaptersDir, "${scraped.contentHash}.html")
            targetFile.writeText(scraped.htmlContent, Charsets.UTF_8)

            chapterDao.updateDownloadState(
                chapterId = chapter.id,
                state = DownloadState.AVAILABLE.value,
                filePath = targetFile.absolutePath,
                contentHash = scraped.contentHash,
                downloadedAt = System.currentTimeMillis()
            )

            rollbackManager?.recordSuccess(definition.id)
            updateStatus(chapterId, DownloadState.AVAILABLE)
            true
        } catch (e: Exception) {
            val isActionRequired = (e as? ChapterExtractionException)?.let {
                it.reason == ExtractionFailureReason.AUTH_REQUIRED || it.reason == ExtractionFailureReason.CHALLENGE
            } ?: false
            val failureState = if (isActionRequired) DownloadState.ACTION_REQUIRED else DownloadState.FAILED

            chapterDao.updateDownloadState(
                chapterId = chapter.id,
                state = failureState.value,
                filePath = null,
                contentHash = null,
                downloadedAt = null
            )
            findMatchingDefinition(chapter.sourceUrl)?.let { def ->
                rollbackManager?.recordFailure(def.id)
            }
            updateStatus(chapterId, failureState, e.message)
            false
        }
    }

    /**
     * Sequentially processes all currently queued chapters in the database.
     * Ideal for background WorkManager tasks.
     */
    suspend fun processQueuedDownloads(): BatchDownloadResult = withContext(Dispatchers.IO) {
        val queued = chapterDao.getQueuedChapters()
        var success = 0
        var failure = 0
        for (chapter in queued) {
            val ok = downloadChapterDirect(chapter.id)
            if (ok) success++ else failure++
        }
        BatchDownloadResult(success, failure)
    }

    fun cancel(chapterId: String) {
        activeJobs[chapterId]?.cancel()
        activeJobs.remove(chapterId)
        scope.launch {
            chapterDao.updateDownloadState(
                chapterId = chapterId,
                state = DownloadState.NOT_DOWNLOADED.value,
                filePath = null,
                contentHash = null,
                downloadedAt = null
            )
            val updated = _downloadStatuses.value.toMutableMap()
            updated.remove(chapterId)
            _downloadStatuses.value = updated
        }
    }

    suspend fun deleteDownloadedChapter(chapterId: String): Boolean = withContext(Dispatchers.IO) {
        val chapter = chapterDao.getChapterById(chapterId) ?: return@withContext false
        chapter.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        chapterDao.updateDownloadState(
            chapterId = chapterId,
            state = DownloadState.NOT_DOWNLOADED.value,
            filePath = null,
            contentHash = null,
            downloadedAt = null
        )
        val updated = _downloadStatuses.value.toMutableMap()
        updated.remove(chapterId)
        _downloadStatuses.value = updated
        true
    }

    suspend fun deleteDownloadedNovel(novelId: String): Int = withContext(Dispatchers.IO) {
        val chapters = chapterDao.getChaptersListForNovel(novelId)
        var deletedCount = 0
        for (chapter in chapters) {
            if (chapter.downloadState == DownloadState.AVAILABLE.value) {
                chapter.filePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                chapterDao.updateDownloadState(
                    chapterId = chapter.id,
                    state = DownloadState.NOT_DOWNLOADED.value,
                    filePath = null,
                    contentHash = null,
                    downloadedAt = null
                )
                deletedCount++
            }
        }
        val novelDir = File(context.filesDir, "chapters/$novelId")
        if (novelDir.exists()) novelDir.deleteRecursively()
        deletedCount
    }

    private fun updateStatus(chapterId: String, state: DownloadState, error: String? = null) {
        val current = _downloadStatuses.value.toMutableMap()
        current[chapterId] = ChapterDownloadStatus(chapterId = chapterId, state = state, errorMessage = error)
        _downloadStatuses.value = current
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private suspend fun findMatchingDefinition(url: String): SourceDefinition? {
        val customDefs = sourceDefinitionDao.getAllDefinitionsDirect()
        for (entity in customDefs) {
            try {
                val parsed = json.decodeFromString<SourceDefinition>(entity.jsonContent)
                if (scraperEngine.matches(url, parsed)) return parsed
            } catch (_: Exception) {}
        }
        return DefaultSourceDefinitions.BUILTIN_DEFINITIONS.firstOrNull {
            scraperEngine.matches(url, it)
        }
    }
}
