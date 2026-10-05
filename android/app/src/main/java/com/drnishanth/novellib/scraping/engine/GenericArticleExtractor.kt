package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ContentValidationRule
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object GenericArticleExtractor {

    private val CANDIDATE_SELECTORS = listOf(
        "article",
        "main article",
        "[role='main']",
        "main",
        "[class*='chapter-content'], [id*='chapter-content']",
        "[class*='entry-content'], [id*='entry-content']",
        "[class*='post-content'], [id*='post-content']",
        "[class*='reading-content'], [id*='reading-content']",
        "[class*='chapter-text'], [id*='chapter-text']",
        "[class*='reader-content'], [id*='reader-content']",
        "[class*='read-content'], [id*='read-content']",
        "[class*='chapter-body'], [id*='chapter-body']",
        "[class*='text-content'], [id*='text-content']",
        "#chapter-inner, .chapter-inner",
        "#content, .content"
    )

    private val POSITIVE_HINTS = listOf("chapter", "entry", "post", "content", "reading", "story", "reader", "text")
    private val NEGATIVE_HINTS = listOf("comment", "nav", "sidebar", "footer", "header", "ad", "social", "share", "author", "recommend", "related", "widget", "menu")

    /**
     * Attempts to locate the main readable article/chapter content from an arbitrary HTML document
     * when configured source selectors fail.
     */
    fun extractArticle(
        doc: Document,
        validationRule: ContentValidationRule? = null,
        sanitizeTagRemoval: List<String> = listOf("script", "style", "iframe", "button")
    ): String? {
        val candidates = mutableSetOf<Element>()

        for (selector in CANDIDATE_SELECTORS) {
            val elements = doc.select(selector)
            for (el in elements) {
                if (el.tagName().lowercase() != "body" && el.tagName().lowercase() != "html") {
                    candidates.add(el)
                }
            }
        }

        // Also inspect div elements with multiple paragraphs
        val divsWithP = doc.select("div:has(> p:nth-of-type(3))")
        for (el in divsWithP) {
            if (el.tagName().lowercase() != "body") {
                candidates.add(el)
            }
        }

        if (candidates.isEmpty()) {
            return null
        }

        val minChars = validationRule?.minTextCharacters ?: 200
        val maxLinkDensity = validationRule?.maxLinkDensity ?: 0.50

        // Score each candidate
        val scoredCandidates = candidates.mapNotNull { element ->
            val text = element.text().trim()
            val textLength = text.length
            if (textLength < minChars) return@mapNotNull null

            val linkTextLength = element.select("a").sumOf { it.text().trim().length }
            val linkRatio = linkTextLength.toDouble() / textLength.toDouble()
            if (linkRatio > maxLinkDensity) return@mapNotNull null

            val pCount = element.select("p").size
            val tagInfo = (element.className() + " " + element.id()).lowercase()

            var score = textLength.toDouble() + (pCount * 50.0)

            // Positive bonus
            for (hint in POSITIVE_HINTS) {
                if (tagInfo.contains(hint)) score += 200.0
            }

            // Negative penalty
            for (neg in NEGATIVE_HINTS) {
                if (tagInfo.contains(neg)) score -= 400.0
            }

            // Link density penalty
            score -= (linkRatio * 1000.0)

            Pair(element, score)
        }.sortedByDescending { it.second }

        val docTitle = doc.title()

        for ((element, _) in scoredCandidates) {
            // Clone element to sanitize locally before validating
            val clone = element.clone()
            for (tag in sanitizeTagRemoval) {
                clone.select(tag).remove()
            }
            val html = clone.html()
            val validation = ChapterContentValidator.validate(
                sanitizedHtml = html,
                docTitle = docTitle,
                fullDoc = doc,
                rule = validationRule
            )
            if (validation.valid) {
                return html
            }
        }

        return null
    }
}
