package com.drnishanth.novellib.core

import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.data.repository.AddictionStatus
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.sync.FakeChapterDao
import com.drnishanth.novellib.sync.FakeNovelDao
import com.drnishanth.novellib.sync.FakeReaderPreferencesDao
import com.drnishanth.novellib.sync.FakeReadingProgressDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddictionTimerTest {

    private lateinit var novelDao: FakeNovelDao
    private lateinit var chapterDao: FakeChapterDao
    private lateinit var readingProgressDao: FakeReadingProgressDao
    private lateinit var readerPreferencesDao: FakeReaderPreferencesDao
    private lateinit var repository: NovelRepository

    private val profileId = "profile-1"
    private val novelAId = "mother-of-learning"
    private val novelBId = "trash-of-counts-family"

    @Before
    fun setup() = runBlocking {
        novelDao = FakeNovelDao()
        chapterDao = FakeChapterDao()
        readingProgressDao = FakeReadingProgressDao()
        readerPreferencesDao = FakeReaderPreferencesDao()

        // Create novels
        novelDao.insertNovel(
            NovelEntity(
                id = novelAId,
                title = "Mother of Learning",
                author = "nobody103",
                description = "Time loop fantasy",
                coverUrl = null
            )
        )
        novelDao.insertNovel(
            NovelEntity(
                id = novelBId,
                title = "Trash of the Count's Family",
                author = "Yoo Ryeo Han",
                description = "Transmigration fantasy",
                coverUrl = null
            )
        )

        // Insert library entries (initially addictionLimit = 0)
        novelDao.insertLibraryEntry(
            LibraryEntryEntity(
                profileId = profileId,
                novelId = novelAId,
                addictionLimit = 0,
                sessionChaptersRead = 0,
                lockedUntil = 0L
            )
        )
        novelDao.insertLibraryEntry(
            LibraryEntryEntity(
                profileId = profileId,
                novelId = novelBId,
                addictionLimit = 0,
                sessionChaptersRead = 0,
                lockedUntil = 0L
            )
        )

        repository = NovelRepository(
            novelDao = novelDao,
            chapterDao = chapterDao,
            readingProgressDao = readingProgressDao,
            readerPreferencesDao = readerPreferencesDao
        )
    }

    @Test
    fun testAddictionTimerDisabledByDefault() = runBlocking {
        val status = repository.checkAddictionLock(profileId, novelAId)
        assertTrue("Should be inactive when limit is 0", status is AddictionStatus.Inactive)

        val readStatus = repository.recordChapterReadForAddiction(profileId, novelAId, "ch-1")
        assertTrue("Should remain inactive when limit is 0", readStatus is AddictionStatus.Inactive)
    }

    @Test
    fun testAddictionTimerEnforcesLimitAndLocksForOneHour() = runBlocking {
        // Set Mother of Learning limit = 2 chapters
        repository.setAddictionLimit(profileId, novelAId, limit = 2)

        // Verify limit is active
        val initialStatus = repository.checkAddictionLock(profileId, novelAId)
        assertTrue(initialStatus is AddictionStatus.Active)
        assertEquals(0, (initialStatus as AddictionStatus.Active).current)
        assertEquals(2, initialStatus.limit)

        // Read chapter 1: session count becomes 1, still unlocked
        val status1 = repository.recordChapterReadForAddiction(profileId, novelAId, "ch-1")
        assertTrue("Chapter 1 should leave novel active", status1 is AddictionStatus.Active)
        assertEquals(1, (status1 as AddictionStatus.Active).current)
        assertEquals(2, status1.limit)

        // Still unlocked
        val checkAfter1 = repository.checkAddictionLock(profileId, novelAId)
        assertTrue(checkAfter1 is AddictionStatus.Active)

        // Read chapter 2: limit reached (2/2) -> triggers 1-hour lockout!
        val beforeLockTime = System.currentTimeMillis()
        val status2 = repository.recordChapterReadForAddiction(profileId, novelAId, "ch-2")
        val afterLockTime = System.currentTimeMillis()

        assertTrue("Chapter 2 should trigger lockout", status2 is AddictionStatus.Locked)
        val locked = status2 as AddictionStatus.Locked
        assertEquals(2, locked.limit)

        // Verify lock is roughly 1 hour in future (3,600,000 ms)
        val expectedMinLockUntil = beforeLockTime + 3600_000L
        val expectedMaxLockUntil = afterLockTime + 3600_000L
        assertTrue(locked.lockedUntil in expectedMinLockUntil..expectedMaxLockUntil)

        // Subsequent check verifies locked state
        val checkAfter2 = repository.checkAddictionLock(profileId, novelAId)
        assertTrue("Novel should be reported as locked", checkAfter2 is AddictionStatus.Locked)
    }

    @Test
    fun testAddictionLockIsPerNovelOnly() = runBlocking {
        // Configure Novel A with limit 2
        repository.setAddictionLimit(profileId, novelAId, limit = 2)
        // Configure Novel B with limit 5
        repository.setAddictionLimit(profileId, novelBId, limit = 5)

        // Read 2 chapters in Novel A -> Novel A locks
        repository.recordChapterReadForAddiction(profileId, novelAId, "ch-1")
        val statusA = repository.recordChapterReadForAddiction(profileId, novelAId, "ch-2")
        assertTrue("Novel A must be locked", statusA is AddictionStatus.Locked)

        // Verify Novel B remains completely unlocked and active!
        val statusB = repository.checkAddictionLock(profileId, novelBId)
        assertTrue("Novel B must remain open and unlocked", statusB is AddictionStatus.Active)
        assertEquals(0, (statusB as AddictionStatus.Active).current)
        assertEquals(5, statusB.limit)

        // Novel B can be read normally
        val readB1 = repository.recordChapterReadForAddiction(profileId, novelBId, "b-ch-1")
        assertTrue("Novel B is actively readable", readB1 is AddictionStatus.Active)
        assertEquals(1, (readB1 as AddictionStatus.Active).current)

        // Novel A is still locked
        val checkA = repository.checkAddictionLock(profileId, novelAId)
        assertTrue("Novel A remains locked independently", checkA is AddictionStatus.Locked)
    }

    @Test
    fun testAddictionTimerUnlocksAfterExpiration() = runBlocking {
        repository.setAddictionLimit(profileId, novelAId, limit = 2)

        // Manually simulate an expired lock (locked 1 hour ago)
        val oneHourAgo = System.currentTimeMillis() - 1000L
        novelDao.updateAddictionSession(profileId, novelAId, chaptersRead = 2, lockedUntil = oneHourAgo)

        // Checking lock status should detect expiration, clear the lock and reset session count to 0
        val status = repository.checkAddictionLock(profileId, novelAId)
        assertTrue("Expired timer should auto-unlock", status is AddictionStatus.Active)
        assertEquals(0, (status as AddictionStatus.Active).current)
        assertEquals(2, (status as AddictionStatus.Active).limit)

        // Next reading starts fresh allowing 2 new chapters
        val nextRead1 = repository.recordChapterReadForAddiction(profileId, novelAId, "ch-3")
        assertTrue(nextRead1 is AddictionStatus.Active)
        assertEquals(1, (nextRead1 as AddictionStatus.Active).current)
    }

    @Test
    fun testManualResetTimer() = runBlocking {
        repository.setAddictionLimit(profileId, novelAId, limit = 2)
        // Lock novel A
        repository.recordChapterReadForAddiction(profileId, novelAId, "ch-1")
        repository.recordChapterReadForAddiction(profileId, novelAId, "ch-2")

        assertTrue(repository.checkAddictionLock(profileId, novelAId) is AddictionStatus.Locked)

        // User manually resets / unlocks
        repository.resetAddictionTimer(profileId, novelAId)

        val status = repository.checkAddictionLock(profileId, novelAId)
        assertTrue("Novel should be unlocked after manual reset", status is AddictionStatus.Active)
        assertEquals(0, (status as AddictionStatus.Active).current)
    }
}
