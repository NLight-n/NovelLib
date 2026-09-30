package com.drnishanth.novellib.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

data class ReaderUiState(
    val isLoading: Boolean = true,
    val currentChapter: ChapterEntity? = null,
    val previousChapterId: String? = null,
    val nextChapterId: String? = null,
    val paragraphs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val showControls: Boolean = false,
    val preferences: ReaderPreferencesEntity = ReaderPreferencesEntity(profileId = "")
)

class ReaderViewModel(
    val novelId: String,
    initialChapterId: String,
    private val novelRepository: NovelRepository = NovelLibApplication.instance.novelRepository,
    private val profileRepository: ProfileRepository = NovelLibApplication.instance.profileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var allChapters: List<ChapterEntity> = emptyList()

    init {
        loadChapter(initialChapterId)
        loadPreferences()
    }

    private fun loadPreferences() {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            val prefs = novelRepository.getReaderPreferences(profileId).firstOrNull()
            if (prefs != null) {
                _uiState.value = _uiState.value.copy(preferences = prefs)
            }
        }
    }

    fun loadChapter(chapterId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Cache chapter list for next/prev navigation
            if (allChapters.isEmpty()) {
                allChapters = novelRepository.getChapters(novelId).firstOrNull() ?: emptyList()
            }

            val currentIndex = allChapters.indexOfFirst { it.id == chapterId }
            val currentChapter = if (currentIndex >= 0) allChapters[currentIndex] else novelRepository.getChapter(chapterId)
            val prevId = if (currentIndex > 0) allChapters[currentIndex - 1].id else null
            val nextId = if (currentIndex in 0 until allChapters.size - 1) allChapters[currentIndex + 1].id else null

            val contentResult = novelRepository.loadChapterContent(chapterId)
            if (contentResult.isSuccess) {
                val html = contentResult.getOrNull() ?: ""
                val parsedParagraphs = parseHtmlToParagraphs(html)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentChapter = currentChapter,
                    previousChapterId = prevId,
                    nextChapterId = nextId,
                    paragraphs = parsedParagraphs
                )

                // Save reading progress
                val profileId = profileRepository.activeProfile.value?.id
                if (profileId != null && currentChapter != null) {
                    val percent = if (allChapters.isNotEmpty()) {
                        (currentIndex + 1).toFloat() / allChapters.size.toFloat()
                    } else 0f
                    novelRepository.saveReadingProgress(
                        profileId = profileId,
                        novelId = novelId,
                        chapterId = chapterId,
                        position = 0,
                        progressPercent = percent
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentChapter = currentChapter,
                    previousChapterId = prevId,
                    nextChapterId = nextId,
                    errorMessage = contentResult.exceptionOrNull()?.message ?: "Failed to load chapter content"
                )
            }
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
        val newSize = (_uiState.value.preferences.fontSize + delta).coerceIn(12f, 32f)
        val updated = _uiState.value.preferences.copy(fontSize = newSize)
        _uiState.value = _uiState.value.copy(preferences = updated)
        savePrefs(updated)
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
}
