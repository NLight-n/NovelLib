package com.drnishanth.novellib.data.repository

import com.drnishanth.novellib.core.database.dao.ReaderPreferencesDao
import com.drnishanth.novellib.core.database.dao.UserProfileDao
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.core.database.entities.UserProfileEntity
import com.drnishanth.novellib.core.security.Argon2SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class ProfileRepository(
    private val userProfileDao: UserProfileDao,
    private val readerPreferencesDao: ReaderPreferencesDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _activeProfile = MutableStateFlow<UserProfileEntity?>(null)
    val activeProfile: StateFlow<UserProfileEntity?> = _activeProfile.asStateFlow()

    init {
        // Automatically activate the most recently active profile if unprotected, or wait for selection
        scope.launch {
            val profiles = userProfileDao.getAllProfiles().firstOrNull()
            if (!profiles.isNullOrEmpty() && _activeProfile.value == null) {
                val mostRecent = profiles.first()
                if (!mostRecent.passwordEnabled) {
                    _activeProfile.value = mostRecent
                }
            }
        }
    }

    fun getAllProfiles(): Flow<List<UserProfileEntity>> = userProfileDao.getAllProfiles()

    suspend fun getProfileCount(): Int = userProfileDao.getProfileCount()

    suspend fun createProfile(
        username: String,
        displayName: String,
        password: String? = null
    ): Result<UserProfileEntity> {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }

        val existing = userProfileDao.getProfileByUsername(trimmedUsername)
        if (existing != null) {
            return Result.failure(IllegalStateException("Username already taken"))
        }

        val hasPassword = !password.isNullOrBlank()
        val passwordHash = if (hasPassword) Argon2SecurityManager.hashPassword(password!!) else null

        val profile = UserProfileEntity(
            id = UUID.randomUUID().toString(),
            username = trimmedUsername,
            displayName = displayName.ifBlank { trimmedUsername },
            passwordHash = passwordHash,
            passwordEnabled = hasPassword
        )

        userProfileDao.insertProfile(profile)

        // Initialize default reader preferences for this profile
        readerPreferencesDao.savePreferences(
            ReaderPreferencesEntity(profileId = profile.id)
        )

        _activeProfile.value = profile
        return Result.success(profile)
    }

    suspend fun unlockProfile(profile: UserProfileEntity, password: String): Boolean {
        if (!profile.passwordEnabled) {
            selectProfile(profile)
            return true
        }
        val hash = profile.passwordHash ?: return false
        val valid = Argon2SecurityManager.verifyPassword(password, hash)
        if (valid) {
            selectProfile(profile)
        }
        return valid
    }

    suspend fun selectProfile(profile: UserProfileEntity) {
        userProfileDao.updateLastActive(profile.id)
        _activeProfile.value = profile.copy(lastActiveAt = System.currentTimeMillis())
    }

    suspend fun updateProfile(
        profileId: String,
        newUsername: String,
        newDisplayName: String,
        currentPassword: String? = null,
        newPassword: String? = null,
        removePassword: Boolean = false
    ): Result<UserProfileEntity> {
        val existing = userProfileDao.getProfileById(profileId)
            ?: return Result.failure(IllegalArgumentException("Profile not found"))

        val trimmedUsername = newUsername.trim()
        if (trimmedUsername.isBlank()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }

        // If username changed, check uniqueness
        if (!trimmedUsername.equals(existing.username, ignoreCase = true)) {
            val taken = userProfileDao.getProfileByUsername(trimmedUsername)
            if (taken != null && taken.id != profileId) {
                return Result.failure(IllegalStateException("Username '@$trimmedUsername' is already taken"))
            }
        }

        var updatedPasswordHash = existing.passwordHash
        var updatedPasswordEnabled = existing.passwordEnabled

        if (existing.passwordEnabled) {
            if (currentPassword.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Current password is required to update profile"))
            }
            val valid = existing.passwordHash?.let {
                Argon2SecurityManager.verifyPassword(currentPassword, it)
            } ?: false
            if (!valid) {
                return Result.failure(IllegalArgumentException("Current password is incorrect"))
            }

            if (removePassword) {
                updatedPasswordHash = null
                updatedPasswordEnabled = false
            } else if (!newPassword.isNullOrBlank()) {
                updatedPasswordHash = Argon2SecurityManager.hashPassword(newPassword)
                updatedPasswordEnabled = true
            }
        } else {
            // Profile currently does not have a password
            if (!newPassword.isNullOrBlank()) {
                updatedPasswordHash = Argon2SecurityManager.hashPassword(newPassword)
                updatedPasswordEnabled = true
            }
        }

        val updated = existing.copy(
            username = trimmedUsername,
            displayName = newDisplayName.ifBlank { trimmedUsername },
            passwordHash = updatedPasswordHash,
            passwordEnabled = updatedPasswordEnabled
        )

        userProfileDao.updateProfile(updated)

        if (_activeProfile.value?.id == profileId) {
            _activeProfile.value = updated
        }

        return Result.success(updated)
    }

    suspend fun deleteProfile(profile: UserProfileEntity): Result<Unit> {
        val count = userProfileDao.getProfileCount()
        if (count <= 1) {
            return Result.failure(IllegalStateException("Cannot delete the only profile"))
        }
        userProfileDao.deleteProfile(profile)
        if (_activeProfile.value?.id == profile.id) {
            val nextProfile = userProfileDao.getAllProfiles().firstOrNull()?.firstOrNull { it.id != profile.id }
            _activeProfile.value = nextProfile
        }
        return Result.success(Unit)
    }

    fun logout() {
        _activeProfile.value = null
    }
}
