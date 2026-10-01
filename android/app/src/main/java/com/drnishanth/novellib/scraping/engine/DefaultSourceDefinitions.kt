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

    val SCRIBBLE_HUB = SourceDefinition(
        id = "scribblehub",
        version = 1,
        name = "Scribble Hub",
        description = "Original web stories and community fiction",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("scribblehub.com", "www.scribblehub.com"),
            urlPatterns = listOf(
                "https://www.scribblehub.com/series/*",
                "https://scribblehub.com/series/*",
                "https://www.scribblehub.com/read/*",
                "https://scribblehub.com/read/*"
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
                selector = "h1.fic_title",
                type = "text",
                transform = listOf("trim")
            ),
            author = SelectorRule(
                selector = "span.auth_name_fic a",
                type = "text",
                transform = listOf("trim")
            ),
            description = SelectorRule(
                selector = ".wi_fic_desc",
                type = "html",
                transform = listOf("trim")
            ),
            cover = SelectorRule(
                selector = ".fic_image img",
                type = "attribute",
                attribute = "src",
                transform = listOf("absolute_url")
            ),
            status = SelectorRule(
                selector = "span.rnd_status",
                type = "text",
                transform = listOf("trim")
            )
        ),
        chapters = ChaptersRules(
            container = "ul.toc_w li.toc_li",
            title = SelectorRule(
                selector = "a.toc_a",
                type = "text",
                transform = listOf("trim")
            ),
            url = SelectorRule(
                selector = "a.toc_a",
                type = "attribute",
                attribute = "href",
                transform = listOf("absolute_url")
            ),
            publishedAt = SelectorRule(
                selector = "span.fic_date_pub",
                type = "text",
                transform = listOf("trim")
            )
        ),
        chapter = ChapterRule(
            content = SelectorRule(
                selector = "div.chp_raw",
                type = "html"
            ),
            sanitize = SanitizeRule(
                removeTags = listOf("script", "style", "iframe", "button"),
                allowTags = listOf("p", "br", "b", "i", "em", "strong", "h1", "h2", "h3", "blockquote")
            )
        )
    )

    val NOVGO = SourceDefinition(
        id = "novgo",
        version = 2,
        name = "NovGo",
        description = "NovGo online free web novels and light fiction",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("novgo.net", "www.novgo.net"),
            urlPatterns = listOf(
                "https://novgo.net/*.html",
                "https://www.novgo.net/*.html",
                "https://novgo.net/*",
                "https://www.novgo.net/*"
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
                selector = "h1.tit, .m-desc h1, .g-tit .tit",
                type = "text",
                transform = listOf("trim")
            ),
            author = SelectorRule(
                selector = ".m-imgtxt .txt .item a[href*='/author/']",
                type = "text",
                transform = listOf("trim")
            ),
            description = SelectorRule(
                selector = "#novel-summary-inner, .m-desc .txt .inner, .m-desc .txt",
                type = "html",
                transform = listOf("trim")
            ),
            cover = SelectorRule(
                selector = ".m-imgtxt .pic img",
                type = "attribute",
                attribute = "src",
                transform = listOf("absolute_url")
            ),
            status = SelectorRule(
                selector = ".m-imgtxt .item span[title='Status'] + .right a, .m-imgtxt .s1.s2 a",
                type = "text",
                transform = listOf("trim")
            )
        ),
        chapters = ChaptersRules(
            container = "#idData li",
            pagination = SelectorRule(
                selector = "#indexselect option",
                type = "attribute",
                attribute = "data-url",
                transform = listOf("absolute_url")
            ),
            title = SelectorRule(
                selector = "a.con, a",
                type = "text",
                transform = listOf("trim", "decode_html")
            ),
            url = SelectorRule(
                selector = "a.con, a",
                type = "attribute",
                attribute = "href",
                transform = listOf("absolute_url")
            )
        ),
        chapter = ChapterRule(
            content = SelectorRule(
                selector = "#chapter-content",
                type = "html"
            ),
            sanitize = SanitizeRule(
                removeTags = listOf("script", "style", "iframe", "button", "div.ads", "div.ad", "ins"),
                allowTags = listOf("p", "br", "b", "i", "em", "strong", "h1", "h2", "h3", "blockquote")
            )
        )
    )

    val BUILTIN_DEFINITIONS: List<SourceDefinition> = listOf(ROYAL_ROAD, SCRIBBLE_HUB, NOVGO)
}
