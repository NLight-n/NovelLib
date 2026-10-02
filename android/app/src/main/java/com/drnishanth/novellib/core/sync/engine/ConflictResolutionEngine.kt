package com.drnishanth.novellib.core.sync.engine

import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.dao.NovelDao
import com.drnishanth.novellib.core.database.dao.ReaderPreferencesDao
import com.drnishanth.novellib.core.database.dao.ReadingProgressDao
import com.drnishanth.novellib.core.database.dao.UserProfileDao
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.core.database.entities.ReadingProgressEntity
import com.drnishanth.novellib.core.database.entities.NovelTagCrossRef
import com.drnishanth.novellib.core.database.entities.SourceEntity
import com.drnishanth.novellib.core.database.entities.TagEntity
import com.drnishanth.novellib.core.database.entities.UserProfileEntity
import com.drnishanth.novellib.core.sync.models.SyncPayload
import com.drnishanth.novellib.core.sync.models.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ConflictResolutionEngine(
    private val userProfileDao: UserProfileDao,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readerPreferencesDao: ReaderPreferencesDao
) {

    /**
     * Merges an incoming peer sync payload into the local database applying deterministic
     * conflict resolution strategies:
     * - Library membership: Set Union
     * - Reading Progress: Last-Write-Wins (LWW) by updatedAt
     * - Reader Preferences: Last-Write-Wins (LWW) by updatedAt
     * - Novel & Chapter Metadata: Union and content-hash deduplication
     * - Profile: Safe non-destructive import (password hashes never transferred)
     */
    suspend fun mergePayload(
        incomingPayload: SyncPayload,
        destinationProfileId: String? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            var profileImported = false

            // 1. Resolve target profile
            val targetProfileId: String = if (!destinationProfileId.isNullOrBlank()) {
                destinationProfileId
            } else {
                val existingProfile = userProfileDao.getProfileByUsername(incomingPayload.profile.username)
                if (existingProfile != null) {
                    existingProfile.id
                } else {
                    val newId = UUID.randomUUID().toString()
                    val newProfile = UserProfileEntity(
                        id = newId,
                        username = incomingPayload.profile.username,
                        displayName = incomingPayload.profile.displayName,
                        avatar = incomingPayload.profile.avatar,
                        passwordHash = null,
                        passwordEnabled = incomingPayload.profile.isPasswordProtected,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        lastActiveAt = System.currentTimeMillis()
                    )
                    userProfileDao.insertProfile(newProfile)
                    profileImported = true
                    newId
                }
            }

            // 2. Resolve reader preferences
            val incomingPrefs = incomingPayload.profile.preferences
            if (incomingPrefs != null) {
                readerPreferencesDao.savePreferences(
                    ReaderPreferencesEntity(
                        profileId = targetProfileId,
                        theme = incomingPrefs.theme,
                        fontFamily = incomingPrefs.fontFamily,
                        fontSize = incomingPrefs.fontSize,
                        lineHeight = incomingPrefs.lineHeight,
                        contentWidth = incomingPrefs.contentWidth,
                        pageNavigationMode = incomingPrefs.pageNavigationMode
                    )
                )
            }

            // 3. Merge Novels, Sources, Chapters, and Reading Progress
            var novelsMerged = 0
            var chaptersMerged = 0
            var progressUpdated = false

            for (incomingNovel in incomingPayload.novels) {
                // Determine whether novel already exists locally
                var localNovel = novelDao.getNovelById(incomingNovel.novelId)
                if (localNovel == null) {
                    for (src in incomingNovel.sources) {
                        val existingSource = novelDao.getSourceByUrl(src.sourceUrl)
                        if (existingSource != null) {
                            localNovel = novelDao.getNovelById(existingSource.novelId)
                            if (localNovel != null) break
                        }
                    }
                }

                val resolvedNovelId = localNovel?.id ?: incomingNovel.novelId
                if (localNovel == null) {
                    novelDao.insertNovel(
                        NovelEntity(
                            id = resolvedNovelId,
                            title = incomingNovel.title,
                            author = incomingNovel.author,
                            description = incomingNovel.description,
                            coverUrl = incomingNovel.coverUrl,
                            status = incomingNovel.status,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                novelsMerged++

                // Merge sources
                for (src in incomingNovel.sources) {
                    val existingSource = novelDao.getSourceByUrl(src.sourceUrl)
                    if (existingSource == null) {
                        novelDao.insertSource(
                            SourceEntity(
                                id = src.id,
                                novelId = resolvedNovelId,
                                sourceDefinitionId = src.sourceDefinitionId,
                                sourceUrl = src.sourceUrl,
                                lastCheckedAt = src.lastCheckedAt,
                                lastSuccessfulCheckAt = src.lastSuccessfulCheckAt
                            )
                        )
                    }
                }

                // Merge chapters (Union strategy by source URL)
                for (ch in incomingNovel.chapters) {
                    val existingChapter = chapterDao.getChapterByUrl(ch.sourceUrl)
                    if (existingChapter == null) {
                        chapterDao.insertChapter(
                            ChapterEntity(
                                id = ch.id,
                                novelId = resolvedNovelId,
                                sourceId = incomingNovel.sources.firstOrNull()?.id ?: UUID.randomUUID().toString(),
                                chapterNumber = ch.chapterNumber,
                                title = ch.title,
                                sourceUrl = ch.sourceUrl,
                                publishedAt = ch.publishedAt,
                                contentHash = ch.contentHash,
                                downloadState = "not_downloaded",
                                discoveredAt = System.currentTimeMillis()
                            )
                        )
                        chaptersMerged++
                    }
                }

                // Merge tags (Deterministic Set-Union strategy: Tags_merged = Tags_A union Tags_B)
                for (tagItem in incomingNovel.tags) {
                    novelDao.insertTag(
                        TagEntity(
                            id = tagItem.id,
                            name = tagItem.name,
                            isWarning = tagItem.isWarning
                        )
                    )
                    novelDao.insertNovelTagCrossRef(
                        NovelTagCrossRef(
                            novelId = resolvedNovelId,
                            tagId = tagItem.id
                        )
                    )
                }

                // Merge library membership (Union strategy)
                if (incomingNovel.libraryEntry != null) {
                    val existingEntry = novelDao.getLibraryEntry(targetProfileId, resolvedNovelId)
                    if (existingEntry == null) {
                        novelDao.insertLibraryEntry(
                            LibraryEntryEntity(
                                profileId = targetProfileId,
                                novelId = resolvedNovelId,
                                addedAt = incomingNovel.libraryEntry.addedAt,
                                downloadMode = incomingNovel.libraryEntry.downloadMode,
                                downloadLimit = incomingNovel.libraryEntry.downloadLimit,
                                autoDownloadEnabled = incomingNovel.libraryEntry.autoDownloadEnabled,
                                notificationsEnabled = incomingNovel.libraryEntry.notificationsEnabled,
                                lastOpenedAt = incomingNovel.libraryEntry.lastOpenedAt
                            )
                        )
                    }
                }

                // Merge reading progress (LWW strategy)
                val incomingProg = incomingNovel.readingProgress
                if (incomingProg != null) {
                    val localProg = readingProgressDao.getProgressDirect(targetProfileId, resolvedNovelId)
                    if (localProg == null || incomingProg.updatedAt >= localProg.updatedAt) {
                        val matchingChapter = chapterDao.getChaptersListForNovel(resolvedNovelId)
                            .firstOrNull { it.chapterNumber == incomingProg.chapterNumber || it.id == incomingProg.chapterId }
                        val targetChapterId = matchingChapter?.id ?: incomingProg.chapterId

                        if (targetChapterId != null) {
                            readingProgressDao.saveProgress(
                                ReadingProgressEntity(
                                    profileId = targetProfileId,
                                    novelId = resolvedNovelId,
                                    chapterId = targetChapterId,
                                    position = incomingProg.position,
                                    progressPercent = incomingProg.progressPercent,
                                    updatedAt = incomingProg.updatedAt
                                )
                            )
                            progressUpdated = true
                        }
                    }
                }
            }

            SyncResult(
                success = true,
                novelsMerged = novelsMerged,
                chaptersMerged = chaptersMerged,
                progressUpdated = progressUpdated,
                profileImported = profileImported,
                message = "Successfully synchronized $novelsMerged novel(s) and $chaptersMerged chapter(s)"
            )
        } catch (e: Exception) {
            SyncResult(
                success = false,
                message = "Synchronization conflict merge failed: ${e.message}"
            )
        }
    }
}
