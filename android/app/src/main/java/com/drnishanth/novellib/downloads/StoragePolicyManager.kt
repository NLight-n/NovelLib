package com.drnishanth.novellib.downloads

import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.downloads.models.HybridPolicyOption
import com.drnishanth.novellib.downloads.models.StoragePolicy

class StoragePolicyManager(
    private val chapterDao: ChapterDao
) {

    /**
     * Resolves the list of chapters that should be downloaded for a novel
     * according to the specified policy, limit, and current read progress chapter number.
     */
    suspend fun resolveChaptersToDownload(
        novelId: String,
        storagePolicy: StoragePolicy,
        hybridOption: HybridPolicyOption = HybridPolicyOption.LATEST_N_UNREAD_CHAPTERS,
        limit: Int = 10,
        lastReadChapterNumber: Int = 0
    ): List<ChapterEntity> {
        val allChapters = chapterDao.getChaptersListForNovel(novelId)
        if (allChapters.isEmpty()) return emptyList()

        return when (storagePolicy) {
            StoragePolicy.ONLINE -> emptyList()

            StoragePolicy.OFFLINE -> allChapters.filter { it.downloadState != "available" }

            StoragePolicy.HYBRID -> {
                when (hybridOption) {
                    HybridPolicyOption.LATEST_N_CHAPTERS -> {
                        allChapters
                            .sortedByDescending { it.chapterNumber }
                            .take(limit)
                            .filter { it.downloadState != "available" }
                    }

                    HybridPolicyOption.LATEST_N_UNREAD_CHAPTERS -> {
                        allChapters
                            .filter { it.chapterNumber > lastReadChapterNumber }
                            .sortedBy { it.chapterNumber }
                            .take(limit)
                            .filter { it.downloadState != "available" }
                    }

                    HybridPolicyOption.ALL_UNREAD_CHAPTERS -> {
                        allChapters
                            .filter { it.chapterNumber > lastReadChapterNumber }
                            .sortedBy { it.chapterNumber }
                            .filter { it.downloadState != "available" }
                    }

                    HybridPolicyOption.ALL_CHAPTERS -> {
                        allChapters.filter { it.downloadState != "available" }
                    }

                    HybridPolicyOption.MANUAL_SELECTION -> {
                        emptyList()
                    }
                }
            }
        }
    }
}
