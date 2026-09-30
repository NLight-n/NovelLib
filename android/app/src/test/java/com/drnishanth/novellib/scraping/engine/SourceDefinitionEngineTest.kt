package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ChapterRule
import com.drnishanth.novellib.scraping.models.ChaptersRules
import com.drnishanth.novellib.scraping.models.NovelRules
import com.drnishanth.novellib.scraping.models.SanitizeRule
import com.drnishanth.novellib.scraping.models.SelectorRule
import com.drnishanth.novellib.scraping.models.SourceDefinition
import com.drnishanth.novellib.scraping.models.SourceMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            urlPatterns = listOf("https://testfiction.com/novel/*")
        ),
        novel = NovelRules(
            title = SelectorRule(selector = "h1.title", type = "text", transform = listOf("trim")),
            author = SelectorRule(selector = "span.author", type = "text", transform = listOf("trim")),
            description = SelectorRule(selector = "div.synopsis", type = "html", transform = listOf("trim"))
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
}
