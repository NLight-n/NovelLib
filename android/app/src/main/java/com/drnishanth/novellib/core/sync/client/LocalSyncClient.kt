package com.drnishanth.novellib.core.sync.client

import com.drnishanth.novellib.core.sync.models.DiscoveredDevice
import com.drnishanth.novellib.core.sync.models.PairingConfirm
import com.drnishanth.novellib.core.sync.models.PairingRequest
import com.drnishanth.novellib.core.sync.models.PairingResponse
import com.drnishanth.novellib.core.sync.models.SyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LocalSyncClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun ping(host: String, port: Int): Result<DiscoveredDevice> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$host:$port/api/sync/ping")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(IllegalStateException("Empty body"))
            val device = json.decodeFromString<DiscoveredDevice>(body).copy(hostAddress = host, port = port)
            Result.success(device)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiatePairing(
        host: String,
        port: Int,
        requestPayload: PairingRequest
    ): Result<PairingResponse> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(requestPayload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("http://$host:$port/api/sync/pair/init")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Pairing rejected: HTTP ${response.code}"))
            }

            val respBody = response.body?.string() ?: return@withContext Result.failure(IllegalStateException("Empty pairing response"))
            val pairingResponse = json.decodeFromString<PairingResponse>(respBody)
            Result.success(pairingResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmPairing(
        host: String,
        port: Int,
        confirmPayload: PairingConfirm
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(confirmPayload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("http://$host:$port/api/sync/pair/confirm")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exchangeSync(
        host: String,
        port: Int,
        payload: SyncPayload
    ): Result<SyncPayload?> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("http://$host:$port/api/sync/exchange")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Sync failed: HTTP ${response.code}"))
            }

            val respBody = response.body?.string()
            if (respBody.isNullOrBlank() || respBody.contains("\"status\":\"merged\"")) {
                return@withContext Result.success(null)
            }

            val remotePayload = json.decodeFromString<SyncPayload>(respBody)
            Result.success(remotePayload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchChapterContent(
        host: String,
        port: Int,
        novelId: String,
        contentHash: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$host:$port/api/sync/chapter/$novelId/$contentHash")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Chapter download failed: HTTP ${response.code}"))
            }

            val content = response.body?.string() ?: return@withContext Result.failure(IllegalStateException("Empty content"))
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
