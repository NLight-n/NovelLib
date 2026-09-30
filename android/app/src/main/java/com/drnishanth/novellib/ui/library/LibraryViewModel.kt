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

    fun switchProfile() {
        profileRepository.logout()
    }
}
