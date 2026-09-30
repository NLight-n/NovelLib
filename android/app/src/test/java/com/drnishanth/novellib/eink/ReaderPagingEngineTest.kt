package com.drnishanth.novellib.eink

import com.drnishanth.novellib.core.eink.ReaderPagingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPagingEngineTest {

    @Test
    fun testCalculateTargetCharsPerPage() {
        val base = ReaderPagingEngine.calculateTargetCharsPerPage(fontSize = 18f, lineHeight = 1.6f, margins = 16)
        val largerFont = ReaderPagingEngine.calculateTargetCharsPerPage(fontSize = 28f, lineHeight = 1.6f, margins = 16)
        val smallerFont = ReaderPagingEngine.calculateTargetCharsPerPage(fontSize = 12f, lineHeight = 1.6f, margins = 16)

        assertTrue("Larger font should yield fewer characters per page", largerFont < base)
        assertTrue("Smaller font should yield more characters per page", smallerFont > base)
    }

    @Test
    fun testPaginate_emptyList() {
        val pages = ReaderPagingEngine.paginate(emptyList(), charsPerPage = 1000)
        assertEquals(1, pages.size)
        assertTrue(pages[0].isFirstPage)
        assertTrue(pages[0].isLastPage)
        assertEquals(0, pages[0].paragraphs.size)
    }

    @Test
    fun testPaginate_multipleParagraphs() {
        val paragraphs = listOf(
            "Paragraph 1 with approximately 50 characters of text.",
            "Paragraph 2 with another small sentence for reading.",
            "Paragraph 3 is also here.",
            "Paragraph 4 completes the sequence."
        )

        // Set a small charsPerPage to force multiple pages
        val pages = ReaderPagingEngine.paginate(paragraphs, charsPerPage = 120)
        assertTrue(pages.size > 1)
        assertEquals(1, pages[0].displayPageNumber)
        assertEquals(pages.size, pages.last().displayPageNumber)
        assertTrue(pages.first().isFirstPage)
        assertTrue(pages.last().isLastPage)
    }

    @Test
    fun testPaginate_splitsLongParagraph() {
        val hugeParagraph = buildString {
            repeat(30) {
                append("Sentence number $it is part of an extremely long narrative paragraph. ")
            }
        }
        assertTrue(hugeParagraph.length > 1500)

        val pages = ReaderPagingEngine.paginate(listOf(hugeParagraph), charsPerPage = 400)
        assertTrue("Huge paragraph must be split into multiple pages", pages.size >= 4)
        for (page in pages) {
            assertTrue(page.paragraphs.isNotEmpty())
        }
    }

    @Test
    fun testScrollAndPageConversion() {
        val paragraphs = (1..20).map { "Paragraph $it with enough narrative content to fill several lines of text." }
        val pages = ReaderPagingEngine.paginate(paragraphs, charsPerPage = 300)

        // For each page, verify scrollIndexForPage points to its startParagraphIndex
        for (page in pages) {
            val startIdx = ReaderPagingEngine.scrollIndexForPage(page.pageIndex, pages)
            assertEquals(page.startParagraphIndex, startIdx)

            val resolvedPageIdx = ReaderPagingEngine.pageIndexForScroll(startIdx, pages)
            assertEquals(page.pageIndex, resolvedPageIdx)
        }
    }
}
