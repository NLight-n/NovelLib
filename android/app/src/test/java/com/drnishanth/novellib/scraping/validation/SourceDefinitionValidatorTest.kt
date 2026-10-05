package com.drnishanth.novellib.scraping.validation

import com.drnishanth.novellib.scraping.engine.DefaultSourceDefinitions
import com.drnishanth.novellib.scraping.models.SelectorRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDefinitionValidatorTest {

    @Test
    fun testValidRoyalRoadDefinition() {
        val result = SourceDefinitionValidator.validate(DefaultSourceDefinitions.ROYAL_ROAD)
        assertTrue("Royal Road definition should be valid", result is SourceDefinitionValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidScribbleHubDefinition() {
        val result = SourceDefinitionValidator.validate(DefaultSourceDefinitions.SCRIBBLE_HUB)
        assertTrue("Scribble Hub definition should be valid", result is SourceDefinitionValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidNovGoDefinition() {
        val result = SourceDefinitionValidator.validate(DefaultSourceDefinitions.NOVGO)
        assertTrue("NovGo definition should be valid", result is SourceDefinitionValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidLitFicDefinition() {
        val result = SourceDefinitionValidator.validate(DefaultSourceDefinitions.LITFIC)
        assertTrue("LitFic definition should be valid", result is SourceDefinitionValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidTapasDefinition() {
        val result = SourceDefinitionValidator.validate(DefaultSourceDefinitions.TAPAS)
        assertTrue("Tapas definition should be valid", result is SourceDefinitionValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidNovelUpdatesDefinition() {
        val result = SourceDefinitionValidator.validate(DefaultSourceDefinitions.NOVEL_UPDATES)
        assertTrue("Novel Updates definition should be valid", result is SourceDefinitionValidator.ValidationResult.Valid)
    }

    @Test
    fun testRejectIncompatibleEngineVersion() {
        val futureDef = DefaultSourceDefinitions.ROYAL_ROAD.copy(minimumEngineVersion = 99)
        val result = SourceDefinitionValidator.validate(futureDef)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
        val msg = (result as SourceDefinitionValidator.ValidationResult.Invalid).message
        assertTrue(msg.contains("requires engine version 99"))
    }

    @Test
    fun testRejectInvalidId() {
        val invalidIdDef = DefaultSourceDefinitions.ROYAL_ROAD.copy(id = "Invalid ID With Spaces & Caps!")
        val result = SourceDefinitionValidator.validate(invalidIdDef)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
    }

    @Test
    fun testRejectMissingRequiredSelectors() {
        val missingTitleDef = DefaultSourceDefinitions.ROYAL_ROAD.copy(
            novel = DefaultSourceDefinitions.ROYAL_ROAD.novel.copy(
                title = SelectorRule(selector = "", type = "text")
            )
        )
        val result = SourceDefinitionValidator.validate(missingTitleDef)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
        val msg = (result as SourceDefinitionValidator.ValidationResult.Invalid).message
        assertTrue(msg.contains("novel.title selector cannot be blank"))
    }

    @Test
    fun testRejectUnsupportedExtractionType() {
        val invalidTypeDef = DefaultSourceDefinitions.ROYAL_ROAD.copy(
            chapter = DefaultSourceDefinitions.ROYAL_ROAD.chapter.copy(
                content = SelectorRule(selector = ".content", type = "unsupported_type")
            )
        )
        val result = SourceDefinitionValidator.validate(invalidTypeDef)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
        val msg = (result as SourceDefinitionValidator.ValidationResult.Invalid).message
        assertTrue(msg.contains("unsupported type"))
    }

    @Test
    fun testRejectInvalidRenderingMode() {
        val invalidMode = DefaultSourceDefinitions.ROYAL_ROAD.copy(
            rendering = com.drnishanth.novellib.scraping.models.RenderingRule(mode = "invalid_mode")
        )
        val result = SourceDefinitionValidator.validate(invalidMode)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
        val msg = (result as SourceDefinitionValidator.ValidationResult.Invalid).message
        assertTrue(msg.contains("rendering.mode 'invalid_mode' is invalid"))
    }

    @Test
    fun testRejectInvalidMaxWaitMs() {
        val invalidWait = DefaultSourceDefinitions.ROYAL_ROAD.copy(
            rendering = com.drnishanth.novellib.scraping.models.RenderingRule(maxWaitMs = 50000L)
        )
        val result = SourceDefinitionValidator.validate(invalidWait)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
        val msg = (result as SourceDefinitionValidator.ValidationResult.Invalid).message
        assertTrue(msg.contains("rendering.max_wait_ms must be between 1000 and 20000 ms"))
    }

    @Test
    fun testRejectExcessiveFallbackSelectors() {
        val rules = (1..10).map { SelectorRule(".content-$it", "html") }
        val tooMany = DefaultSourceDefinitions.ROYAL_ROAD.copy(
            chapter = DefaultSourceDefinitions.ROYAL_ROAD.chapter.copy(
                contentSelectors = rules
            )
        )
        val result = SourceDefinitionValidator.validate(tooMany)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
        val msg = (result as SourceDefinitionValidator.ValidationResult.Invalid).message
        assertTrue(msg.contains("cannot contain more than 8 fallback selectors"))
    }

    @Test
    fun testRejectInvalidLinkDensity() {
        val invalidDensity = DefaultSourceDefinitions.ROYAL_ROAD.copy(
            chapter = DefaultSourceDefinitions.ROYAL_ROAD.chapter.copy(
                validation = com.drnishanth.novellib.scraping.models.ContentValidationRule(maxLinkDensity = 2.5)
            )
        )
        val result = SourceDefinitionValidator.validate(invalidDensity)
        assertTrue(result is SourceDefinitionValidator.ValidationResult.Invalid)
        val msg = (result as SourceDefinitionValidator.ValidationResult.Invalid).message
        assertTrue(msg.contains("chapter.validation.max_link_density must be between 0.0 and 1.0"))
    }
}
