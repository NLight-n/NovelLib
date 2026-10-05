package com.drnishanth.novellib.ui.reader

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.core.eink.BatteryDiagnostics
import com.drnishanth.novellib.core.eink.EInkHardwareManager
import com.drnishanth.novellib.core.eink.ReaderPagingEngine
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import com.drnishanth.novellib.downloads.DownloadManager
import com.drnishanth.novellib.downloads.models.RetentionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.drnishanth.novellib.scraping.models.ChapterExtractionException
import com.drnishanth.novellib.scraping.models.ExtractionFailureReason
import com.drnishanth.novellib.data.repository.AddictionStatus
import org.jsoup.Jsoup

data class ReaderUiState(
    val isLoading: Boolean = true,
    val currentChapter: ChapterEntity? = null,
    val previousChapterId: String? = null,
    val nextChapterId: String? = null,
    val paragraphs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val failureReason: ExtractionFailureReason? = null,
    val showControls: Boolean = false,
    val initialScrollIndex: Int = 0,
    val preferences: ReaderPreferencesEntity = ReaderPreferencesEntity(profileId = ""),
    // E-Ink and Paging extensions
    val isEInkDevice: Boolean = false,
    val pages: List<ReaderPagingEngine.ReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val isPageRefreshFlashing: Boolean = false,
    val batteryStatus: BatteryDiagnostics.BatteryStatus? = null,
    // De-addiction timer state
    val isAddictionLocked: Boolean = false,
    val lockedUntil: Long = 0L,
    val addictionLimit: Int = 0,
    val novelTitle: String = ""
)

class ReaderViewModel(
    val novelId: String,
    private val initialChapterId: String,
    private val novelRepository: NovelRepository = NovelLibApplication.instance.novelRepository,
    private val profileRepository: ProfileRepository = NovelLibApplication.instance.profileRepository,
    private val downloadManager: DownloadManager = NovelLibApplication.instance.downloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            isEInkDevice = EInkHardwareManager.isEInkDevice()
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var allChapters: List<ChapterEntity> = emptyList()
    private var pageTurnCount = 0
    private val sessionReadChapterIds = mutableSetOf<String>()

    init {
        loadBatteryDiagnostics()
        loadPreferences()
        loadChapter(initialChapterId)
    }

    private fun loadBatteryDiagnostics() {
        try {
            val status = BatteryDiagnostics.getBatteryStatus(NovelLibApplication.instance)
            _uiState.value = _uiState.value.copy(batteryStatus = status)
        } catch (_: Throwable) {
            // Ignore in headless unit test environments
        }
    }

    private fun loadPreferences() {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            val prefs = novelRepository.getReaderPreferences(profileId).firstOrNull()
            if (prefs != null) {
                // If running on an E-Ink device and user hasn't explicitly customized, default to eink theme
                val effectivePrefs = if (_uiState.value.isEInkDevice && prefs.theme == "light") {
                    prefs.copy(theme = "eink", pageNavigationMode = "paging", animationEnabled = false)
                } else {
                    prefs
                }
                _uiState.value = _uiState.value.copy(preferences = effectivePrefs)
                recomputePages()
            }
        }
    }

    fun loadChapter(chapterId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Check if novel is addiction-locked
            val profileId = profileRepository.activeProfile.value?.id
            if (profileId != null) {
                val lockStatus = novelRepository.checkAddictionLock(profileId, novelId)
                if (lockStatus is AddictionStatus.Locked) {
                    val novel = novelRepository.getNovel(novelId)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAddictionLocked = true,
                        lockedUntil = lockStatus.lockedUntil,
                        addictionLimit = lockStatus.limit,
                        novelTitle = novel?.title ?: "Novel"
                    )
                    return@launch
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAddictionLocked = false,
                        lockedUntil = 0L,
                        addictionLimit = if (lockStatus is AddictionStatus.Active) lockStatus.limit else 0
                    )
                }
            }

            // Cache chapter list for next/prev navigation
            if (allChapters.isEmpty()) {
                allChapters = novelRepository.getChapters(novelId).firstOrNull() ?: emptyList()
            }

            val currentIndex = allChapters.indexOfFirst { it.id == chapterId }
            val currentChapter = if (currentIndex >= 0) allChapters[currentIndex] else novelRepository.getChapter(chapterId)
            val prevId = if (currentIndex > 0) allChapters[currentIndex - 1].id else null
            val nextId = if (currentIndex in 0 until allChapters.size - 1) allChapters[currentIndex + 1].id else null

            // Check if there is saved reading progress for this chapter
            var savedPos = 0
            if (profileId != null) {
                val progress = novelRepository.getReadingProgress(profileId, novelId).firstOrNull()
                if (progress != null && progress.chapterId == chapterId) {
                    savedPos = progress.position
                }
            }

            val contentResult = novelRepository.loadChapterContent(chapterId)
            if (contentResult.isSuccess) {
                val html = contentResult.getOrNull() ?: ""
                val parsedParagraphs = parseHtmlToParagraphs(html)

                val prefs = _uiState.value.preferences
                val targetChars = ReaderPagingEngine.calculateTargetCharsPerPage(
                    fontSize = prefs.fontSize,
                    lineHeight = prefs.lineHeight,
                    margins = prefs.margins
                )
                val generatedPages = ReaderPagingEngine.paginate(parsedParagraphs, targetChars)
                val targetPageIndex = ReaderPagingEngine.pageIndexForScroll(savedPos, generatedPages)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentChapter = currentChapter,
                    previousChapterId = prevId,
                    nextChapterId = nextId,
                    paragraphs = parsedParagraphs,
                    pages = generatedPages,
                    currentPageIndex = targetPageIndex,
                    initialScrollIndex = savedPos,
                    errorMessage = null,
                    failureReason = null
                )

                // Save/update progress to current chapter
                if (profileId != null && currentChapter != null) {
                    val percent = if (allChapters.isNotEmpty()) {
                        (currentIndex + 1).toFloat() / allChapters.size.toFloat()
                    } else 0f
                    novelRepository.saveReadingProgress(
                        profileId = profileId,
                        novelId = novelId,
                        chapterId = chapterId,
                        position = savedPos,
                        progressPercent = percent
                    )
                    novelRepository.markChapterRead(
                        profileId = profileId,
                        novelId = novelId,
                        chapterId = chapterId
                    )

                    // Track session reading progress for addiction control
                    if (sessionReadChapterIds.add(chapterId)) {
                        val addictionStatus = novelRepository.recordChapterReadForAddiction(
                            profileId = profileId,
                            novelId = novelId,
                            chapterId = chapterId
                        )
                        if (addictionStatus is AddictionStatus.Locked) {
                            val novel = novelRepository.getNovel(novelId)
                            _uiState.value = _uiState.value.copy(
                                lockedUntil = addictionStatus.lockedUntil,
                                addictionLimit = addictionStatus.limit,
                                novelTitle = novel?.title ?: "Novel"
                            )
                        }
                    }
                }
            } else {
                val ex = contentResult.exceptionOrNull()
                val reason = (ex as? ChapterExtractionException)?.reason
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentChapter = currentChapter,
                    previousChapterId = prevId,
                    nextChapterId = nextId,
                    errorMessage = ex?.message ?: "Failed to load chapter content",
                    failureReason = reason
                )
            }
        }
    }

    /**
     * Recomputes discrete pages when typography or margins change while preserving current position.
     */
    private fun recomputePages() {
        val paragraphs = _uiState.value.paragraphs
        if (paragraphs.isEmpty()) return

        val prefs = _uiState.value.preferences
        val targetChars = ReaderPagingEngine.calculateTargetCharsPerPage(
            fontSize = prefs.fontSize,
            lineHeight = prefs.lineHeight,
            margins = prefs.margins
        )

        val currentParagraphIndex = if (_uiState.value.pages.isNotEmpty()) {
            ReaderPagingEngine.scrollIndexForPage(_uiState.value.currentPageIndex, _uiState.value.pages)
        } else {
            _uiState.value.initialScrollIndex
        }

        val newPages = ReaderPagingEngine.paginate(paragraphs, targetChars)
        val newPageIndex = ReaderPagingEngine.pageIndexForScroll(currentParagraphIndex, newPages)

        _uiState.value = _uiState.value.copy(
            pages = newPages,
            currentPageIndex = newPageIndex
        )
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.currentPageIndex < state.pages.size - 1) {
            val newIndex = state.currentPageIndex + 1
            _uiState.value = state.copy(currentPageIndex = newIndex)
            onPageTurned(newIndex)
        } else if (state.nextChapterId != null) {
            loadChapter(state.nextChapterId)
        }
    }

    fun previousPage() {
        val state = _uiState.value
        if (state.currentPageIndex > 0) {
            val newIndex = state.currentPageIndex - 1
            _uiState.value = state.copy(currentPageIndex = newIndex)
            onPageTurned(newIndex)
        } else if (state.previousChapterId != null) {
            loadChapter(state.previousChapterId)
        }
    }

    fun goToPage(pageIndex: Int) {
        val state = _uiState.value
        val clamped = pageIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))
        _uiState.value = state.copy(currentPageIndex = clamped)
        onPageTurned(clamped)
    }

    private fun onPageTurned(pageIndex: Int) {
        val state = _uiState.value
        val paragraphIndex = ReaderPagingEngine.scrollIndexForPage(pageIndex, state.pages)
        saveScrollIndex(paragraphIndex)

        // Check periodic E-Ink auto-refresh
        pageTurnCount++
        val interval = state.preferences.einkFullRefreshInterval
        if (interval > 0 && pageTurnCount >= interval) {
            triggerScreenRefresh()
        }
    }

    /**
     * Intercepts physical e-reader buttons (Volume keys, Page Up/Down, DPAD).
     */
    fun handleHardwareKeyEvent(keyCode: Int): Boolean {
        if (!_uiState.value.preferences.volumeKeysNavigation) return false

        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                nextPage()
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                previousPage()
                true
            }
            else -> false
        }
    }

    /**
     * Triggers EPDC hardware refresh and brief electrophoretic clear flash.
     */
    fun triggerScreenRefresh(context: Context? = null, view: View? = null) {
        pageTurnCount = 0
        try {
            val appCtx = context ?: NovelLibApplication.instance
            EInkHardwareManager.triggerScreenRefresh(appCtx, view)
        } catch (_: Throwable) {
            // Safe in test environments
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPageRefreshFlashing = true)
            delay(70) // Flash duration to wipe residual electrophoretic pigment
            _uiState.value = _uiState.value.copy(isPageRefreshFlashing = false)
        }
    }

    fun saveScrollIndex(index: Int) {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        val chapterId = _uiState.value.currentChapter?.id ?: return
        val totalParagraphs = _uiState.value.paragraphs.size
        val percent = if (totalParagraphs > 0) (index.toFloat() / totalParagraphs).coerceIn(0f, 1f) else 0f

        viewModelScope.launch {
            novelRepository.saveReadingProgress(
                profileId = profileId,
                novelId = novelId,
                chapterId = chapterId,
                position = index,
                progressPercent = percent
            )
        }
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    fun updateTheme(themeName: String) {
        val updated = _uiState.value.preferences.copy(theme = themeName)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
    }

    fun updateFontSize(delta: Float) {
        val newSize = (_uiState.value.preferences.fontSize + delta).coerceIn(12f, 36f)
        val updated = _uiState.value.preferences.copy(fontSize = newSize)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
        recomputePages()
    }

    fun updateFontFamily(family: String) {
        val updated = _uiState.value.preferences.copy(fontFamily = family)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
    }

    fun updateLineHeight(height: Float) {
        val updated = _uiState.value.preferences.copy(lineHeight = height)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
        recomputePages()
    }

    fun updateNavigationMode(mode: String) {
        val updated = _uiState.value.preferences.copy(pageNavigationMode = mode)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
    }

    fun updateRefreshInterval(interval: Int) {
        val updated = _uiState.value.preferences.copy(einkFullRefreshInterval = interval)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
    }

    fun updateVolumeKeysNavigation(enabled: Boolean) {
        val updated = _uiState.value.preferences.copy(volumeKeysNavigation = enabled)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
    }

    fun updateTextBrightness(brightness: Float) {
        val updated = _uiState.value.preferences.copy(textBrightness = brightness)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
    }

    fun downloadCurrentChapter() {
        val chapter = _uiState.value.currentChapter ?: return
        downloadManager.enqueueChapter(chapter.id, RetentionPolicy.OFFLINE)
    }

    fun deleteCurrentChapter() {
        val chapter = _uiState.value.currentChapter ?: return
        viewModelScope.launch {
            downloadManager.deleteDownloadedChapter(chapter.id)
            val updated = novelRepository.getChapter(chapter.id)
            _uiState.value = _uiState.value.copy(currentChapter = updated)
        }
    }

    private fun savePrefs(prefs: ReaderPreferencesEntity) {
        viewModelScope.launch {
            novelRepository.saveReaderPreferences(prefs)
        }
    }

    private fun parseHtmlToParagraphs(html: String): List<String> {
        val doc = Jsoup.parseBodyFragment(html)
        val pElements = doc.select("p, h1, h2, h3, blockquote")
        return if (pElements.isNotEmpty()) {
            pElements.mapNotNull { el ->
                val text = el.text().trim()
                if (text.isNotBlank()) text else null
            }
        } else {
            // Fallback for simple line break text
            html.split("<br>", "<br/>", "\n\n")
                .map { Jsoup.parse(it).text().trim() }
                .filter { it.isNotBlank() }
        }
    }

    fun onLockoutExpired() {
        val targetId = _uiState.value.currentChapter?.id ?: initialChapterId
        loadChapter(targetId)
    }
}
