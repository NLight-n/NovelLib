package com.drnishanth.novellib.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.entities.SyncDeviceEntity
import com.drnishanth.novellib.core.database.entities.UserProfileEntity
import com.drnishanth.novellib.core.sync.models.DiscoveredDevice
import com.drnishanth.novellib.core.sync.models.PairingResponse
import com.drnishanth.novellib.core.sync.models.SyncResult
import com.drnishanth.novellib.core.sync.repository.SyncRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val pendingPairingResponse: PairingResponse? = null,
    val pendingPairingDevice: DiscoveredDevice? = null,
    val incomingPairingDeviceName: String? = null,
    val incomingPairingDeviceId: String? = null,
    val incomingPairingPublicKey: String? = null,
    val incomingSasCode: String? = null,
    val showPairingDialog: Boolean = false,
    val showIncomingPairingDialog: Boolean = false,
    val lastSyncResult: SyncResult? = null
)

class SyncViewModel(
    private val syncRepository: SyncRepository = NovelLibApplication.instance.syncRepository,
    private val profileRepository: ProfileRepository = NovelLibApplication.instance.profileRepository
) : ViewModel() {

    val activeProfile: StateFlow<UserProfileEntity?> = profileRepository.activeProfile
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = syncRepository.discoveredDevices
    val trustedDevices: StateFlow<List<SyncDeviceEntity>> = syncRepository.trustedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myDeviceId: String = syncRepository.deviceIdentityManager.deviceId
    val myDeviceName: String = syncRepository.deviceIdentityManager.deviceName

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        // Start NSD discovery and local sync server
        syncRepository.startSyncMode()

        // Listen for incoming pairing events from peer devices
        viewModelScope.launch {
            syncRepository.incomingPairingEvents.collect { event ->
                _uiState.value = _uiState.value.copy(
                    incomingPairingDeviceName = event.request.senderDeviceName,
                    incomingPairingDeviceId = event.request.senderDeviceId,
                    incomingPairingPublicKey = event.request.senderPublicKey,
                    incomingSasCode = event.sasCode,
                    showIncomingPairingDialog = true
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncRepository.stopSyncMode()
    }

    fun initiatePairing(device: DiscoveredDevice) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = "Connecting to ${device.deviceName}...")
            val result = syncRepository.initiatePairing(device)
            result.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    pendingPairingResponse = response,
                    pendingPairingDevice = device,
                    showPairingDialog = true,
                    statusMessage = null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(statusMessage = "Pairing failed: ${err.message}")
            }
        }
    }

    fun confirmOutgoingPairing() {
        val device = _uiState.value.pendingPairingDevice ?: return
        val response = _uiState.value.pendingPairingResponse ?: return
        viewModelScope.launch {
            val confirmed = syncRepository.confirmPairing(device, response.receiverPublicKey)
            _uiState.value = _uiState.value.copy(
                showPairingDialog = false,
                pendingPairingDevice = null,
                pendingPairingResponse = null,
                statusMessage = if (confirmed.getOrDefault(false)) "Device paired and trusted!" else "Pairing confirmation failed"
            )
        }
    }

    fun cancelOutgoingPairing() {
        _uiState.value = _uiState.value.copy(
            showPairingDialog = false,
            pendingPairingDevice = null,
            pendingPairingResponse = null
        )
    }

    fun acceptIncomingPairing() {
        val deviceId = _uiState.value.incomingPairingDeviceId ?: return
        val deviceName = _uiState.value.incomingPairingDeviceName ?: return
        val publicKey = _uiState.value.incomingPairingPublicKey ?: return
        viewModelScope.launch {
            syncRepository.acceptPairing(
                peerDeviceId = deviceId,
                peerDeviceName = deviceName,
                peerDeviceType = "phone",
                peerPublicKey = publicKey
            )
            _uiState.value = _uiState.value.copy(
                showIncomingPairingDialog = false,
                incomingPairingDeviceId = null,
                incomingPairingDeviceName = null,
                incomingPairingPublicKey = null,
                incomingSasCode = null,
                statusMessage = "Accepted pairing with $deviceName"
            )
        }
    }

    fun rejectIncomingPairing() {
        _uiState.value = _uiState.value.copy(
            showIncomingPairingDialog = false,
            incomingPairingDeviceId = null,
            incomingPairingDeviceName = null,
            incomingPairingPublicKey = null,
            incomingSasCode = null
        )
    }

    fun startSyncWithDevice(host: String, port: Int) {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, statusMessage = "Syncing with peer device...")
            val result = syncRepository.executeSync(host, port, profileId)
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                lastSyncResult = result.getOrNull(),
                statusMessage = if (result.isSuccess) result.getOrNull()?.message else "Sync failed: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    fun forgetDevice(deviceId: String) {
        viewModelScope.launch {
            syncRepository.forgetDevice(deviceId)
            _uiState.value = _uiState.value.copy(statusMessage = "Device removed from trusted list")
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }
}
