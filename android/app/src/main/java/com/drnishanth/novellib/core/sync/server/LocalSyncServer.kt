package com.drnishanth.novellib.core.sync.server

import android.content.Context
import com.drnishanth.novellib.core.database.dao.SyncDeviceDao
import com.drnishanth.novellib.core.database.entities.SyncDeviceEntity
import com.drnishanth.novellib.core.sync.engine.ConflictResolutionEngine
import com.drnishanth.novellib.core.sync.models.PairingConfirm
import com.drnishanth.novellib.core.sync.models.PairingRequest
import com.drnishanth.novellib.core.sync.models.PairingResponse
import com.drnishanth.novellib.core.sync.models.SyncPayload
import com.drnishanth.novellib.core.sync.security.DeviceIdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

data class IncomingPairingEvent(
    val request: PairingRequest,
    val sasCode: String
)

class LocalSyncServer(
    private val context: Context,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val syncDeviceDao: SyncDeviceDao,
    private val conflictResolutionEngine: ConflictResolutionEngine,
    private val localPayloadProvider: suspend () -> SyncPayload?
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var port: Int = 0
        private set

    private val _incomingPairingEvents = MutableSharedFlow<IncomingPairingEvent>(extraBufferCapacity = 5)
    val incomingPairingEvents: SharedFlow<IncomingPairingEvent> = _incomingPairingEvents.asSharedFlow()

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(preferredPort: Int = 8765): Int {
        if (isRunning) return port

        try {
            serverSocket = ServerSocket(preferredPort)
        } catch (_: Exception) {
            // Fallback to dynamic available port
            serverSocket = ServerSocket(0)
        }

        port = serverSocket!!.localPort
        isRunning = true

        serverJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return port
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        port = 0
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val output = s.getOutputStream()

                val requestLine = reader.readLine() ?: return@withContext
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@withContext

                val method = parts[0]
                val path = parts[1]

                // Read headers
                var contentLength = 0
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                // Read body if present
                val body = if (contentLength > 0) {
                    val charArray = CharArray(contentLength)
                    var readChars = 0
                    while (readChars < contentLength) {
                        val count = reader.read(charArray, readChars, contentLength - readChars)
                        if (count == -1) break
                        readChars += count
                    }
                    String(charArray, 0, readChars)
                } else ""

                routeRequest(method, path, body, output)
            }
        } catch (_: Exception) {}
    }

    private suspend fun routeRequest(method: String, path: String, body: String, output: OutputStream) {
        when {
            method == "GET" && path == "/api/sync/ping" -> {
                val pingJson = """{"deviceId":"${deviceIdentityManager.deviceId}","deviceName":"${deviceIdentityManager.deviceName}","deviceType":"${deviceIdentityManager.deviceType}","publicKey":"${deviceIdentityManager.publicKeyBase64}"}"""
                sendHttpResponse(output, 200, "application/json", pingJson)
            }

            method == "POST" && path == "/api/sync/pair/init" -> {
                val req = json.decodeFromString<PairingRequest>(body)
                val localNonce = UUID.randomUUID().toString()
                val sasCode = DeviceIdentityManager.computeSasCode(
                    keyA = req.senderPublicKey,
                    keyB = deviceIdentityManager.publicKeyBase64,
                    nonceA = req.nonce,
                    nonceB = localNonce
                )

                _incomingPairingEvents.emit(IncomingPairingEvent(req, sasCode))

                val resp = PairingResponse(
                    accepted = true,
                    receiverDeviceId = deviceIdentityManager.deviceId,
                    receiverDeviceName = deviceIdentityManager.deviceName,
                    receiverDeviceType = deviceIdentityManager.deviceType,
                    receiverPublicKey = deviceIdentityManager.publicKeyBase64,
                    nonce = localNonce,
                    sasCode = sasCode
                )
                sendHttpResponse(output, 200, "application/json", json.encodeToString(resp))
            }

            method == "POST" && path == "/api/sync/pair/confirm" -> {
                val confirm = json.decodeFromString<PairingConfirm>(body)
                if (confirm.confirmed) {
                    syncDeviceDao.setTrusted(confirm.senderDeviceId, true)
                }
                sendHttpResponse(output, 200, "application/json", """{"status":"ok"}""")
            }

            method == "POST" && path == "/api/sync/exchange" -> {
                val incomingPayload = json.decodeFromString<SyncPayload>(body)

                // Verify sender device is trusted
                val senderDevice = syncDeviceDao.getDeviceByDeviceId(incomingPayload.senderDeviceId)
                if (senderDevice == null || !senderDevice.trusted) {
                    sendHttpResponse(output, 403, "application/json", """{"error":"Device not paired or trusted"}""")
                    return
                }

                // Merge incoming payload locally
                conflictResolutionEngine.mergePayload(incomingPayload)

                // Update device last seen
                syncDeviceDao.updateLastSeen(incomingPayload.senderDeviceId)

                // Return local payload back to sender for bidirectional sync
                val localPayload = localPayloadProvider()
                if (localPayload != null) {
                    val responseJson = json.encodeToString(localPayload)
                    sendHttpResponse(output, 200, "application/json", responseJson)
                } else {
                    sendHttpResponse(output, 200, "application/json", """{"status":"merged"}""")
                }
            }

            method == "GET" && path.startsWith("/api/sync/chapter/") -> {
                val parts = path.removePrefix("/api/sync/chapter/").split("/")
                if (parts.size >= 2) {
                    val novelId = parts[0]
                    val contentHash = parts[1]
                    val file = File(context.filesDir, "chapters/$novelId/$contentHash.html")
                    if (file.exists()) {
                        sendHttpResponse(output, 200, "text/html; charset=UTF-8", file.readText(Charsets.UTF_8))
                    } else {
                        sendHttpResponse(output, 404, "text/plain", "Chapter not found")
                    }
                } else {
                    sendHttpResponse(output, 400, "text/plain", "Bad chapter request")
                }
            }

            else -> {
                sendHttpResponse(output, 404, "text/plain", "Not Found")
            }
        }
    }

    private fun sendHttpResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 403) "Forbidden" else if (statusCode == 404) "Not Found" else "Error"
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${contentBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(contentBytes)
        output.flush()
    }
}
