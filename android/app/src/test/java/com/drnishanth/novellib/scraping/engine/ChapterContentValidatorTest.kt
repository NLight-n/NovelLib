package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ContentValidationRule
import com.drnishanth.novellib.scraping.models.ExtractionFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterContentValidatorTest {

    @Test
    fun testValidChapterContentPasses() {
        val html = """
            <p>The quick brown fox jumps over the lazy dog. This is a very interesting novel chapter with several paragraphs of genuine story text.</p>
            <p>He looked into the distant horizon and realized the journey had only begun. Every obstacle in front of him would be overcome with patience and determination.</p>
            <p>The night sky was filled with sparkling stars, whispering secrets of ancient constellations and legendary magical spells.</p>
        """.trimIndent()

        val rule = ContentValidationRule(
            minTextCharacters = 100,
            minParagraphs = 2,
            maxLinkDensity = 0.35
        )

        val result = ChapterContentValidator.validate(
            sanitizedHtml = html,
            docTitle = "Chapter 1 - The Beginning",
            rule = rule
        )

        assertTrue("Expected valid result", result.valid)
        assertEquals(null, result.reason)
        assertTrue(result.characterCount > 100)
        assertTrue(result.paragraphCount >= 2)
    }

    @Test
    fun testRejectBlankHtml() {
        val result = ChapterContentValidator.validate(sanitizedHtml = "   ")
        assertFalse("Blank HTML must be invalid", result.valid)
        assertEquals(ExtractionFailureReason.NO_CONTENT, result.reason)
    }

    @Test
    fun testRejectCloudflareChallenge() {
        val challengeHtml = """
            <div id="cf-wrapper">
                <h1>Just a moment...</h1>
                <p>Checking your browser before accessing the website. CF-Browser-Verification in progress.</p>
            </div>
        """.trimIndent()

        val result = ChapterContentValidator.validate(
            sanitizedHtml = challengeHtml,
            docTitle = "Just a moment..."
        )

        assertFalse("Challenge page must be rejected", result.valid)
        assertEquals(ExtractionFailureReason.CHALLENGE, result.reason)
    }

    @Test
    fun testRejectAuthenticationRequired() {
        val loginHtml = """
            <div class="login-box">
                <h2>Members Only</h2>
                <p>Sign in to continue reading this chapter. Premium subscription required.</p>
            </div>
        """.trimIndent()

        val result = ChapterContentValidator.validate(
            sanitizedHtml = loginHtml,
            docTitle = "Sign in to continue"
        )

        assertFalse("Login wall must be rejected", result.valid)
        assertEquals(ExtractionFailureReason.AUTH_REQUIRED, result.reason)
    }

    @Test
    fun testRejectShortContent() {
        val shortHtml = "<p>Too short.</p>"
        val rule = ContentValidationRule(minTextCharacters = 100, minParagraphs = 1)
        val result = ChapterContentValidator.validate(sanitizedHtml = shortHtml, rule = rule)

        assertFalse("Content shorter than minTextCharacters must be rejected", result.valid)
        assertEquals(ExtractionFailureReason.CONTENT_INVALID, result.reason)
    }

    @Test
    fun testRejectHighLinkDensity() {
        // Mostly navigation / link farm
        val linkSpam = """
            <p><a href="/1">Chapter 1</a> <a href="/2">Chapter 2</a> <a href="/3">Chapter 3</a> <a href="/4">Chapter 4</a></p>
            <p><a href="/5">Chapter 5</a> <a href="/6">Chapter 6</a> <a href="/7">Chapter 7</a> <a href="/8">Chapter 8</a></p>
            <p>End.</p>
        """.trimIndent()

        val rule = ContentValidationRule(minTextCharacters = 30, minParagraphs = 2, maxLinkDensity = 0.40)
        val result = ChapterContentValidator.validate(sanitizedHtml = linkSpam, rule = rule)

        assertFalse("Link spam must be rejected", result.valid)
        assertEquals(ExtractionFailureReason.CONTENT_INVALID, result.reason)
    }

    @Test
    fun testRejectCustomPatterns() {
        val html = "<p>Notice: DMCA Takedown Notice applied to this fiction. Content removed by administrator.</p><p>Second paragraph with some filler text to exceed minimum length.</p>"
        val rule = ContentValidationRule(
            minTextCharacters = 50,
            rejectTitlePatterns = listOf("dmca takedown")
        )
        val result = ChapterContentValidator.validate(sanitizedHtml = html, rule = rule)

        assertFalse("Custom reject pattern must trigger rejection", result.valid)
        assertEquals(ExtractionFailureReason.CONTENT_INVALID, result.reason)
    }
}
