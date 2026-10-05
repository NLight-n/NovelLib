package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ChapterRule
import com.drnishanth.novellib.scraping.models.ChaptersRules
import com.drnishanth.novellib.scraping.models.NovelRules
import com.drnishanth.novellib.scraping.models.SanitizeRule
import com.drnishanth.novellib.scraping.models.SelectorRule
import com.drnishanth.novellib.scraping.models.SourceDefinition
import com.drnishanth.novellib.scraping.models.SourceMatch
import com.drnishanth.novellib.scraping.models.TagSelectorRule
import com.drnishanth.novellib.scraping.models.ChapterExtractionResult
import com.drnishanth.novellib.scraping.models.ContentValidationRule
import com.drnishanth.novellib.scraping.models.ExtractionFailureReason
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDefinitionEngineTest {

    private val engine = SourceDefinitionEngine()

    private val sampleDefinition = SourceDefinition(
        id = "test-fiction",
        version = 1,
        name = "Test Fiction",
        minimumEngineVersion = 1,
        match = SourceMatch(
            hosts = listOf("testfiction.com", "www.testfiction.com"),
            urlPatterns = listOf("https://testfiction.com/novel/*"),
            novelUrlPattern = "https?://(?:www\\.)?testfiction\\.com/novel/[^/]+$"
        ),
        novel = NovelRules(
            title = SelectorRule(selector = "h1.title", type = "text", transform = listOf("trim")),
            author = SelectorRule(selector = "span.author", type = "text", transform = listOf("trim")),
            description = SelectorRule(selector = "div.synopsis", type = "html", transform = listOf("trim")),
            tags = TagSelectorRule(selector = ".tags .tag", attribute = "text", transform = "trim"),
            contentWarnings = TagSelectorRule(selector = ".warnings .warn", attribute = "text", transform = "trim")
        ),
        chapters = ChaptersRules(
            container = "ul.chapters li",
            title = SelectorRule(selector = "a", type = "text", transform = listOf("trim")),
            url = SelectorRule(selector = "a", type = "attribute", attribute = "href", transform = listOf("absolute_url"))
        ),
        chapter = ChapterRule(
            content = SelectorRule(selector = "div.chapter-text", type = "html"),
            sanitize = SanitizeRule(removeTags = listOf("script", "style", "button"))
        )
    )

    @Test
    fun testUrlMatching() {
        assertTrue(engine.matches("https://testfiction.com/novel/123", sampleDefinition))
        assertTrue(engine.matches("https://www.testfiction.com/novel/abc-xyz", sampleDefinition))
        assertFalse(engine.matches("https://anothersite.org/novel/123", sampleDefinition))
    }

    @Test
    fun testBuiltinRoyalRoadMatches() {
        val rr = DefaultSourceDefinitions.ROYAL_ROAD
        assertTrue(engine.matches("https://www.royalroad.com/fiction/21220/mother-of-learning", rr))
        assertTrue(engine.matches("https://royalroad.com/fiction/1234/test", rr))
        assertFalse(engine.matches("https://example.com/fiction/123", rr))
    }

    @Test
    fun testBuiltinNovGoMatches() {
        val novgo = DefaultSourceDefinitions.NOVGO
        assertTrue(engine.matches("https://novgo.net/level-eater.html", novgo))
        assertTrue(engine.matches("https://www.novgo.net/peerless-martial-god.html", novgo))
        assertTrue(engine.matches("https://novgo.net/level-eater/chapter-0.html", novgo))
        assertFalse(engine.matches("https://example.com/novel/123", novgo))
    }

    @Test
    fun testNovelUrlMatchingRejectingChapters() {
        val rr = DefaultSourceDefinitions.ROYAL_ROAD
        // Landing pages match
        assertTrue(engine.matchesNovelUrl("https://www.royalroad.com/fiction/21220/mother-of-learning", rr))
        assertTrue(engine.matchesNovelUrl("https://royalroad.com/fiction/12345/super-minion", rr))
        // Chapters do NOT match
        assertFalse(engine.matchesNovelUrl("https://www.royalroad.com/fiction/21220/mother-of-learning/chapter/32507/1-good-morning-brother", rr))
        assertFalse(engine.matchesNovelUrl("https://www.royalroad.com/fiction/12345/chapter/999/prologue", rr))
        // Author profile or general pages do NOT match
        assertFalse(engine.matchesNovelUrl("https://www.royalroad.com/profile/1234", rr))
        assertFalse(engine.matchesNovelUrl("https://www.royalroad.com/fictions/search", rr))

        val novgo = DefaultSourceDefinitions.NOVGO
        // Novel landing pages match
        assertTrue(engine.matchesNovelUrl("https://novgo.net/level-eater.html", novgo))
        assertTrue(engine.matchesNovelUrl("https://www.novgo.net/peerless-martial-god.html", novgo))
        // Chapters do NOT match
        assertFalse(engine.matchesNovelUrl("https://novgo.net/level-eater/chapter-0.html", novgo))
        assertFalse(engine.matchesNovelUrl("https://novgo.net/peerless-martial-god/chapter-100.html", novgo))
    }

    @Test
    fun testExtractMultipleValues() {
        val html = """
            <html>
              <body>
                <div class="tags">
                  <span class="tag">  Progression Fantasy  </span>
                  <span class="tag">Action</span>
                  <span class="tag">Progression Fantasy</span>
                  <span class="tag">LitRPG</span>
                </div>
                <div class="warnings">
                  <span class="warn">Graphic Violence</span>
                  <span class="warn">Profanity</span>
                </div>
                <ul class="links">
                  <li><a href="/category/adventure">Adventure</a></li>
                  <li><a href="/category/magic">Magic</a></li>
                </ul>
              </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com")

        // Test tags extraction (trimming + deduplicating)
        val tags = engine.extractMultipleValues(
            doc = doc,
            selector = ".tags .tag",
            attribute = "text",
            transform = "trim",
            baseUrl = "https://example.com"
        )
        assertEquals(listOf("Progression Fantasy", "Action", "LitRPG"), tags)

        // Test warnings extraction
        val warnings = engine.extractMultipleValues(
            doc = doc,
            selector = ".warnings .warn",
            attribute = "text",
            transform = "trim",
            baseUrl = "https://example.com"
        )
        assertEquals(listOf("Graphic Violence", "Profanity"), warnings)

        // Test attribute extraction with absolute_url transform
        val links = engine.extractMultipleValues(
            doc = doc,
            selector = ".links a",
            attribute = "href",
            transform = "absolute_url",
            baseUrl = "https://example.com"
        )
        assertEquals(listOf("https://example.com/category/adventure", "https://example.com/category/magic"), links)
    }

    @Test
    fun testBuiltinDefinitionsHaveTagRulesAndNovelUrlPatterns() {
        val rr = DefaultSourceDefinitions.ROYAL_ROAD
        assertNotNull(rr.match.novelUrlPattern)
        assertNotNull(rr.novel.tags)
        assertEquals("span.tags a.fiction-tag, span.tags a", rr.novel.tags?.selector)
        assertNotNull(rr.novel.contentWarnings)
        assertEquals(".font-red-sunglo ul.list-inline li, ul.list-inline li", rr.novel.contentWarnings?.selector)

        val novgo = DefaultSourceDefinitions.NOVGO
        assertNotNull(novgo.match.novelUrlPattern)
        assertNotNull(novgo.novel.tags)
        assertTrue(novgo.novel.tags?.selector?.contains(".m-imgtxt .item span[title='Genre'] + .right a") == true)

        val sh = DefaultSourceDefinitions.SCRIBBLE_HUB
        assertNotNull(sh.match.novelUrlPattern)
        assertNotNull(sh.novel.tags)
        assertNotNull(sh.novel.contentWarnings)
        assertTrue(engine.matchesNovelUrl("https://www.scribblehub.com/series/104322/tree-of-aeons/", sh))

        val litfic = DefaultSourceDefinitions.LITFIC
        assertNotNull(litfic.match.novelUrlPattern)
        assertNotNull(litfic.novel.tags)
        assertTrue(engine.matches("https://litfic.com/browse", litfic))
        assertTrue(engine.matchesNovelUrl("https://litfic.com/series/sample-series", litfic))

        val tapas = DefaultSourceDefinitions.TAPAS
        assertNotNull(tapas.match.novelUrlPattern)
        assertNotNull(tapas.novel.tags)
        assertTrue(engine.matchesNovelUrl("https://tapas.io/series/the-beginning-after-the-end", tapas))

        val nu = DefaultSourceDefinitions.NOVEL_UPDATES
        assertNotNull(nu.match.novelUrlPattern)
        assertNotNull(nu.novel.tags)
        assertTrue(engine.matchesNovelUrl("https://www.novelupdates.com/series/trash-of-the-counts-family/", nu))
    }

    @Test
    fun testLayeredExtractionPrimarySelector() = runBlocking {
        val html = """
            <html>
            <body>
                <h1>Chapter 1</h1>
                <div class="chapter-content">
                    <p>The dawn brought a crisp cool breeze through the high mountain pass, rustling the green pine needles above.</p>
                    <p>He tightened his cloak and picked up his staff, knowing that the journey to the ancient citadel would test every ounce of human endurance.</p>
                    <p>Ahead in the clearing, the stone markers pointed towards the winding path through the misty crags where dragons once nested.</p>
                    <p>Step by step, he climbed upward into the clouds with resolute courage.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = engine.extractChapter(
            url = "https://www.royalroad.com/fiction/123/novel/chapter/456",
            definition = DefaultSourceDefinitions.ROYAL_ROAD,
            preloadedHtml = html
        )

        assertTrue("Expected Success result, got $result", result is ChapterExtractionResult.Success)
        val success = result as ChapterExtractionResult.Success
        assertFalse("Primary selector must not be marked as fallback", success.usedFallback)
        assertTrue(success.content.htmlContent.contains("mountain pass"))
    }

    @Test
    fun testLayeredExtractionFallbackSelector() = runBlocking {
        // Redesigned site: .chapter-content is absent, but .chapter-inner fallback is present!
        val html = """
            <html>
            <body>
                <h1>Chapter 2</h1>
                <div class="chapter-inner">
                    <p>The dawn brought a crisp cool breeze through the high mountain pass, rustling the green pine needles above.</p>
                    <p>He tightened his cloak and picked up his staff, knowing that the journey to the ancient citadel would test every ounce of human endurance.</p>
                    <p>Ahead in the clearing, the stone markers pointed towards the winding path through the misty crags where dragons once nested.</p>
                    <p>Step by step, he climbed upward into the clouds with resolute courage.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = engine.extractChapter(
            url = "https://www.royalroad.com/fiction/123/novel/chapter/456",
            definition = DefaultSourceDefinitions.ROYAL_ROAD,
            preloadedHtml = html
        )

        assertTrue("Expected Success result, got $result", result is ChapterExtractionResult.Success)
        val success = result as ChapterExtractionResult.Success
        assertTrue("Expected usedFallback to be true when primary selector was absent", success.usedFallback)
        assertTrue(success.content.htmlContent.contains("mountain pass"))
    }

    @Test
    fun testLayeredExtractionGenericArticleFallback() = runBlocking {
        // Redesigned site: neither .chapter-content nor .chapter-inner exist! Only a standard <article> tag.
        val html = """
            <html>
            <head><title>Chapter 3 - The Ascent</title></head>
            <body>
                <header><nav>Navigation</nav></header>
                <article>
                    <p>The dawn brought a crisp cool breeze through the high mountain pass, rustling the green pine needles above.</p>
                    <p>He tightened his cloak and picked up his staff, knowing that the journey to the ancient citadel would test every ounce of human endurance.</p>
                    <p>Ahead in the clearing, the stone markers pointed towards the winding path through the misty crags where dragons once nested.</p>
                    <p>Step by step, he climbed upward into the clouds with resolute courage.</p>
                </article>
                <footer>Footer text</footer>
            </body>
            </html>
        """.trimIndent()

        val result = engine.extractChapter(
            url = "https://www.royalroad.com/fiction/123/novel/chapter/456",
            definition = DefaultSourceDefinitions.ROYAL_ROAD,
            preloadedHtml = html
        )

        assertTrue("Expected Success result, got $result", result is ChapterExtractionResult.Success)
        val success = result as ChapterExtractionResult.Success
        assertTrue("Generic article extractor fallback should be used", success.usedFallback)
        assertTrue(success.content.htmlContent.contains("citadel"))
    }

    @Test
    fun testExtractionRejectsChallengePage() = runBlocking {
        val challengeHtml = """
            <html>
            <head><title>Just a moment...</title></head>
            <body>
                <h1>Checking your browser before accessing royalroad.com.</h1>
                <p>This process is automatic. Please wait a few seconds.</p>
            </body>
            </html>
        """.trimIndent()

        val result = engine.extractChapter(
            url = "https://www.royalroad.com/fiction/123/novel/chapter/456",
            definition = DefaultSourceDefinitions.ROYAL_ROAD,
            preloadedHtml = challengeHtml
        )

        assertTrue(result is ChapterExtractionResult.Failure)
        val failure = result as ChapterExtractionResult.Failure
        assertEquals(ExtractionFailureReason.CHALLENGE, failure.reason)
    }

    @Test
    fun testRoyalRoadDescriptionExtraction() = runBlocking {
        val rrHtml = """
            <html>
            <head><title>Mother of Learning | Royal Road</title></head>
            <body>
                <h1>Mother of Learning</h1>
                <h4><a>nobody103</a></h4>
                <div class="fiction-info">
                    <div class="description">
                        <div class="hidden-content">
                            <p>Zorian is a teenage mage of humble birth and slightly above-average skill, attending his third year of education at Cyoria's magical academy.</p>
                            <p>Just as he is about to leave, something goes terribly wrong and he finds himself back at the start of the month.</p>
                        </div>
                    </div>
                </div>
                <table id="chapters">
                    <tbody>
                        <tr><td><a href="/fiction/21220/mother-of-learning/chapter/32507/1-good-morning-brother">1. Good Morning Brother</a></td></tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val (novel, chapters) = engine.scrapeNovel(
            url = "https://www.royalroad.com/fiction/21220/mother-of-learning",
            definition = DefaultSourceDefinitions.ROYAL_ROAD,
            preloadedHtml = rrHtml
        )

        assertEquals("Mother of Learning", novel.title)
        assertEquals("nobody103", novel.author)
        assertTrue("Description should contain Zorian synopsis", novel.description.contains("Zorian is a teenage mage"))
        assertEquals(1, chapters.size)
        assertEquals("1. Good Morning Brother", chapters[0].title)
    }

    @Test
    fun testRoyalRoadDescriptionMetaFallback() = runBlocking {
        val rrHtml = """
            <html>
            <head>
                <title>Sample Story | Royal Road</title>
                <meta property="og:description" content="A thrilling journey into uncharted lands." />
            </head>
            <body>
                <h1>Sample Story</h1>
                <h4><a>Author Name</a></h4>
                <table id="chapters">
                    <tbody>
                        <tr><td><a href="/fiction/999/sample/chapter/1">Chapter 1</a></td></tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val (novel, chapters) = engine.scrapeNovel(
            url = "https://www.royalroad.com/fiction/999/sample",
            definition = DefaultSourceDefinitions.ROYAL_ROAD,
            preloadedHtml = rrHtml
        )

        assertEquals("Sample Story", novel.title)
        assertEquals("A thrilling journey into uncharted lands.", novel.description)
        assertEquals(1, chapters.size)
    }
}
