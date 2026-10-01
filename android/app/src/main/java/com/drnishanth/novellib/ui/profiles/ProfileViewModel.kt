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
    val selectedProfileForEdit: UserProfileEntity? = null,
    val isCreatingProfile: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
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
        _uiState.value = _uiState.value.copy(isCreatingProfile = true, errorMessage = null, successMessage = null)
    }

    fun dismissCreateProfileDialog() {
        _uiState.value = _uiState.value.copy(isCreatingProfile = false, errorMessage = null)
    }

    fun showUnlockDialog(profile: UserProfileEntity) {
        _uiState.value = _uiState.value.copy(selectedProfileForUnlock = profile, errorMessage = null, successMessage = null)
    }

    fun dismissUnlockDialog() {
        _uiState.value = _uiState.value.copy(selectedProfileForUnlock = null, errorMessage = null)
    }

    fun showEditProfileDialog(profile: UserProfileEntity) {
        _uiState.value = _uiState.value.copy(selectedProfileForEdit = profile, errorMessage = null, successMessage = null)
    }

    fun dismissEditProfileDialog() {
        _uiState.value = _uiState.value.copy(selectedProfileForEdit = null, errorMessage = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun createProfile(username: String, displayName: String, password: String?) {
        viewModelScope.launch {
            val result = profileRepository.createProfile(username, displayName, password)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isCreatingProfile = false,
                    errorMessage = null,
                    successMessage = "Profile created successfully!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to create profile"
                )
            }
        }
    }

    fun updateProfile(
        profileId: String,
        newUsername: String,
        newDisplayName: String,
        currentPassword: String? = null,
        newPassword: String? = null,
        removePassword: Boolean = false
    ) {
        viewModelScope.launch {
            val result = profileRepository.updateProfile(
                profileId = profileId,
                newUsername = newUsername,
                newDisplayName = newDisplayName,
                currentPassword = currentPassword,
                newPassword = newPassword,
                removePassword = removePassword
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    selectedProfileForEdit = null,
                    errorMessage = null,
                    successMessage = "Profile updated successfully!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to update profile"
                )
            }
        }
    }

    fun deleteProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            val result = profileRepository.deleteProfile(profile)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    selectedProfileForEdit = null,
                    errorMessage = null,
                    successMessage = "Profile deleted successfully"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete profile"
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
