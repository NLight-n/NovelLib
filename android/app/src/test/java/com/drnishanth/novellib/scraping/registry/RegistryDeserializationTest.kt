package com.drnishanth.novellib.scraping.registry

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RegistryDeserializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testParseActiveRegistryJsonFile() {
        val candidates = listOf(
            File("../../sources/registry.json"),
            File("../sources/registry.json"),
            File("sources/registry.json")
        )
        val registryFile = candidates.firstOrNull { it.exists() }
        assertNotNull("sources/registry.json must exist in one of candidate paths", registryFile)
        val content = registryFile!!.readText()

        val parsed = json.decodeFromString<SourceRegistryIndex>(content)
        assertTrue("Registry version should be >= 11", parsed.effectiveVersion >= 11)
        assertEquals("Should have 6 active sources", 6, parsed.sources.size)

        val royalRoad = parsed.sources.firstOrNull { it.id == "royalroad" }
        assertNotNull("Royal Road source item should be present", royalRoad)
        assertEquals("royalroad", royalRoad?.id)
        assertEquals("Royal Road", royalRoad?.name)
        assertTrue("Royal Road version should be >= 5", (royalRoad?.version ?: 0) >= 5)
        assertEquals(
            "3f6c88d757b624e96846524d86ef3f61d76c970aad9901364c07f30ff34a9aa7",
            royalRoad?.effectiveChecksum
        )

        // Verify all 6 sources have non-empty effectiveChecksum
        for (source in parsed.sources) {
            assertTrue("Source ${source.id} must have effectiveChecksum", source.effectiveChecksum.isNotBlank())
        }
    }

    @Test
    fun testParseWithOnlySha256Field() {
        val payload = """
            {
              "registry_version": 11,
              "last_updated": "2026-10-05T08:35:00Z",
              "sources": [
                {
                  "id": "test_src",
                  "name": "Test Source",
                  "version": 1,
                  "sha256": "abcdef1234567890"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SourceRegistryIndex>(payload)
        assertEquals(1, parsed.sources.size)
        assertEquals("abcdef1234567890", parsed.sources[0].effectiveChecksum)
    }

    @Test
    fun testParseWithOnlyChecksumField() {
        val payload = """
            {
              "version": 7,
              "updated_at": 1728117300000,
              "sources": [
                {
                  "id": "test_src",
                  "name": "Test Source",
                  "version": 1,
                  "checksum": "fedcba0987654321"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SourceRegistryIndex>(payload)
        assertEquals(1, parsed.sources.size)
        assertEquals("fedcba0987654321", parsed.sources[0].effectiveChecksum)
    }

    @Test
    fun testParseWithBothChecksumAndSha256() {
        val payload = """
            {
              "version": 11,
              "registry_version": 11,
              "sources": [
                {
                  "id": "test_src",
                  "name": "Test Source",
                  "version": 2,
                  "checksum": "hash1",
                  "sha256": "hash1"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SourceRegistryIndex>(payload)
        assertEquals(1, parsed.sources.size)
        assertEquals("hash1", parsed.sources[0].effectiveChecksum)
    }
}
