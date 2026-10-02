package com.drnishanth.novellib.sync

import com.drnishanth.novellib.core.database.dao.ChapterDao
import com.drnishanth.novellib.core.database.dao.NovelDao
import com.drnishanth.novellib.core.database.dao.NovelWithEntry
import com.drnishanth.novellib.core.database.dao.ReaderPreferencesDao
import com.drnishanth.novellib.core.database.dao.ReadingProgressDao
import com.drnishanth.novellib.core.database.dao.UserProfileDao
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.core.database.entities.ReadingProgressEntity
import com.drnishanth.novellib.core.database.entities.SourceEntity
import com.drnishanth.novellib.core.database.entities.UserProfileEntity
import com.drnishanth.novellib.core.sync.engine.ConflictResolutionEngine
import com.drnishanth.novellib.core.sync.models.SyncChapterItem
import com.drnishanth.novellib.core.sync.models.SyncLibraryEntryItem
import com.drnishanth.novellib.core.sync.models.SyncNovelItem
import com.drnishanth.novellib.core.sync.models.SyncPayload
import com.drnishanth.novellib.core.sync.models.SyncProfileData
import com.drnishanth.novellib.core.sync.models.SyncReadingProgressItem
import com.drnishanth.novellib.core.sync.models.SyncSourceItem
import com.drnishanth.novellib.core.database.entities.NovelTagCrossRef
import com.drnishanth.novellib.core.database.entities.TagEntity
import com.drnishanth.novellib.core.database.models.NovelWithTags
import com.drnishanth.novellib.core.sync.models.SyncTagItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConflictResolutionEngineTest {

    private lateinit var userProfileDao: FakeUserProfileDao
    private lateinit var novelDao: FakeNovelDao
    private lateinit var chapterDao: FakeChapterDao
    private lateinit var readingProgressDao: FakeReadingProgressDao
    private lateinit var readerPreferencesDao: FakeReaderPreferencesDao
    private lateinit var engine: ConflictResolutionEngine

    @Before
    fun setup() {
        userProfileDao = FakeUserProfileDao()
        novelDao = FakeNovelDao()
        chapterDao = FakeChapterDao()
        readingProgressDao = FakeReadingProgressDao()
        readerPreferencesDao = FakeReaderPreferencesDao()

        engine = ConflictResolutionEngine(
            userProfileDao = userProfileDao,
            novelDao = novelDao,
            chapterDao = chapterDao,
            readingProgressDao = readingProgressDao,
            readerPreferencesDao = readerPreferencesDao
        )
    }

    @Test
    fun testLibraryUnion() = runBlocking {
        // Local profile has Novel A
        val profile = UserProfileEntity(id = "p1", username = "alice", displayName = "Alice", passwordHash = "h")
        userProfileDao.insertProfile(profile)

        val localNovelA = NovelEntity(id = "n_a", title = "Novel A", author = "Author A", description = "", coverUrl = null, status = "ongoing")
        novelDao.insertNovel(localNovelA)
        novelDao.insertLibraryEntry(LibraryEntryEntity(profileId = "p1", novelId = "n_a", addedAt = 100L))

        // Incoming payload has Novel B
        val incomingPayload = SyncPayload(
            senderDeviceId = "dev_peer",
            senderDeviceName = "Peer Tablet",
            profile = SyncProfileData(username = "alice", displayName = "Alice"),
            novels = listOf(
                SyncNovelItem(
                    novelId = "n_b",
                    title = "Novel B",
                    author = "Author B",
                    description = "",
                    sources = listOf(SyncSourceItem("s_b", "royalroad", "https://example.com/b")),
                    chapters = listOf(SyncChapterItem("c_b1", 1, "Chapter 1", "https://example.com/b/1")),
                    libraryEntry = SyncLibraryEntryItem(addedAt = 200L)
                )
            )
        )

        val result = engine.mergePayload(incomingPayload, destinationProfileId = "p1")
        assertTrue(result.success)

        // Both Novel A and Novel B must be present in library (Union strategy)
        val entryA = novelDao.getLibraryEntry("p1", "n_a")
        val entryB = novelDao.getLibraryEntry("p1", "n_b")
        assertTrue(entryA != null)
        assertTrue(entryB != null)
    }

    @Test
    fun testReadingProgressLwwNewerWins() = runBlocking {
        val profile = UserProfileEntity(id = "p1", username = "alice", displayName = "Alice", passwordHash = "h")
        userProfileDao.insertProfile(profile)

        val novel = NovelEntity(id = "n1", title = "Novel 1", author = "Author", description = "", coverUrl = null, status = "ongoing")
        novelDao.insertNovel(novel)
        chapterDao.insertChapter(ChapterEntity(id = "ch_5", novelId = "n1", sourceId = "s1", chapterNumber = 5, title = "Ch 5", sourceUrl = "url/5"))

        // Local progress is chapter 2 updated at timestamp 1000
        readingProgressDao.saveProgress(
            ReadingProgressEntity(profileId = "p1", novelId = "n1", chapterId = "ch_2", position = 100, progressPercent = 0.2f, updatedAt = 1000L)
        )

        // Remote progress is chapter 5 updated at timestamp 2000 (newer)
        val incomingPayload = SyncPayload(
            senderDeviceId = "dev_peer",
            senderDeviceName = "Peer Device",
            profile = SyncProfileData(username = "alice", displayName = "Alice"),
            novels = listOf(
                SyncNovelItem(
                    novelId = "n1",
                    title = "Novel 1",
                    author = "Author",
                    description = "",
                    readingProgress = SyncReadingProgressItem(chapterId = "ch_5", chapterNumber = 5, position = 500, progressPercent = 0.5f, updatedAt = 2000L)
                )
            )
        )

        val result = engine.mergePayload(incomingPayload, destinationProfileId = "p1")
        assertTrue(result.success)

        // Newer progress must overwrite local progress
        val currentProgress = readingProgressDao.getProgressDirect("p1", "n1")
        assertEquals(2000L, currentProgress?.updatedAt)
        assertEquals("ch_5", currentProgress?.chapterId)
        assertEquals(500, currentProgress?.position)
    }

    @Test
    fun testReadingProgressLwwOlderRejected() = runBlocking {
        val profile = UserProfileEntity(id = "p1", username = "alice", displayName = "Alice", passwordHash = "h")
        userProfileDao.insertProfile(profile)

        val novel = NovelEntity(id = "n1", title = "Novel 1", author = "Author", description = "", coverUrl = null, status = "ongoing")
        novelDao.insertNovel(novel)

        // Local progress is chapter 10 updated at timestamp 5000
        readingProgressDao.saveProgress(
            ReadingProgressEntity(profileId = "p1", novelId = "n1", chapterId = "ch_10", position = 900, progressPercent = 0.9f, updatedAt = 5000L)
        )

        // Remote progress is chapter 3 updated at timestamp 2000 (stale / older)
        val incomingPayload = SyncPayload(
            senderDeviceId = "dev_peer",
            senderDeviceName = "Peer Device",
            profile = SyncProfileData(username = "alice", displayName = "Alice"),
            novels = listOf(
                SyncNovelItem(
                    novelId = "n1",
                    title = "Novel 1",
                    author = "Author",
                    description = "",
                    readingProgress = SyncReadingProgressItem(chapterId = "ch_3", chapterNumber = 3, position = 100, progressPercent = 0.1f, updatedAt = 2000L)
                )
            )
        )

        engine.mergePayload(incomingPayload, destinationProfileId = "p1")

        // Stale progress must be rejected; local progress remains intact
        val currentProgress = readingProgressDao.getProgressDirect("p1", "n1")
        assertEquals(5000L, currentProgress?.updatedAt)
        assertEquals("ch_10", currentProgress?.chapterId)
    }

    @Test
    fun testSafeProfileImportNeverLeaksPassword() = runBlocking {
        val incomingPayload = SyncPayload(
            senderDeviceId = "dev_peer",
            senderDeviceName = "Peer Device",
            profile = SyncProfileData(
                username = "bob",
                displayName = "Bob",
                isPasswordProtected = true
            ),
            novels = emptyList()
        )

        val result = engine.mergePayload(incomingPayload)
        assertTrue(result.success)
        assertTrue(result.profileImported)

        val importedProfile = userProfileDao.getProfileByUsername("bob")
        assertTrue(importedProfile != null)
        assertEquals("bob", importedProfile?.username)
        // Passwords must NEVER be transmitted or pre-filled over the wire
        assertEquals(null, importedProfile?.passwordHash)
        assertTrue(importedProfile?.passwordEnabled == true)
    }

    @Test
    fun testNovelTagsSetUnionMerging() = runBlocking {
        val profile = UserProfileEntity(id = "p1", username = "alice", displayName = "Alice", passwordHash = "h")
        userProfileDao.insertProfile(profile)

        val novel = NovelEntity(id = "n_union", title = "Union Novel", author = "Author", description = "", coverUrl = null, status = "ongoing")
        novelDao.insertNovel(novel)

        // Seed local tags: Fantasy, Progression
        val tagFantasy = TagEntity(id = "fantasy", name = "Fantasy", isWarning = false)
        val tagProgression = TagEntity(id = "progression", name = "Progression", isWarning = false)
        novelDao.insertTags(listOf(tagFantasy, tagProgression))
        novelDao.insertNovelTagCrossRefs(
            listOf(
                NovelTagCrossRef(novelId = "n_union", tagId = "fantasy"),
                NovelTagCrossRef(novelId = "n_union", tagId = "progression")
            )
        )

        // Remote payload has: Progression, LitRPG, Graphic Violence (warning)
        val incomingPayload = SyncPayload(
            senderDeviceId = "dev_peer",
            senderDeviceName = "Peer Device",
            profile = SyncProfileData(username = "alice", displayName = "Alice"),
            novels = listOf(
                SyncNovelItem(
                    novelId = "n_union",
                    title = "Union Novel",
                    author = "Author",
                    description = "",
                    tags = listOf(
                        SyncTagItem(id = "progression", name = "Progression", isWarning = false),
                        SyncTagItem(id = "litrpg", name = "LitRPG", isWarning = false),
                        SyncTagItem(id = "graphic_violence", name = "Graphic Violence", isWarning = true)
                    )
                )
            )
        )

        val result = engine.mergePayload(incomingPayload, destinationProfileId = "p1")
        assertTrue(result.success)

        // Merged tags must be Set-Union of local and remote tags: Fantasy, Progression, LitRPG, Graphic Violence
        val mergedTags = novelDao.getTagsForNovel("n_union")
        val mergedTagIds = mergedTags.map { it.id }.toSet()
        assertEquals(setOf("fantasy", "progression", "litrpg", "graphic_violence"), mergedTagIds)
        val warningTag = mergedTags.firstOrNull { it.id == "graphic_violence" }
        assertTrue(warningTag != null)
        assertTrue(warningTag?.isWarning == true)
    }
}

// In-Memory Fake DAOs for fast and reliable JVM testing
class FakeUserProfileDao : UserProfileDao {
    private val profiles = mutableMapOf<String, UserProfileEntity>()

    override fun getAllProfiles(): Flow<List<UserProfileEntity>> = flowOf(profiles.values.toList())
    override suspend fun getProfileById(id: String): UserProfileEntity? = profiles[id]
    override suspend fun getProfileByUsername(username: String): UserProfileEntity? = profiles.values.firstOrNull { it.username == username }
    override suspend fun getProfileCount(): Int = profiles.size
    override suspend fun insertProfile(profile: UserProfileEntity) { profiles[profile.id] = profile }
    override suspend fun updateProfile(profile: UserProfileEntity) { profiles[profile.id] = profile }
    override suspend fun deleteProfile(profile: UserProfileEntity) { profiles.remove(profile.id) }
    override suspend fun updateLastActive(profileId: String, timestamp: Long) {}
}

class FakeNovelDao : NovelDao {
    private val novels = mutableMapOf<String, NovelEntity>()
    private val sources = mutableListOf<SourceEntity>()
    private val entries = mutableListOf<LibraryEntryEntity>()
    private val tags = mutableMapOf<String, TagEntity>()
    private val novelTags = mutableListOf<NovelTagCrossRef>()

    override fun getNovelsForProfile(profileId: String): Flow<List<NovelWithEntry>> = flowOf(emptyList())

    override fun getNovelsForProfileExcludingTags(
        profileId: String,
        excludedTagIds: List<String>,
        excludedTagNamesLower: List<String>
    ): Flow<List<NovelWithEntry>> = flowOf(emptyList())

    override suspend fun getNovelWithTags(novelId: String): NovelWithTags? {
        val novel = novels[novelId] ?: return null
        val tagIds = novelTags.filter { it.novelId == novelId }.map { it.tagId }.toSet()
        val tagList = tags.values.filter { it.id in tagIds }
        return NovelWithTags(novel, tagList)
    }

    override fun getNovelWithTagsFlow(novelId: String): Flow<NovelWithTags?> {
        val novel = novels[novelId] ?: return flowOf(null)
        val tagIds = novelTags.filter { it.novelId == novelId }.map { it.tagId }.toSet()
        val tagList = tags.values.filter { it.id in tagIds }
        return flowOf(NovelWithTags(novel, tagList))
    }

    override fun getAllNovelsWithTags(): Flow<List<NovelWithTags>> {
        val list = novels.values.map { novel ->
            val tagIds = novelTags.filter { it.novelId == novel.id }.map { it.tagId }.toSet()
            val tagList = tags.values.filter { it.id in tagIds }
            NovelWithTags(novel, tagList)
        }
        return flowOf(list)
    }

    override fun getNovelsExcludingTags(
        excludedTagIds: List<String>,
        excludedTagNamesLower: List<String>
    ): Flow<List<NovelWithTags>> {
        val list = novels.values.mapNotNull { novel ->
            val tagIds = novelTags.filter { it.novelId == novel.id }.map { it.tagId }.toSet()
            val tagList = tags.values.filter { it.id in tagIds }
            val excluded = tagList.any { it.id in excludedTagIds || it.name.lowercase() in excludedTagNamesLower }
            if (excluded) null else NovelWithTags(novel, tagList)
        }
        return flowOf(list)
    }

    override suspend fun insertTag(tag: TagEntity) {
        tags[tag.id] = tag
    }

    override suspend fun insertTags(tags: List<TagEntity>) {
        tags.forEach { this.tags[it.id] = it }
    }

    override suspend fun insertNovelTagCrossRef(crossRef: NovelTagCrossRef) {
        if (!novelTags.any { it.novelId == crossRef.novelId && it.tagId == crossRef.tagId }) {
            novelTags.add(crossRef)
        }
    }

    override suspend fun insertNovelTagCrossRefs(crossRefs: List<NovelTagCrossRef>) {
        crossRefs.forEach { insertNovelTagCrossRef(it) }
    }

    override suspend fun getTagsForNovel(novelId: String): List<TagEntity> {
        val tagIds = novelTags.filter { it.novelId == novelId }.map { it.tagId }.toSet()
        return tags.values.filter { it.id in tagIds }
    }

    override fun getTagsForNovelFlow(novelId: String): Flow<List<TagEntity>> {
        val tagIds = novelTags.filter { it.novelId == novelId }.map { it.tagId }.toSet()
        return flowOf(tags.values.filter { it.id in tagIds })
    }

    override suspend fun getAllTags(): List<TagEntity> = tags.values.toList()

    override suspend fun deleteTagsForNovel(novelId: String) {
        novelTags.removeAll { it.novelId == novelId }
    }

    override suspend fun getNovelById(novelId: String): NovelEntity? = novels[novelId]
    override suspend fun insertNovel(novel: NovelEntity) { novels[novel.id] = novel }
    override suspend fun insertSource(source: SourceEntity) { sources.add(source) }
    override suspend fun getSourceByUrl(sourceUrl: String): SourceEntity? = sources.firstOrNull { it.sourceUrl == sourceUrl }
    override suspend fun getSourcesForNovel(novelId: String): List<SourceEntity> = sources.filter { it.novelId == novelId }
    override suspend fun insertLibraryEntry(entry: LibraryEntryEntity) {
        entries.removeAll { it.profileId == entry.profileId && it.novelId == entry.novelId }
        entries.add(entry)
    }
    override suspend fun removeNovelFromLibrary(profileId: String, novelId: String) {
        entries.removeAll { it.profileId == profileId && it.novelId == novelId }
    }
    override suspend fun updateNovel(novel: NovelEntity) { novels[novel.id] = novel }
    override suspend fun updateSource(source: SourceEntity) {
        sources.removeAll { it.id == source.id }
        sources.add(source)
    }
    override suspend fun updateSourceCheckTime(sourceId: String, lastCheckedAt: Long, lastSuccessfulCheckAt: Long) {
        val idx = sources.indexOfFirst { it.id == sourceId }
        if (idx >= 0) {
            sources[idx] = sources[idx].copy(lastCheckedAt = lastCheckedAt, lastSuccessfulCheckAt = lastSuccessfulCheckAt)
        }
    }
    override suspend fun getLibraryEntryCountForNovel(novelId: String): Int =
        entries.count { it.novelId == novelId }
    override suspend fun deleteNovel(novelId: String) { novels.remove(novelId) }
    override suspend fun isNovelInLibrary(profileId: String, novelId: String): Int =
        if (entries.any { it.profileId == profileId && it.novelId == novelId }) 1 else 0
    override suspend fun getLibraryEntry(profileId: String, novelId: String): LibraryEntryEntity? =
        entries.firstOrNull { it.profileId == profileId && it.novelId == novelId }
    override suspend fun updateDownloadPolicy(profileId: String, novelId: String, mode: String, limit: Int, autoDownload: Boolean) {}
    override suspend fun updateLastOpened(profileId: String, novelId: String, timestamp: Long) {}
    override suspend fun getAllNovels(): List<NovelEntity> = novels.values.toList()
    override suspend fun getAllLibraryEntries(): List<LibraryEntryEntity> = entries.toList()
    override suspend fun getLibraryEntriesForNovel(novelId: String): List<LibraryEntryEntity> = entries.filter { it.novelId == novelId }
    override suspend fun updateNotificationPreference(profileId: String, novelId: String, enabled: Boolean) {}
}

class FakeChapterDao : ChapterDao {
    private val chapters = mutableMapOf<String, ChapterEntity>()

    override fun getChaptersForNovel(novelId: String): Flow<List<ChapterEntity>> = flowOf(chapters.values.filter { it.novelId == novelId })
    override suspend fun getChaptersListForNovel(novelId: String): List<ChapterEntity> = chapters.values.filter { it.novelId == novelId }
    override suspend fun getChapterById(chapterId: String): ChapterEntity? = chapters[chapterId]
    override suspend fun getChapterByUrl(sourceUrl: String): ChapterEntity? = chapters.values.firstOrNull { it.sourceUrl == sourceUrl }
    override suspend fun insertChapters(chapters: List<ChapterEntity>) { chapters.forEach { this.chapters[it.id] = it } }
    override suspend fun insertChapter(chapter: ChapterEntity) { chapters[chapter.id] = chapter }
    override suspend fun updateChapter(chapter: ChapterEntity) { chapters[chapter.id] = chapter }
    override suspend fun updateChapters(chapters: List<ChapterEntity>) { chapters.forEach { this.chapters[it.id] = it } }
    override suspend fun updateDownloadState(chapterId: String, state: String, filePath: String?, contentHash: String?, downloadedAt: Long?, updatedAt: Long) {}
    override suspend fun updateRetentionPolicy(chapterId: String, policy: String) {}
    override suspend fun getQueuedChapters(): List<ChapterEntity> = emptyList()
    override suspend fun getCachedChaptersSortedByDownloadedAt(): List<ChapterEntity> = emptyList()
    override suspend fun getChaptersAfterNumber(novelId: String, chapterNumber: Int, limit: Int): List<ChapterEntity> = emptyList()
    override suspend fun getLatestChapters(novelId: String, limit: Int): List<ChapterEntity> = emptyList()
    override suspend fun getChapterCountForNovel(novelId: String): Int = chapters.size
    override suspend fun getDownloadedChapterCount(novelId: String): Int = 0
}

class FakeReadingProgressDao : ReadingProgressDao {
    private val progressMap = mutableMapOf<String, ReadingProgressEntity>()

    override fun getProgress(profileId: String, novelId: String): Flow<ReadingProgressEntity?> = flowOf(progressMap["$profileId:$novelId"])
    override suspend fun getProgressDirect(profileId: String, novelId: String): ReadingProgressEntity? = progressMap["$profileId:$novelId"]
    override suspend fun saveProgress(progress: ReadingProgressEntity) {
        progressMap["${progress.profileId}:${progress.novelId}"] = progress
    }
    override suspend fun deleteProgress(profileId: String, novelId: String) {
        progressMap.remove("$profileId:$novelId")
    }
}

class FakeReaderPreferencesDao : ReaderPreferencesDao {
    private val prefsMap = mutableMapOf<String, ReaderPreferencesEntity>()

    override fun getPreferences(profileId: String): Flow<ReaderPreferencesEntity?> = flowOf(prefsMap[profileId])
    override suspend fun getPreferencesDirect(profileId: String): ReaderPreferencesEntity? = prefsMap[profileId]
    override suspend fun savePreferences(preferences: ReaderPreferencesEntity) {
        prefsMap[preferences.profileId] = preferences
    }
}
