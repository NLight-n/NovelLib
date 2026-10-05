package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ContentValidationResult
import com.drnishanth.novellib.scraping.models.ContentValidationRule
import com.drnishanth.novellib.scraping.models.ExtractionFailureReason
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object ChapterContentValidator {

    private val CHALLENGE_PATTERNS = listOf(
        "just a moment",
        "checking your browser",
        "cf-browser-verification",
        "ddos protection",
        "access denied",
        "verify you are human",
        "attention required",
        "security check to access",
        "cloudflare ray id",
        "turnstile verification"
    )

    private val AUTH_PATTERNS = listOf(
        "sign in to continue",
        "login to read",
        "members only",
        "you need to be logged in",
        "subscription required",
        "content locked",
        "please log in to access"
    )

    /**
     * Validates sanitized chapter HTML against structural quality rules,
     * link density, and challenge/auth detection.
     */
    fun validate(
        sanitizedHtml: String,
        docTitle: String? = null,
        fullDoc: Document? = null,
        rule: ContentValidationRule? = null
    ): ContentValidationResult {
        if (sanitizedHtml.isBlank()) {
            return ContentValidationResult(
                valid = false,
                reason = ExtractionFailureReason.NO_CONTENT,
                characterCount = 0,
                paragraphCount = 0,
                linkDensity = 0.0,
                matchedRejection = "HTML content is blank"
            )
        }

        val fragment = Jsoup.parseBodyFragment(sanitizedHtml)
        val visibleText = fragment.text().trim()
        val charCount = visibleText.length

        // Paragraph count calculation
        val blockElements = fragment.select("p, div, li, blockquote, h1, h2, h3, h4, h5, h6")
        val nonEmptyBlocks = blockElements.count { it.text().trim().isNotEmpty() }
        val paragraphCount = if (nonEmptyBlocks > 0) {
            nonEmptyBlocks
        } else {
            // Fallback for flat text separated by newlines
            visibleText.split("\n").count { it.trim().isNotEmpty() }.coerceAtLeast(if (charCount > 0) 1 else 0)
        }

        // Link density calculation (link text length / total visible text length)
        val linkTextLength = fragment.select("a").sumOf { it.text().trim().length }
        val linkDensity = if (charCount > 0) {
            linkTextLength.toDouble() / charCount.toDouble()
        } else {
            1.0
        }

        val lowerVisible = visibleText.lowercase()
        val lowerDocTitle = (docTitle ?: fullDoc?.title() ?: "").lowercase()

        // 1. Challenge page detection
        for (pattern in CHALLENGE_PATTERNS) {
            if (lowerDocTitle.contains(pattern) || (charCount < 1000 && lowerVisible.contains(pattern))) {
                return ContentValidationResult(
                    valid = false,
                    reason = ExtractionFailureReason.CHALLENGE,
                    characterCount = charCount,
                    paragraphCount = paragraphCount,
                    linkDensity = linkDensity,
                    matchedRejection = "Challenge detected: '$pattern'"
                )
            }
        }

        // 2. Auth / paywall detection
        for (pattern in AUTH_PATTERNS) {
            if (lowerDocTitle.contains(pattern) || (charCount < 1000 && lowerVisible.contains(pattern))) {
                return ContentValidationResult(
                    valid = false,
                    reason = ExtractionFailureReason.AUTH_REQUIRED,
                    characterCount = charCount,
                    paragraphCount = paragraphCount,
                    linkDensity = linkDensity,
                    matchedRejection = "Authentication required: '$pattern'"
                )
            }
        }

        // 3. Custom rejection patterns
        val customPatterns = rule?.rejectTitlePatterns ?: emptyList()
        for (pattern in customPatterns) {
            val lowerPat = pattern.lowercase().trim()
            if (lowerPat.isNotEmpty()) {
                if (lowerDocTitle.contains(lowerPat) || lowerVisible.contains(lowerPat)) {
                    return ContentValidationResult(
                        valid = false,
                        reason = ExtractionFailureReason.CONTENT_INVALID,
                        characterCount = charCount,
                        paragraphCount = paragraphCount,
                        linkDensity = linkDensity,
                        matchedRejection = "Custom reject pattern matched: '$pattern'"
                    )
                }
            }
        }

        // 4. Minimum character count check
        val minChars = rule?.minTextCharacters ?: 100
        if (charCount < minChars) {
            return ContentValidationResult(
                valid = false,
                reason = if (charCount == 0) ExtractionFailureReason.NO_CONTENT else ExtractionFailureReason.CONTENT_INVALID,
                characterCount = charCount,
                paragraphCount = paragraphCount,
                linkDensity = linkDensity,
                matchedRejection = "Visible character count ($charCount) below minimum ($minChars)"
            )
        }

        // 5. Minimum paragraph count check
        val minParagraphs = rule?.minParagraphs ?: 1
        if (paragraphCount < minParagraphs) {
            return ContentValidationResult(
                valid = false,
                reason = ExtractionFailureReason.CONTENT_INVALID,
                characterCount = charCount,
                paragraphCount = paragraphCount,
                linkDensity = linkDensity,
                matchedRejection = "Paragraph count ($paragraphCount) below minimum ($minParagraphs)"
            )
        }

        // 6. Maximum link density check
        val maxDensity = rule?.maxLinkDensity ?: 0.50
        if (linkDensity > maxDensity) {
            return ContentValidationResult(
                valid = false,
                reason = ExtractionFailureReason.CONTENT_INVALID,
                characterCount = charCount,
                paragraphCount = paragraphCount,
                linkDensity = linkDensity,
                matchedRejection = "Link density ($linkDensity) exceeds maximum allowed ($maxDensity)"
            )
        }

        return ContentValidationResult(
            valid = true,
            reason = null,
            characterCount = charCount,
            paragraphCount = paragraphCount,
            linkDensity = linkDensity
        )
    }
}
