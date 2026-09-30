package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ChapterRule
import com.drnishanth.novellib.scraping.models.ChaptersRules
import com.drnishanth.novellib.scraping.models.NovelRules
import com.drnishanth.novellib.scraping.models.RequestConfig
import com.drnishanth.novellib.scraping.models.SanitizeRule
import com.drnishanth.novellib.scraping.models.SelectorRule
import com.drnishanth.novellib.scraping.models.SourceDefinition
import com.drnishanth.novellib.scraping.models.SourceMatch

object DefaultSourceDefinitions {
    val ROYAL_ROAD = SourceDefinition(
        id = "royalroad",
        version = 1,
        name = "Royal Road",
        description = "Royal Road online web fiction platform",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("royalroad.com", "www.royalroad.com"),
            urlPatterns = listOf(
                "https://www.royalroad.com/fiction/*",
                "https://royalroad.com/fiction/*"
            )
        ),
        requests = mapOf(
            "default" to RequestConfig(
                method = "GET",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 NovelLibrary/0.1"
                )
            )
        ),
        novel = NovelRules(
            title = SelectorRule(
                selector = "h1",
                type = "text",
                transform = listOf("trim")
            ),
            author = SelectorRule(
                selector = "h4 a",
                type = "text",
                transform = listOf("trim")
            ),
            description = SelectorRule(
                selector = ".description .description-content",
                type = "html",
                transform = listOf("trim")
            ),
            cover = SelectorRule(
                selector = ".fiche-header img",
                type = "attribute",
                attribute = "src",
                transform = listOf("absolute_url")
            ),
            status = SelectorRule(
                selector = ".fiction-info .label",
                type = "text",
                transform = listOf("trim")
            )
        ),
        chapters = ChaptersRules(
            container = "#chapters tbody tr",
            title = SelectorRule(
                selector = "td:first-child a",
                type = "text",
                transform = listOf("trim")
            ),
            url = SelectorRule(
                selector = "td:first-child a",
                type = "attribute",
                attribute = "href",
                transform = listOf("absolute_url")
            ),
            publishedAt = SelectorRule(
                selector = "time",
                type = "attribute",
                attribute = "unixtimestamp"
            )
        ),
        chapter = ChapterRule(
            content = SelectorRule(
                selector = ".chapter-content",
                type = "html"
            ),
            sanitize = SanitizeRule(
                removeTags = listOf("script", "style", "iframe", "button", "div.author-note-portlet"),
                allowTags = listOf("p", "br", "b", "i", "em", "strong", "h1", "h2", "h3", "blockquote")
            )
        )
    )

    val BUILTIN_DEFINITIONS: List<SourceDefinition> = listOf(ROYAL_ROAD)
}
