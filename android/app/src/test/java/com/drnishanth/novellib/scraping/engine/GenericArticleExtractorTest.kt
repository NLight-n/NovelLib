package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.scraping.models.ContentValidationRule
import org.jsoup.Jsoup
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericArticleExtractorTest {

    @Test
    fun testExtractArticleFromStandardArticleTag() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Chapter 10</title></head>
            <body>
                <header><nav><a href="/">Home</a></nav></header>
                <article class="reader-container">
                    <p>The dawn brought a crisp cool breeze through the mountain pass.</p>
                    <p>He tightened his cloak and picked up his staff, knowing that the journey to the citadel would test every ounce of endurance.</p>
                    <p>Ahead, the ancient stone markers pointed towards the winding path through the misty crags.</p>
                </article>
                <footer><p>© 2026 Author</p></footer>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val extracted = GenericArticleExtractor.extractArticle(
            doc = doc,
            validationRule = ContentValidationRule(minTextCharacters = 100, minParagraphs = 2)
        )

        assertNotNull("Expected article to be extracted", extracted)
        assertTrue(extracted!!.contains("mountain pass"))
        assertTrue(extracted.contains("citadel"))
    }

    @Test
    fun testDeprioritizeCommentsAndSidebar() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Chapter 15</title></head>
            <body>
                <div class="sidebar-widget">
                    <p><a href="/1">Related Link 1</a> <a href="/2">Related Link 2</a> <a href="/3">Related Link 3</a></p>
                    <p><a href="/4">Related Link 4</a> <a href="/5">Related Link 5</a></p>
                </div>
                <div class="entry-content">
                    <p>This is the primary text of the novel chapter that the reader should be enjoying.</p>
                    <p>The magic flowed through his veins like liquid sunlight, warm and powerful beyond measure.</p>
                    <p>With a single gesture, the ward was shattered into sparkling motes of starlight.</p>
                </div>
                <div class="comments-container">
                    <p>Comment 1: Great chapter! Can't wait for more!</p>
                    <p>Comment 2: Thanks for translating this novel!</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val extracted = GenericArticleExtractor.extractArticle(
            doc = doc,
            validationRule = ContentValidationRule(minTextCharacters = 100, minParagraphs = 2)
        )

        assertNotNull(extracted)
        assertTrue(extracted!!.contains("primary text of the novel chapter"))
        assertTrue(!extracted.contains("comments-container"))
    }

    @Test
    fun testReturnNullOnEmptyOrChallengePage() {
        val challengeHtml = """
            <html>
            <head><title>Just a moment...</title></head>
            <body>
                <div>Please wait while your request is being verified...</div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(challengeHtml)
        val extracted = GenericArticleExtractor.extractArticle(
            doc = doc,
            validationRule = ContentValidationRule(minTextCharacters = 100, minParagraphs = 2)
        )

        assertNull("Challenge page must not extract as an article", extracted)
    }
}
