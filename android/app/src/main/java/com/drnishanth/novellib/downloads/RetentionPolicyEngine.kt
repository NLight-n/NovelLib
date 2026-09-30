package com.drnishanth.novellib.downloads

import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.downloads.models.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RetentionPolicyEngine(
    private val chapterDao: ChapterDao
) {

    /**
     * Removes expired or excess cached chapter files to free disk space,
     * while protecting all explicitly saved "offline" chapters.
     */
    suspend fun cleanCache(
        maxCachedChapters: Int = 20,
        maxCacheBytes: Long = 50 * 1024 * 1024L // 50MB
    ): Int = withContext(Dispatchers.IO) {
        val cachedChapters = chapterDao.getCachedChaptersSortedByDownloadedAt()
        if (cachedChapters.isEmpty()) return@withContext 0

        var deletedCount = 0
        var totalCachedBytes = cachedChapters.sumOf { chapter ->
            chapter.filePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.length() else 0L
            } ?: 0L
        }

        var currentCachedCount = cachedChapters.size

        // Evict oldest cached files first if count or size limits are exceeded
        for (chapter in cachedChapters) {
            val shouldEvict = currentCachedCount > maxCachedChapters || totalCachedBytes > maxCacheBytes
            if (!shouldEvict) break

            chapter.filePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    totalCachedBytes -= file.length()
                    file.delete()
                }
            }

            chapterDao.updateDownloadState(
                chapterId = chapter.id,
                state = DownloadState.NOT_DOWNLOADED.value,
                filePath = null,
                contentHash = chapter.contentHash,
                downloadedAt = null,
                updatedAt = System.currentTimeMillis()
            )

            deletedCount++
            currentCachedCount--
        }

        deletedCount
    }
}
