package com.drnishanth.novellib.ui.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.database.entities.LibraryEntryEntity
import com.drnishanth.novellib.core.database.entities.NovelEntity
import com.drnishanth.novellib.core.database.entities.ReadingProgressEntity
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import com.drnishanth.novellib.downloads.DownloadManager
import com.drnishanth.novellib.downloads.models.ChapterDownloadStatus
import com.drnishanth.novellib.downloads.models.HybridPolicyOption
import com.drnishanth.novellib.downloads.models.RetentionPolicy
import com.drnishanth.novellib.downloads.models.StoragePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NovelDetailUiState(
    val showStoragePolicyDialog: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val message: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NovelDetailViewModel(
    private val novelId: String,
    private val novelRepository: NovelRepository = NovelLibApplication.instance.novelRepository,
    private val profileRepository: ProfileRepository = NovelLibApplication.instance.profileRepository,
    private val downloadManager: DownloadManager = NovelLibApplication.instance.downloadManager
) : ViewModel() {

    private val _novel = MutableStateFlow<NovelEntity?>(null)
    val novel: StateFlow<NovelEntity?> = _novel.asStateFlow()

    private val _libraryEntry = MutableStateFlow<LibraryEntryEntity?>(null)
    val libraryEntry: StateFlow<LibraryEntryEntity?> = _libraryEntry.asStateFlow()

    private val _uiState = MutableStateFlow(NovelDetailUiState())
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending.asStateFlow()

    val chapters: StateFlow<List<ChapterEntity>> = novelRepository.getChapters(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readingProgress: StateFlow<ReadingProgressEntity?> = profileRepository.activeProfile.flatMapLatest { profile ->
        if (profile != null) {
            novelRepository.getReadingProgress(profile.id, novelId)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val readChapterIds: StateFlow<Set<String>> = profileRepository.activeProfile.flatMapLatest { profile ->
        if (profile != null) {
            novelRepository.getReadChapterIds(profile.id, novelId).map { it.toSet() }
        } else {
            flowOf(emptySet())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadStatuses: StateFlow<Map<String, ChapterDownloadStatus>> = downloadManager.downloadStatuses

    fun toggleSortOrder() {
        _isAscending.value = !_isAscending.value
    }

    fun toggleChapterRead(chapterId: String, currentRead: Boolean) {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            if (currentRead) {
                novelRepository.markChapterUnread(profileId, chapterId)
            } else {
                novelRepository.markChapterRead(profileId, novelId, chapterId)
            }
        }
    }

    init {
        viewModelScope.launch {
            _novel.value = novelRepository.getNovel(novelId)
            loadLibraryEntry()
        }
    }

    private suspend fun loadLibraryEntry() {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        _libraryEntry.value = novelRepository.getLibraryEntry(profileId, novelId)
    }

    fun showStoragePolicyDialog() {
        _uiState.value = _uiState.value.copy(showStoragePolicyDialog = true)
    }

    fun dismissStoragePolicyDialog() {
        _uiState.value = _uiState.value.copy(showStoragePolicyDialog = false)
    }

    fun updateStoragePolicy(mode: String, limit: Int, autoDownload: Boolean) {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            novelRepository.updateDownloadPolicy(profileId, novelId, mode, limit, autoDownload)
            loadLibraryEntry()
            _uiState.value = _uiState.value.copy(
                showStoragePolicyDialog = false,
                message = "Storage mode updated to ${mode.replaceFirstChar { it.uppercase() }}"
            )
        }
    }

    fun downloadChapter(chapterId: String) {
        downloadManager.enqueueChapter(chapterId, RetentionPolicy.OFFLINE)
    }

    fun deleteChapterDownload(chapterId: String) {
        viewModelScope.launch {
            val deleted = downloadManager.deleteDownloadedChapter(chapterId)
            if (deleted) {
                _uiState.value = _uiState.value.copy(message = "Deleted downloaded chapter content")
            }
        }
    }

    fun downloadAllChapters() {
        viewModelScope.launch {
            val toDownload = chapters.value.filter { it.downloadState != "available" }.map { it.id }
            if (toDownload.isNotEmpty()) {
                downloadManager.enqueueChapters(toDownload, RetentionPolicy.OFFLINE)
                _uiState.value = _uiState.value.copy(message = "Queued ${toDownload.size} chapter(s) for download")
            }
        }
    }

    fun deleteNovelDownloads() {
        viewModelScope.launch {
            val deletedCount = downloadManager.deleteDownloadedNovel(novelId)
            _uiState.value = _uiState.value.copy(message = "Removed $deletedCount downloaded chapter(s)")
        }
    }

    fun toggleNotifications() {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        val current = _libraryEntry.value?.notificationsEnabled ?: true
        val newSetting = !current
        viewModelScope.launch {
            novelRepository.updateNotificationPreference(profileId, novelId, newSetting)
            loadLibraryEntry()
            _uiState.value = _uiState.value.copy(
                message = if (newSetting) "Notifications enabled for this novel" else "Notifications disabled for this novel"
            )
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdates = true)
            val result = novelRepository.checkNovelUpdates(novelId)
            _uiState.value = _uiState.value.copy(isCheckingUpdates = false)
            result.onSuccess { newChapters ->
                if (newChapters.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        message = "Found ${newChapters.size} new chapter(s)!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        message = "Novel is up to date"
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    message = "Update check failed: ${err.message}"
                )
            }
        }
    }

    fun removeNovelFromLibrary(deleteDownloads: Boolean = false, onRemoved: () -> Unit) {
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            if (deleteDownloads) {
                downloadManager.deleteDownloadedNovel(novelId)
            }
            novelRepository.removeFromLibrary(profileId, novelId, deleteDownloads)
            onRemoved()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
