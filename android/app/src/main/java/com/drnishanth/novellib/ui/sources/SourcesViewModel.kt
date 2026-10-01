package com.drnishanth.novellib.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.dao.SourceDefinitionDao
import com.drnishanth.novellib.core.database.entities.SourceDefinitionEntity
import com.drnishanth.novellib.scraping.engine.DefaultSourceDefinitions
import com.drnishanth.novellib.scraping.engine.RollbackManager
import com.drnishanth.novellib.scraping.registry.RegistrySourceItem
import com.drnishanth.novellib.scraping.registry.SourceDefinitionRepositoryClient
import com.drnishanth.novellib.scraping.registry.SourceRegistryIndex
import com.drnishanth.novellib.scraping.registry.SourceUpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SourcesUiState(
    val isCheckingUpdates: Boolean = false,
    val isUpdatingSourceId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val registryUrl: String = SourceDefinitionRepositoryClient.DEFAULT_REGISTRY_URL,
    val updateStatuses: List<SourceUpdateStatus> = emptyList()
)

class SourcesViewModel(
    private val sourceDefinitionDao: SourceDefinitionDao = NovelLibApplication.instance.database.sourceDefinitionDao(),
    private val repositoryClient: SourceDefinitionRepositoryClient = NovelLibApplication.instance.sourceDefinitionRepositoryClient,
    private val rollbackManager: RollbackManager = NovelLibApplication.instance.rollbackManager
) : ViewModel() {

    val installedDefinitions: StateFlow<List<SourceDefinitionEntity>> =
        sourceDefinitionDao.getAllDefinitionsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    private var cachedRemoteIndex: SourceRegistryIndex? = null

    init {
        // Seed or auto-upgrade built-in definitions into Room
        viewModelScope.launch {
            val json = Json { prettyPrint = true }
            for (builtin in DefaultSourceDefinitions.BUILTIN_DEFINITIONS) {
                val existing = sourceDefinitionDao.getDefinitionById(builtin.id)
                if (existing == null) {
                    val rawJson = json.encodeToString(builtin)
                    sourceDefinitionDao.insertDefinition(
                        SourceDefinitionEntity(
                            id = builtin.id,
                            version = builtin.version,
                            name = builtin.name,
                            description = builtin.description,
                            enabled = true,
                            minimumEngineVersion = builtin.minimumEngineVersion,
                            jsonContent = rawJson,
                            installedAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else if (builtin.version > existing.version) {
                    val rawJson = json.encodeToString(builtin)
                    sourceDefinitionDao.insertDefinition(
                        existing.copy(
                            version = builtin.version,
                            name = builtin.name,
                            description = builtin.description,
                            minimumEngineVersion = builtin.minimumEngineVersion,
                            jsonContent = rawJson,
                            previousVersion = existing.version,
                            previousJsonContent = existing.jsonContent,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdates = true, errorMessage = null, message = null)
            val result = repositoryClient.checkForUpdates(_uiState.value.registryUrl)
            if (result.isSuccess) {
                val statuses = result.getOrNull() ?: emptyList()
                val fetchIndexResult = repositoryClient.fetchRegistryIndex(_uiState.value.registryUrl)
                cachedRemoteIndex = fetchIndexResult.getOrNull()

                val updatesCount = statuses.count { it.hasUpdate }
                val msg = if (updatesCount > 0) "$updatesCount update(s) available!" else "All sources are up to date."
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdates = false,
                    updateStatuses = statuses,
                    message = msg
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdates = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to check for updates"
                )
            }
        }
    }

    fun updateSource(sourceId: String) {
        val remoteItem: RegistrySourceItem? = cachedRemoteIndex?.sources?.firstOrNull { it.id == sourceId }
        if (remoteItem == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Source metadata not loaded. Check for updates first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingSourceId = sourceId, errorMessage = null)
            val result = repositoryClient.installOrUpdate(remoteItem, _uiState.value.registryUrl)
            if (result.isSuccess) {
                val successMsg = "Successfully updated ${remoteItem.name} to v${remoteItem.version}!"
                val checkResult = repositoryClient.checkForUpdates(_uiState.value.registryUrl)
                val statuses = checkResult.getOrNull() ?: emptyList()
                val fetchIndexResult = repositoryClient.fetchRegistryIndex(_uiState.value.registryUrl)
                cachedRemoteIndex = fetchIndexResult.getOrNull()

                _uiState.value = _uiState.value.copy(
                    isUpdatingSourceId = null,
                    updateStatuses = statuses,
                    message = successMsg
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isUpdatingSourceId = null,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to update source"
                )
            }
        }
    }

    fun rollbackSource(sourceId: String) {
        viewModelScope.launch {
            val result = rollbackManager.manualRollback(sourceId)
            if (result.isSuccess) {
                val rolledBack = result.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    message = "Rolled back ${rolledBack.name} to v${rolledBack.version}."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Rollback failed"
                )
            }
        }
    }

    fun toggleSourceEnabled(sourceId: String, currentEnabled: Boolean) {
        viewModelScope.launch {
            sourceDefinitionDao.updateEnabled(sourceId, !currentEnabled)
        }
    }

    fun setRegistryUrl(url: String) {
        _uiState.value = _uiState.value.copy(registryUrl = url.trim())
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }
}
