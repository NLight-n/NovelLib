package com.drnishanth.novellib.core.sync.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.drnishanth.novellib.core.sync.models.DiscoveredDevice
import com.drnishanth.novellib.core.sync.security.DeviceIdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NsdDiscoveryManager(
    private val context: Context,
    private val deviceIdentityManager: DeviceIdentityManager
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val scope = CoroutineScope(Dispatchers.IO)
    private val resolveMutex = Mutex()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile
    var isAdvertising: Boolean = false
        private set

    @Volatile
    var isDiscovering: Boolean = false
        private set

    companion object {
        const val SERVICE_TYPE = "_novellib-sync._tcp."
    }

    fun startAdvertising(port: Int) {
        if (isAdvertising || nsdManager == null) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "NovelLib-${deviceIdentityManager.deviceId}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("device_id", deviceIdentityManager.deviceId)
            setAttribute("device_name", deviceIdentityManager.deviceName)
            setAttribute("device_type", deviceIdentityManager.deviceType)
            setAttribute("public_key", deviceIdentityManager.publicKeyBase64)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                isAdvertising = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isAdvertising = false
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                isAdvertising = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isAdvertising = false
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (_: Exception) {
            isAdvertising = false
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising || nsdManager == null) return
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (_: Exception) {}
        }
        registrationListener = null
        isAdvertising = false
    }

    fun startDiscovery() {
        if (isDiscovering || nsdManager == null) return
        _discoveredDevices.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Ignore services advertised by our own device ID
                if (serviceInfo.serviceName.contains(deviceIdentityManager.deviceId)) return

                scope.launch {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val current = _discoveredDevices.value.toMutableList()
                current.removeAll { it.deviceName == serviceInfo.serviceName || it.deviceId in serviceInfo.serviceName }
                _discoveredDevices.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: Exception) {
            isDiscovering = false
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering || nsdManager == null) return
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (_: Exception) {}
        }
        discoveryListener = null
        isDiscovering = false
    }

    private suspend fun resolveService(info: NsdServiceInfo) {
        if (nsdManager == null) return

        resolveMutex.withLock {
            try {
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        val host = resolvedInfo.host?.hostAddress ?: return
                        val port = resolvedInfo.port

                        val deviceIdAttr = resolvedInfo.attributes["device_id"]?.let { String(it, Charsets.UTF_8) }
                            ?: resolvedInfo.serviceName.substringAfter("NovelLib-", resolvedInfo.serviceName)

                        // Skip own device
                        if (deviceIdAttr == deviceIdentityManager.deviceId) return

                        val deviceNameAttr = resolvedInfo.attributes["device_name"]?.let { String(it, Charsets.UTF_8) }
                            ?: resolvedInfo.serviceName

                        val deviceTypeAttr = resolvedInfo.attributes["device_type"]?.let { String(it, Charsets.UTF_8) }
                            ?: "phone"

                        val publicKeyAttr = resolvedInfo.attributes["public_key"]?.let { String(it, Charsets.UTF_8) }
                            ?: ""

                        val device = DiscoveredDevice(
                            deviceId = deviceIdAttr,
                            deviceName = deviceNameAttr,
                            deviceType = deviceTypeAttr,
                            hostAddress = host,
                            port = port,
                            publicKeyBase64 = publicKeyAttr
                        )

                        val current = _discoveredDevices.value.toMutableList()
                        current.removeAll { it.deviceId == device.deviceId || (it.hostAddress == device.hostAddress && it.port == device.port) }
                        current.add(device)
                        _discoveredDevices.value = current
                    }
                })
            } catch (_: Exception) {}
        }
    }
}
