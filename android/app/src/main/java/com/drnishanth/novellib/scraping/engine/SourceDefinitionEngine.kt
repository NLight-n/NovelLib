package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.core.utils.HtmlSanitizer
import com.drnishanth.novellib.scraping.models.ChapterRule
import com.drnishanth.novellib.scraping.models.RequestConfig
import com.drnishanth.novellib.scraping.models.ScrapedChapterContent
import com.drnishanth.novellib.scraping.models.ScrapedChapterItem
import com.drnishanth.novellib.scraping.models.ScrapedNovel
import com.drnishanth.novellib.scraping.models.SelectorRule
import com.drnishanth.novellib.scraping.models.SourceDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SourceDefinitionEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    /**
     * Determines whether a given URL matches a source definition.
     */
    fun matches(url: String, definition: SourceDefinition): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false

            val hostMatches = definition.match.hosts.any { pattern ->
                host == pattern.lowercase() || host.endsWith("." + pattern.lowercase())
            }
            if (hostMatches) return true

            definition.match.urlPatterns.any { pattern ->
                val regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .toRegex(RegexOption.IGNORE_CASE)
                regex.matches(url)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches novel metadata and chapter list from a novel landing page.
     */
    suspend fun scrapeNovel(
        url: String,
        definition: SourceDefinition
    ): Pair<ScrapedNovel, List<ScrapedChapterItem>> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(url, definition.requests["default"])

        // Extract Novel metadata
        val rawTitle = extractValue(doc, definition.novel.title, url)
            ?: throw IllegalStateException("Novel title could not be extracted from $url")
        val rawAuthor = definition.novel.author?.let { extractValue(doc, it, url) } ?: "Unknown"
        val rawDescription = definition.novel.description?.let { extractValue(doc, it, url) } ?: ""
        val rawCoverUrl = definition.novel.cover?.let { extractValue(doc, it, url) }
        val coverUrl = if (!rawCoverUrl.isNullOrBlank()) {
            rawCoverUrl
        } else {
            val og = doc.selectFirst("meta[property='og:image'], meta[name='twitter:image'], meta[property='twitter:image']")?.attr("content")?.trim()
            if (!og.isNullOrBlank()) {
                resolveAbsoluteUrl(og, url)
            } else {
                null
            }
        }
        val status = definition.novel.status?.let { extractValue(doc, it, url) } ?: "ONGOING"

        val novel = ScrapedNovel(
            title = HtmlSanitizer.cleanTitle(rawTitle),
            author = HtmlSanitizer.cleanTitle(rawAuthor),
            description = HtmlSanitizer.cleanHtmlSynopsis(rawDescription),
            coverUrl = coverUrl,
            status = status,
            sourceUrl = url
        )

        // Extract Chapters
        val chapters = mutableListOf<ScrapedChapterItem>()

        fun extractChaptersFromDoc(d: Document, pageBaseUrl: String) {
            val elements = d.select(definition.chapters.container)
            elements.forEach { element ->
                val rawChapTitle = extractFromElement(element, definition.chapters.title, pageBaseUrl)
                    ?: "Chapter ${chapters.size + 1}"
                val chapTitle = HtmlSanitizer.cleanTitle(rawChapTitle)
                val chapUrl = extractFromElement(element, definition.chapters.url, pageBaseUrl)

                if (!chapUrl.isNullOrBlank()) {
                    val pubAt = definition.chapters.publishedAt?.let {
                        extractFromElement(element, it, pageBaseUrl)?.toLongOrNull()
                    }
                    val parsedNumber = parseChapterNumber(chapTitle, chapUrl) ?: (chapters.size + 1)
                    chapters.add(
                        ScrapedChapterItem(
                            number = parsedNumber,
                            title = chapTitle,
                            url = chapUrl,
                            publishedAt = pubAt
                        )
                    )
                }
            }
        }

        extractChaptersFromDoc(doc, url)

        // If pagination rule exists, extract additional pages
        definition.chapters.pagination?.let { pagRule ->
            try {
                val pageOptions = doc.select(pagRule.selector)
                val directRule = pagRule.copy(selector = ".")
                val pageUrls = pageOptions.mapNotNull { opt ->
                    val rawPageUrl = extractFromElement(opt, directRule, url)
                    if (rawPageUrl != null &&
                        (rawPageUrl.startsWith("http://") || rawPageUrl.startsWith("https://")) &&
                        rawPageUrl != url
                    ) {
                        rawPageUrl
                    } else null
                }.distinct()

                for (pageUrl in pageUrls) {
                    try {
                        val pageDoc = fetchDocument(pageUrl, definition.requests["default"])
                        extractChaptersFromDoc(pageDoc, pageUrl)
                    } catch (e: Exception) {
                        // Skip page on failure
                    }
                }
            } catch (e: Exception) {
                // Ignore pagination errors
            }
        }

        // De-duplicate by chapter URL and sort if numbers are valid
        val distinctChapters = chapters.distinctBy { it.url }
        val finalChapters = if (distinctChapters.size > 1 && distinctChapters.all { it.number > 0 }) {
            val numbers = distinctChapters.map { it.number }
            if (numbers.distinct().size == distinctChapters.size) {
                distinctChapters.sortedBy { it.number }
            } else {
                distinctChapters.mapIndexed { idx, item -> item.copy(number = idx + 1) }
            }
        } else {
            distinctChapters.mapIndexed { idx, item -> item.copy(number = idx + 1) }
        }

        Pair(novel, finalChapters)
    }

    private fun parseChapterNumber(title: String, url: String): Int? {
        val titleRegex = Regex("""(?i)\b(?:chapter|ch\.?)\s*(\d+)""")
        val titleMatch = titleRegex.find(title)
        if (titleMatch != null) {
            return titleMatch.groupValues[1].toIntOrNull()
        }
        val urlRegex = Regex("""(?i)\bchapter-(\d+)\b""")
        val urlMatch = urlRegex.find(url)
        if (urlMatch != null) {
            return urlMatch.groupValues[1].toIntOrNull()
        }
        return null
    }

    /**
     * Fetches and normalizes chapter content.
     */
    suspend fun scrapeChapterContent(
        url: String,
        definition: SourceDefinition
    ): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val doc = fetchDocument(url, definition.requests["default"])
        val rawHtml = extractValue(doc, definition.chapter.content, url)
            ?: throw IllegalStateException("Chapter content could not be extracted from $url")

        val sanitizedHtml = sanitizeHtml(rawHtml, definition.chapter)
        val hash = sha256(sanitizedHtml)

        ScrapedChapterContent(
            title = null,
            htmlContent = sanitizedHtml,
            contentHash = hash
        )
    }

    private suspend fun fetchDocument(url: String, requestConfig: RequestConfig?): Document {
        val requestBuilder = Request.Builder().url(url)
        val headers = requestConfig?.headers ?: emptyMap()
        if (!headers.containsKey("User-Agent")) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 NovelLibrary/0.1"
            )
        }
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP request failed with code ${response.code} for $url")
        }

        val body = response.body?.string() ?: throw IllegalStateException("Empty response body from $url")
        return Jsoup.parse(body, url)
    }

    private fun extractValue(doc: Document, rule: SelectorRule, baseUrl: String): String? {
        val element = doc.selectFirst(rule.selector) ?: return null
        return extractFromElement(element, rule, baseUrl)
    }

    private fun extractFromElement(element: Element, rule: SelectorRule, baseUrl: String): String? {
        val targetElement = if (rule.selector.isEmpty() || rule.selector == ".") {
            element
        } else {
            element.selectFirst(rule.selector) ?: element
        }

        var value = when (rule.type.lowercase()) {
            "html" -> targetElement.html()
            "attribute" -> {
                val attr = rule.attribute ?: return null
                targetElement.attr(attr)
            }
            else -> targetElement.text()
        }

        // Apply transformations
        for (transform in rule.transform) {
            value = when (transform.lowercase()) {
                "trim" -> value.trim()
                "normalize_whitespace" -> value.replace("\\s+".toRegex(), " ")
                "decode_html" -> Parser.unescapeEntities(value, false)
                "absolute_url" -> resolveAbsoluteUrl(value, baseUrl)
                else -> value
            }
        }

        // Apply string replacements
        for ((target, replacement) in rule.replace) {
            value = value.replace(target, replacement)
        }

        // Apply regex replacements
        if (!rule.regexPattern.isNullOrEmpty() && rule.regexReplacement != null) {
            value = value.replace(rule.regexPattern.toRegex(), rule.regexReplacement)
        }

        return value
    }

    private fun sanitizeHtml(html: String, rule: ChapterRule): String {
        val fragment = Jsoup.parseBodyFragment(html)

        // Remove dangerous / unwanted tags
        val removeTags = rule.sanitize?.removeTags ?: listOf("script", "style", "iframe", "button")
        for (tag in removeTags) {
            fragment.select(tag).remove()
        }

        // Remove inline event handlers (onclick, onload, etc.)
        fragment.allElements.forEach { el ->
            val attrsToRemove = el.attributes().filter {
                it.key.startsWith("on", ignoreCase = true) || it.key.equals("style", ignoreCase = true)
            }
            attrsToRemove.forEach { el.removeAttr(it.key) }
        }

        return fragment.body().html()
    }

    private fun resolveAbsoluteUrl(relativeUrl: String, baseUrl: String): String {
        return try {
            val base = URI(baseUrl)
            base.resolve(relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
