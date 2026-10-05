package com.drnishanth.novellib.scraping.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SourceDefinition(
    val id: String,
    val version: Int,
    val name: String,
    val description: String? = null,
    @SerialName("minimum_engine_version")
    val minimumEngineVersion: Int = 1,
    val match: SourceMatch,
    val requests: Map<String, RequestConfig> = emptyMap(),
    val rendering: RenderingRule? = null,
    val novel: NovelRules,
    val chapters: ChaptersRules,
    val chapter: ChapterRule
)

@Serializable
data class RenderingRule(
    val mode: String = "auto",
    @SerialName("ready_selector") val readySelector: String? = null,
    @SerialName("min_text_characters") val minTextCharacters: Int = 300,
    @SerialName("max_wait_ms") val maxWaitMs: Long = 12_000L,
    @SerialName("scroll_until_stable") val scrollUntilStable: Boolean = false
)

@Serializable
data class ContentValidationRule(
    @SerialName("min_text_characters") val minTextCharacters: Int = 300,
    @SerialName("min_paragraphs") val minParagraphs: Int = 2,
    @SerialName("max_link_density") val maxLinkDensity: Double = 0.50,
    @SerialName("reject_title_patterns") val rejectTitlePatterns: List<String> = emptyList()
)

@Serializable
data class SourceMatch(
    val hosts: List<String> = emptyList(),
    @SerialName("url_patterns")
    val urlPatterns: List<String> = emptyList(),
    @SerialName("novel_url_pattern")
    val novelUrlPattern: String? = null
)

@Serializable
data class RequestConfig(
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class SelectorRule(
    val selector: String,
    val type: String = "text", // text, html, attribute
    val attribute: String? = null,
    val transform: List<String> = emptyList(),
    val replace: Map<String, String> = emptyMap(),
    @SerialName("regex_pattern")
    val regexPattern: String? = null,
    @SerialName("regex_replacement")
    val regexReplacement: String? = null
)

@Serializable
data class TagSelectorRule(
    val selector: String,
    val attribute: String? = null,
    val transform: String? = null
)

@Serializable
data class NovelRules(
    val title: SelectorRule,
    val author: SelectorRule? = null,
    val description: SelectorRule? = null,
    val cover: SelectorRule? = null,
    val status: SelectorRule? = null,
    val tags: TagSelectorRule? = null,
    @SerialName("contentWarnings")
    val contentWarnings: TagSelectorRule? = null
)

@Serializable
data class ChaptersRules(
    val container: String,
    val title: SelectorRule,
    val url: SelectorRule,
    @SerialName("published_at")
    val publishedAt: SelectorRule? = null,
    val pagination: SelectorRule? = null,
    val order: String? = null
)

@Serializable
data class ChapterRule(
    val content: SelectorRule,
    @SerialName("content_selectors")
    val contentSelectors: List<SelectorRule> = emptyList(),
    val validation: ContentValidationRule? = null,
    val sanitize: SanitizeRule? = null
)

@Serializable
data class SanitizeRule(
    @SerialName("remove_tags")
    val removeTags: List<String> = listOf("script", "style", "iframe", "button"),
    @SerialName("allow_tags")
    val allowTags: List<String> = emptyList()
)

data class ScrapedNovel(
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String?,
    val status: String,
    val sourceUrl: String,
    val tags: List<String> = emptyList(),
    val contentWarnings: List<String> = emptyList()
)

data class ScrapedChapterItem(
    val number: Int,
    val title: String,
    val url: String,
    val publishedAt: Long? = null
)

data class ScrapedChapterContent(
    val title: String?,
    val htmlContent: String,
    val contentHash: String
)

enum class FetchMode { HTTP_ONLY, AUTO, WEBVIEW_ONLY }

enum class ExtractionFailureReason {
    NETWORK,
    HTTP_ERROR,
    AUTH_REQUIRED,
    CHALLENGE,
    NO_CONTENT,
    CONTENT_INVALID,
    TIMEOUT
}

enum class FetchMethod { HTTP, WEBVIEW, READER_MODE }

data class FetchedDocument(
    val document: org.jsoup.nodes.Document,
    val finalUrl: String,
    val fetchMethod: FetchMethod,
    val elapsedMs: Long
)

data class ContentValidationResult(
    val valid: Boolean,
    val reason: ExtractionFailureReason? = null,
    val characterCount: Int = 0,
    val paragraphCount: Int = 0,
    val linkDensity: Double = 0.0,
    val matchedRejection: String? = null
)

sealed interface ChapterExtractionResult {
    data class Success(
        val content: ScrapedChapterContent,
        val fetchMethod: FetchMethod = FetchMethod.HTTP,
        val usedFallback: Boolean = false
    ) : ChapterExtractionResult

    data class Failure(
        val reason: ExtractionFailureReason,
        val message: String,
        val fetchMethod: FetchMethod? = null,
        val validationResult: ContentValidationResult? = null
    ) : ChapterExtractionResult
}

class ChapterExtractionException(
    val reason: ExtractionFailureReason,
    override val message: String,
    val fetchMethod: FetchMethod? = null,
    val validationResult: ContentValidationResult? = null
) : Exception(message)
