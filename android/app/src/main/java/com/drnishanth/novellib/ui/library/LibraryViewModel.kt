package com.drnishanth.novellib.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.dao.NovelWithEntry
import com.drnishanth.novellib.core.database.entities.UserProfileEntity
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isImporting: Boolean = false,
    val isRefreshing: Boolean = false,
    val showAddDialog: Boolean = false,
    val importError: String? = null,
    val importSuccessMessage: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val novelRepository: NovelRepository = NovelLibApplication.instance.novelRepository,
    private val profileRepository: ProfileRepository = NovelLibApplication.instance.profileRepository
) : ViewModel() {

    val activeProfile: StateFlow<UserProfileEntity?> = profileRepository.activeProfile

    val novels: StateFlow<List<NovelWithEntry>> = activeProfile.flatMapLatest { profile ->
        if (profile != null) {
            novelRepository.getLibraryNovels(profile.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun showAddNovelDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, importError = null, importSuccessMessage = null)
    }

    fun dismissAddNovelDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, importError = null)
    }

    fun importNovel(url: String) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importError = null)
            val result = novelRepository.importNovelFromUrl(url.trim(), profile.id)
            if (result.isSuccess) {
                val novel = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    showAddDialog = false,
                    importSuccessMessage = "Added \"${novel?.title}\" to library!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importError = result.exceptionOrNull()?.message ?: "Failed to import novel"
                )
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(importSuccessMessage = null)
    }

    fun checkAllUpdates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val updates = novelRepository.checkAllNovelsUpdates()
            val totalNewChapters = updates.values.sumOf { it.size }
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                importSuccessMessage = if (totalNewChapters > 0) {
                    "Discovered $totalNewChapters new chapter(s) across library!"
                } else {
                    "Library is up to date"
                }
            )
        }
    }

    fun removeNovelFromLibrary(novelId: String, deleteDownloads: Boolean = false) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            novelRepository.removeFromLibrary(profile.id, novelId, deleteDownloads)
        }
    }

    fun switchProfile() {
        profileRepository.logout()
    }

    val showEditProfileDialog = MutableStateFlow(false)
    val editProfileError = MutableStateFlow<String?>(null)

    fun openEditProfile() {
        editProfileError.value = null
        showEditProfileDialog.value = true
    }

    fun closeEditProfile() {
        editProfileError.value = null
        showEditProfileDialog.value = false
    }

    fun updateProfile(
        newUsername: String,
        newDisplayName: String,
        currentPassword: String? = null,
        newPassword: String? = null,
        removePassword: Boolean = false
    ) {
        val current = activeProfile.value ?: return
        viewModelScope.launch {
            val result = profileRepository.updateProfile(
                profileId = current.id,
                newUsername = newUsername,
                newDisplayName = newDisplayName,
                currentPassword = currentPassword,
                newPassword = newPassword,
                removePassword = removePassword
            )
            if (result.isSuccess) {
                showEditProfileDialog.value = false
                editProfileError.value = null
                _uiState.value = _uiState.value.copy(importSuccessMessage = "Profile updated successfully!")
            } else {
                editProfileError.value = result.exceptionOrNull()?.message ?: "Failed to update profile"
            }
        }
    }
}
