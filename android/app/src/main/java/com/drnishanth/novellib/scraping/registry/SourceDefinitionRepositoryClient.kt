package com.drnishanth.novellib.scraping.registry

import com.drnishanth.novellib.core.database.dao.SourceDefinitionDao
import com.drnishanth.novellib.core.database.entities.SourceDefinitionEntity
import com.drnishanth.novellib.scraping.models.SourceDefinition
import com.drnishanth.novellib.scraping.security.IntegrityAndSignatureVerifier
import com.drnishanth.novellib.scraping.validation.SourceDefinitionValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

class SourceDefinitionRepositoryClient(
    private val sourceDefinitionDao: SourceDefinitionDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    companion object {
        const val DEFAULT_REGISTRY_URL =
            "https://raw.githubusercontent.com/NLight-n/NovelLib/main/sources/registry.json"
    }

    /**
     * Downloads and parses the registry index from the specified GitHub URL.
     */
    suspend fun fetchRegistryIndex(
        registryUrl: String = DEFAULT_REGISTRY_URL
    ): Result<SourceRegistryIndex> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(registryUrl)
                .header("User-Agent", "NovelLibrary-Updater/0.1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to fetch registry: HTTP ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty registry response"))

            val index = json.decodeFromString<SourceRegistryIndex>(body)
            Result.success(index)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compares available remote versions in the registry against installed definitions in Room.
     */
    suspend fun checkForUpdates(
        registryUrl: String = DEFAULT_REGISTRY_URL
    ): Result<List<SourceUpdateStatus>> = withContext(Dispatchers.IO) {
        val registryResult = fetchRegistryIndex(registryUrl)
        if (registryResult.isFailure) {
            return@withContext Result.failure(registryResult.exceptionOrNull()!!)
        }

        val remoteSources = registryResult.getOrNull()!!.sources
        val installedMap = sourceDefinitionDao.getAllDefinitionsDirect().associateBy { it.id }

        val statuses = remoteSources.map { remote ->
            val installed = installedMap[remote.id]
            val currentVer = installed?.version ?: 0
            val hasUpdate = remote.version > currentVer
            val enabled = installed?.enabled ?: true
            val canRollback = installed?.previousJsonContent != null

            SourceUpdateStatus(
                sourceId = remote.id,
                name = remote.name,
                currentVersion = currentVer,
                latestVersion = remote.version,
                hasUpdate = hasUpdate,
                enabled = enabled,
                consecutiveFailures = installed?.consecutiveFailures ?: 0,
                canRollback = canRollback
            )
        }

        Result.success(statuses)
    }

    /**
     * Downloads, validates, checksum-verifies, and installs or updates a source definition.
     */
    suspend fun installOrUpdate(
        item: RegistrySourceItem,
        registryUrl: String = DEFAULT_REGISTRY_URL,
        publisherPublicKeyBase64: String? = null
    ): Result<SourceDefinitionEntity> = withContext(Dispatchers.IO) {
        try {
            // Resolve download URL
            val downloadUrl = resolveDefinitionUrl(item, registryUrl)

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "NovelLibrary-Updater/0.1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to download definition for ${item.id}: HTTP ${response.code}")
                )
            }

            val rawJson = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty definition body received"))

            // 1. Checksum verification
            val checksumValid = IntegrityAndSignatureVerifier.verifyChecksum(rawJson, item.checksum)
            if (!checksumValid) {
                return@withContext Result.failure(
                    SecurityException("Checksum mismatch for ${item.id}. Expected: ${item.checksum}")
                )
            }

            // 2. Cryptographic signature verification
            val signatureValid = IntegrityAndSignatureVerifier.verifySignature(
                content = rawJson,
                signatureBase64 = item.signature,
                publicKeyBase64 = publisherPublicKeyBase64
            )
            if (!signatureValid) {
                return@withContext Result.failure(
                    SecurityException("Invalid digital signature for source definition ${item.id}")
                )
            }

            // 3. Schema and capability validation
            val parsedDefinition = json.decodeFromString<SourceDefinition>(rawJson)
            val validationResult = SourceDefinitionValidator.validate(parsedDefinition)
            if (validationResult is SourceDefinitionValidator.ValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalArgumentException("Definition validation failed: ${validationResult.message}")
                )
            }

            // 4. Preserve existing version for rollback if updating
            val existing = sourceDefinitionDao.getDefinitionById(item.id)

            val entity = SourceDefinitionEntity(
                id = parsedDefinition.id,
                version = parsedDefinition.version,
                name = parsedDefinition.name,
                description = parsedDefinition.description,
                enabled = existing?.enabled ?: true,
                minimumEngineVersion = parsedDefinition.minimumEngineVersion,
                definitionUrl = downloadUrl,
                checksum = item.checksum,
                signature = item.signature,
                jsonContent = rawJson,
                previousVersion = existing?.version,
                previousJsonContent = existing?.jsonContent,
                previousChecksum = existing?.checksum,
                consecutiveFailures = 0,
                installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            sourceDefinitionDao.insertDefinition(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveDefinitionUrl(item: RegistrySourceItem, registryUrl: String): String {
        return if (!item.url.isNullOrBlank()) {
            item.url
        } else if (!item.relativePath.isNullOrBlank()) {
            val baseUri = URI(registryUrl)
            baseUri.resolve(item.relativePath).toString()
        } else {
            throw IllegalArgumentException("Registry item ${item.id} has neither url nor relativePath")
        }
    }
}
