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

    fun logout() {
        _activeProfile.value = null
    }
}
