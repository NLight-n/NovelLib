package com.drnishanth.novellib.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.entities.UserProfileEntity
import com.drnishanth.novellib.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val selectedProfileForUnlock: UserProfileEntity? = null,
    val isCreatingProfile: Boolean = false,
    val errorMessage: String? = null,
    val isVerifying: Boolean = false
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository = NovelLibApplication.instance.profileRepository
) : ViewModel() {

    val profiles: StateFlow<List<UserProfileEntity>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<UserProfileEntity?> = profileRepository.activeProfile

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun showCreateProfileDialog() {
        _uiState.value = _uiState.value.copy(isCreatingProfile = true, errorMessage = null)
    }

    fun dismissCreateProfileDialog() {
        _uiState.value = _uiState.value.copy(isCreatingProfile = false, errorMessage = null)
    }

    fun showUnlockDialog(profile: UserProfileEntity) {
        _uiState.value = _uiState.value.copy(selectedProfileForUnlock = profile, errorMessage = null)
    }

    fun dismissUnlockDialog() {
        _uiState.value = _uiState.value.copy(selectedProfileForUnlock = null, errorMessage = null)
    }

    fun createProfile(username: String, displayName: String, password: String?) {
        viewModelScope.launch {
            val result = profileRepository.createProfile(username, displayName, password)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isCreatingProfile = false, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to create profile"
                )
            }
        }
    }

    fun selectOrUnlockProfile(profile: UserProfileEntity, password: String? = null, onUnlocked: () -> Unit) {
        viewModelScope.launch {
            if (!profile.passwordEnabled) {
                profileRepository.selectProfile(profile)
                onUnlocked()
            } else {
                if (password.isNullOrBlank()) {
                    showUnlockDialog(profile)
                } else {
                    _uiState.value = _uiState.value.copy(isVerifying = true)
                    val success = profileRepository.unlockProfile(profile, password)
                    _uiState.value = _uiState.value.copy(isVerifying = false)
                    if (success) {
                        dismissUnlockDialog()
                        onUnlocked()
                    } else {
                        _uiState.value = _uiState.value.copy(errorMessage = "Incorrect password")
                    }
                }
            }
        }
    }
}
