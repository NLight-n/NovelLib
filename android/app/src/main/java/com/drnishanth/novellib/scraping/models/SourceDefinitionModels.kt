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
    val novel: NovelRules,
    val chapters: ChaptersRules,
    val chapter: ChapterRule
)

@Serializable
data class SourceMatch(
    val hosts: List<String> = emptyList(),
    @SerialName("url_patterns")
    val urlPatterns: List<String> = emptyList()
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
data class NovelRules(
    val title: SelectorRule,
    val author: SelectorRule? = null,
    val description: SelectorRule? = null,
    val cover: SelectorRule? = null,
    val status: SelectorRule? = null
)

@Serializable
data class ChaptersRules(
    val container: String,
    val title: SelectorRule,
    val url: SelectorRule,
    @SerialName("published_at")
    val publishedAt: SelectorRule? = null
)

@Serializable
data class ChapterRule(
    val content: SelectorRule,
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
    val sourceUrl: String
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
