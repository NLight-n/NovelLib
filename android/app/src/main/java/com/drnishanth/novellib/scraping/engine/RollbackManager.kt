package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.core.database.dao.SourceDefinitionDao
import com.drnishanth.novellib.core.database.entities.SourceDefinitionEntity
import com.drnishanth.novellib.scraping.models.SourceDefinition
import kotlinx.serialization.json.Json

class RollbackManager(
    private val sourceDefinitionDao: SourceDefinitionDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        const val FAILURE_THRESHOLD = 3
    }

    /**
     * Records a failure for a source definition. If failures reach the threshold
     * and a previous version exists, it rolls back automatically.
     * Returns true if a rollback was triggered.
     */
    suspend fun recordFailure(sourceId: String): Boolean {
        val entity = sourceDefinitionDao.getDefinitionById(sourceId) ?: return false
        val newFailureCount = entity.consecutiveFailures + 1

        if (newFailureCount >= FAILURE_THRESHOLD && entity.previousJsonContent != null) {
            // Trigger automatic rollback
            val rollbackResult = manualRollback(sourceId)
            return rollbackResult.isSuccess
        } else {
            sourceDefinitionDao.updateFailureCount(sourceId, newFailureCount)
            return false
        }
    }

    /**
     * Resets failure counter upon a successful scrape.
     */
    suspend fun recordSuccess(sourceId: String) {
        sourceDefinitionDao.updateFailureCount(sourceId, 0)
    }

    /**
     * Performs a rollback to the previous working definition stored for this source.
     */
    suspend fun manualRollback(sourceId: String): Result<SourceDefinitionEntity> {
        val current = sourceDefinitionDao.getDefinitionById(sourceId)
            ?: return Result.failure(IllegalArgumentException("Source definition $sourceId not found"))

        val prevContent = current.previousJsonContent
            ?: return Result.failure(IllegalStateException("No previous version available for rollback"))

        val prevVersion = current.previousVersion ?: 1

        return try {
            val parsedPrev = json.decodeFromString<SourceDefinition>(prevContent)

            val rolledBackEntity = current.copy(
                version = prevVersion,
                name = parsedPrev.name,
                description = parsedPrev.description,
                jsonContent = prevContent,
                checksum = current.previousChecksum,
                // Keep the current content as the "next" or nullify to prevent loop
                previousVersion = null,
                previousJsonContent = null,
                previousChecksum = null,
                consecutiveFailures = 0,
                updatedAt = System.currentTimeMillis()
            )

            sourceDefinitionDao.updateDefinition(rolledBackEntity)
            Result.success(rolledBackEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
