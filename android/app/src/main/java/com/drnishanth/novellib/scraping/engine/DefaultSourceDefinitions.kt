package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ChapterRule
import com.drnishanth.novellib.scraping.models.ChaptersRules
import com.drnishanth.novellib.scraping.models.NovelRules
import com.drnishanth.novellib.scraping.models.RequestConfig
import com.drnishanth.novellib.scraping.models.SanitizeRule
import com.drnishanth.novellib.scraping.models.SelectorRule
import com.drnishanth.novellib.scraping.models.SourceDefinition
import com.drnishanth.novellib.scraping.models.SourceMatch
import com.drnishanth.novellib.scraping.models.TagSelectorRule

object DefaultSourceDefinitions {
    val ROYAL_ROAD = SourceDefinition(
        id = "royalroad",
        version = 3,
        name = "Royal Road",
        description = "Royal Road online web fiction platform",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("royalroad.com", "www.royalroad.com"),
            urlPatterns = listOf(
                "https://www.royalroad.com/fiction/*",
                "https://royalroad.com/fiction/*"
            ),
            novelUrlPattern = "https://www.royalroad.com/fiction/*"
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
                selector = "img[data-type='cover'], .cover-art-container img, .cover-col img, .thumbnail[data-type='cover'], img.thumbnail",
                type = "attribute",
                attribute = "src",
                transform = listOf("absolute_url")
            ),
            status = SelectorRule(
                selector = ".fiction-info .label",
                type = "text",
                transform = listOf("trim")
            ),
            tags = TagSelectorRule(
                selector = "span.tags a.fiction-tag, span.tags a",
                attribute = "text",
                transform = "trim"
            ),
            contentWarnings = TagSelectorRule(
                selector = ".font-red-sunglo ul.list-inline li, ul.list-inline li",
                attribute = "text",
                transform = "trim"
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
        version = 2,
        name = "Scribble Hub",
        description = "Original web stories, community fiction, and serialized light novels",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("scribblehub.com", "www.scribblehub.com"),
            urlPatterns = listOf(
                "https://www.scribblehub.com/series/*",
                "https://scribblehub.com/series/*",
                "https://www.scribblehub.com/read/*",
                "https://scribblehub.com/read/*"
            ),
            novelUrlPattern = "https://www.scribblehub.com/series/*"
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
                selector = "h1.fic_title, .fic_title",
                type = "text",
                transform = listOf("trim")
            ),
            author = SelectorRule(
                selector = "span.auth_name_fic a, span.auth_name_fic",
                type = "text",
                transform = listOf("trim")
            ),
            description = SelectorRule(
                selector = ".wi_fic_desc, .fic_desc",
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
            ),
            tags = TagSelectorRule(
                selector = ".fic_genre, .fic_genre a, .stag, .stag a, a.stag",
                attribute = "text",
                transform = "trim"
            ),
            contentWarnings = TagSelectorRule(
                selector = ".raw_warning, .raw_warning a, .content_warning a, .warning_list li",
                attribute = "text",
                transform = "trim"
            )
        ),
        chapters = ChaptersRules(
            container = "ul.toc_w li.toc_li, .toc_li, ul.toc_w li",
            title = SelectorRule(
                selector = "a.toc_a, a",
                type = "text",
                transform = listOf("trim")
            ),
            url = SelectorRule(
                selector = "a.toc_a, a",
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
                selector = "div.chp_raw, .chp_raw, #chp_raw",
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
            ),
            novelUrlPattern = "https://novgo.net/*.html"
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
            ),
            tags = TagSelectorRule(
                selector = ".m-imgtxt .item span[title='Genre'] + .right a, .m-imgtxt .item span.glyphicon-th-list + .right a, a[href*='/genre/']",
                attribute = "text",
                transform = "trim"
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

    val LITFIC = SourceDefinition(
        id = "litfic",
        version = 2,
        name = "LitFic",
        description = "Serialized modern fiction, web novels, and original community stories",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("litfic.com", "www.litfic.com"),
            urlPatterns = listOf(
                "https://litfic.com/series/*",
                "https://www.litfic.com/series/*",
                "https://litfic.com/browse*",
                "https://www.litfic.com/browse*"
            ),
            novelUrlPattern = "https://litfic.com/series/*"
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
                selector = "main h1, h1, meta[property='og:title']",
                type = "text",
                transform = listOf("trim")
            ),
            author = SelectorRule(
                selector = "a[href*='/profile/'], a[href*='/author/'], .text-flame",
                type = "text",
                transform = listOf("trim")
            ),
            description = SelectorRule(
                selector = ".prose, .reader-content, p",
                type = "html",
                transform = listOf("trim")
            ),
            cover = SelectorRule(
                selector = "img[src*='covers'], img[src*='firebasestorage'], .relative img, img",
                type = "attribute",
                attribute = "src",
                transform = listOf("absolute_url")
            ),
            status = SelectorRule(
                selector = ".badge, span[class*='status']",
                type = "text",
                transform = listOf("trim")
            ),
            tags = TagSelectorRule(
                selector = "a[href*='/browse?'], a[href*='/genres/'], .badge, span.tag",
                attribute = "text",
                transform = "trim"
            ),
            contentWarnings = TagSelectorRule(
                selector = "span[class*='warning'], .text-red-500",
                attribute = "text",
                transform = "trim"
            )
        ),
        chapters = ChaptersRules(
            container = "a[href*='/series/'], div a[href*='/series/']",
            title = SelectorRule(
                selector = "span, p, a",
                type = "text",
                transform = listOf("trim")
            ),
            url = SelectorRule(
                selector = "a, this",
                type = "attribute",
                attribute = "href",
                transform = listOf("absolute_url")
            )
        ),
        chapter = ChapterRule(
            content = SelectorRule(
                selector = "div.reader-content, article, div[class*='reader']",
                type = "html"
            ),
            sanitize = SanitizeRule(
                removeTags = listOf("script", "style", "iframe", "button"),
                allowTags = listOf("p", "br", "b", "i", "em", "strong", "h1", "h2", "h3", "blockquote")
            )
        )
    )

    val TAPAS = SourceDefinition(
        id = "tapas",
        version = 1,
        name = "Tapas",
        description = "Web novels, serialized community books, and light fiction",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("tapas.io", "www.tapas.io"),
            urlPatterns = listOf(
                "https://tapas.io/series/*",
                "https://www.tapas.io/series/*",
                "https://tapas.io/episode/*",
                "https://www.tapas.io/episode/*"
            ),
            novelUrlPattern = "https://tapas.io/series/*"
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
                selector = "h1.title, .title, a.title, .series-header__title, h1",
                type = "text",
                transform = listOf("trim")
            ),
            author = SelectorRule(
                selector = "a.creator, .creator__name, a[href*='/creator/'], .creator",
                type = "text",
                transform = listOf("trim")
            ),
            description = SelectorRule(
                selector = ".description, .content__body, p.desc, meta[property='og:description']",
                type = "html",
                transform = listOf("trim")
            ),
            cover = SelectorRule(
                selector = "img.thumb, .thumb img, meta[property='og:image'], img",
                type = "attribute",
                attribute = "src",
                transform = listOf("absolute_url")
            ),
            status = SelectorRule(
                selector = ".status, .badge",
                type = "text",
                transform = listOf("trim")
            ),
            tags = TagSelectorRule(
                selector = "a.tag, .tag, a[href*='/genres/'], .genre, .sub-genre",
                attribute = "text",
                transform = "trim"
            ),
            contentWarnings = TagSelectorRule(
                selector = ".warning, .badge--warning",
                attribute = "text",
                transform = "trim"
            )
        ),
        chapters = ChaptersRules(
            container = "ul.episode-list li, .episode-list li, li.episode",
            title = SelectorRule(
                selector = "a.item__title, .episode-title, a",
                type = "text",
                transform = listOf("trim")
            ),
            url = SelectorRule(
                selector = "a",
                type = "attribute",
                attribute = "href",
                transform = listOf("absolute_url")
            )
        ),
        chapter = ChapterRule(
            content = SelectorRule(
                selector = "article.viewer__body, div.viewer__body, .content__body, article",
                type = "html"
            ),
            sanitize = SanitizeRule(
                removeTags = listOf("script", "style", "iframe", "button"),
                allowTags = listOf("p", "br", "b", "i", "em", "strong", "h1", "h2", "h3", "blockquote")
            )
        )
    )

    val NOVEL_UPDATES = SourceDefinition(
        id = "novelupdates",
        version = 1,
        name = "Novel Updates",
        description = "Directory, tracker, and reading index for Asian translated web novels",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("novelupdates.com", "www.novelupdates.com"),
            urlPatterns = listOf(
                "https://www.novelupdates.com/series/*",
                "https://novelupdates.com/series/*"
            ),
            novelUrlPattern = "https://www.novelupdates.com/series/*"
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
                selector = ".seriestitlenine, h1.seriestitlenine, h1",
                type = "text",
                transform = listOf("trim")
            ),
            author = SelectorRule(
                selector = "#authtag, #showauthors a, #showauthors",
                type = "text",
                transform = listOf("trim")
            ),
            description = SelectorRule(
                selector = "#editdescription, .seriesdesc, #editdescription p",
                type = "html",
                transform = listOf("trim")
            ),
            cover = SelectorRule(
                selector = ".seriesimg img, .seriesimg",
                type = "attribute",
                attribute = "src",
                transform = listOf("absolute_url")
            ),
            status = SelectorRule(
                selector = "#editstatus, .status",
                type = "text",
                transform = listOf("trim")
            ),
            tags = TagSelectorRule(
                selector = "#showtags a, #showgenre a, a.genre, a.genretag",
                attribute = "text",
                transform = "trim"
            ),
            contentWarnings = TagSelectorRule(
                selector = "#showwarning a, .warning, .contentwarning",
                attribute = "text",
                transform = "trim"
            )
        ),
        chapters = ChaptersRules(
            container = "table#myTable tbody tr, #myTable tr, ul.sp-toc li, a.chp_a",
            title = SelectorRule(
                selector = "a",
                type = "text",
                transform = listOf("trim")
            ),
            url = SelectorRule(
                selector = "a",
                type = "attribute",
                attribute = "href",
                transform = listOf("absolute_url")
            )
        ),
        chapter = ChapterRule(
            content = SelectorRule(
                selector = "div.entry-content, article, div#content, .chapter-content",
                type = "html"
            ),
            sanitize = SanitizeRule(
                removeTags = listOf("script", "style", "iframe", "button"),
                allowTags = listOf("p", "br", "b", "i", "em", "strong", "h1", "h2", "h3", "blockquote")
            )
        )
    )

    val BUILTIN_DEFINITIONS: List<SourceDefinition> = listOf(
        ROYAL_ROAD,
        SCRIBBLE_HUB,
        NOVGO,
        LITFIC,
        TAPAS,
        NOVEL_UPDATES
    )
}
