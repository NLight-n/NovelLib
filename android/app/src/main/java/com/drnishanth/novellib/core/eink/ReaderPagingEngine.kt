package com.drnishanth.novellib.core.eink

/**
 * Paging engine for discrete, static tap-to-page reading.
 * Eliminates continuous scrolling animations to prevent ghosting and latency
 * on E-Ink and low-refresh hardware displays.
 */
object ReaderPagingEngine {

    data class ReaderPage(
        val pageIndex: Int,
        val totalPages: Int,
        val displayPageNumber: Int = pageIndex + 1,
        val paragraphs: List<String>,
        val startParagraphIndex: Int,
        val endParagraphIndex: Int,
        val isFirstPage: Boolean = pageIndex == 0,
        val isLastPage: Boolean = pageIndex == totalPages - 1
    )

    /**
     * Estimates optimal characters per page based on current font size, line height, and margins.
     */
    fun calculateTargetCharsPerPage(
        fontSize: Float,
        lineHeight: Float = 1.6f,
        margins: Int = 16
    ): Int {
        val baseChars = 1200f
        val fontScale = 18f / fontSize.coerceIn(12f, 36f)
        val lineScale = 1.6f / lineHeight.coerceIn(1.2f, 2.2f)
        val marginScale = (400f - (margins * 2)) / 368f

        val calculated = (baseChars * fontScale * lineScale * marginScale).toInt()
        return calculated.coerceIn(400, 3000)
    }

    /**
     * Splits a list of chapter paragraphs into discrete reading pages.
     * Preserves paragraph boundaries where possible, and safely splits oversized
     * paragraphs at sentence/word boundaries.
     */
    fun paginate(
        paragraphs: List<String>,
        charsPerPage: Int = 1200
    ): List<ReaderPage> {
        if (paragraphs.isEmpty()) {
            return listOf(
                ReaderPage(
                    pageIndex = 0,
                    totalPages = 1,
                    paragraphs = emptyList(),
                    startParagraphIndex = 0,
                    endParagraphIndex = 0
                )
            )
        }

        val pagesRaw = mutableListOf<PageDraft>()
        var currentDraftParagraphs = mutableListOf<String>()
        var currentLength = 0
        var startIdx = 0

        paragraphs.forEachIndexed { pIndex, rawParagraph ->
            val pText = rawParagraph.trim()
            if (pText.isBlank()) return@forEachIndexed

            if (pText.length > charsPerPage) {
                // Split large paragraph into chunks
                val subChunks = splitLongParagraph(pText, charsPerPage)
                for (chunk in subChunks) {
                    if (currentLength + chunk.length > charsPerPage && currentDraftParagraphs.isNotEmpty()) {
                        pagesRaw.add(
                            PageDraft(
                                paragraphs = currentDraftParagraphs.toList(),
                                startIdx = startIdx,
                                endIdx = pIndex
                            )
                        )
                        currentDraftParagraphs = mutableListOf()
                        currentLength = 0
                        startIdx = pIndex
                    }
                    currentDraftParagraphs.add(chunk)
                    currentLength += chunk.length
                }
            } else {
                if (currentLength + pText.length > charsPerPage && currentDraftParagraphs.isNotEmpty()) {
                    pagesRaw.add(
                        PageDraft(
                            paragraphs = currentDraftParagraphs.toList(),
                            startIdx = startIdx,
                            endIdx = pIndex - 1
                        )
                    )
                    currentDraftParagraphs = mutableListOf()
                    currentLength = 0
                    startIdx = pIndex
                }
                currentDraftParagraphs.add(pText)
                currentLength += pText.length
            }
        }

        if (currentDraftParagraphs.isNotEmpty()) {
            pagesRaw.add(
                PageDraft(
                    paragraphs = currentDraftParagraphs.toList(),
                    startIdx = startIdx,
                    endIdx = paragraphs.size - 1
                )
            )
        }

        val total = pagesRaw.size.coerceAtLeast(1)
        return pagesRaw.mapIndexed { idx, draft ->
            ReaderPage(
                pageIndex = idx,
                totalPages = total,
                paragraphs = draft.paragraphs,
                startParagraphIndex = draft.startIdx,
                endParagraphIndex = draft.endIdx
            )
        }
    }

    /**
     * Converts a scroll index (paragraph index) to the corresponding page index.
     */
    fun pageIndexForScroll(scrollIndex: Int, pages: List<ReaderPage>): Int {
        if (pages.isEmpty()) return 0
        val found = pages.indexOfFirst { scrollIndex in it.startParagraphIndex..it.endParagraphIndex }
        return if (found >= 0) found else (pages.size - 1).coerceAtLeast(0)
    }

    /**
     * Converts a page index to its starting paragraph index.
     */
    fun scrollIndexForPage(pageIndex: Int, pages: List<ReaderPage>): Int {
        if (pages.isEmpty()) return 0
        val clamped = pageIndex.coerceIn(0, pages.size - 1)
        return pages[clamped].startParagraphIndex
    }

    private fun splitLongParagraph(text: String, maxChunkSize: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxChunkSize) {
            // Find a sentence or word boundary near maxChunkSize
            var splitPoint = remaining.lastIndexOf(". ", maxChunkSize)
            if (splitPoint < maxChunkSize / 2) {
                splitPoint = remaining.lastIndexOf(" ", maxChunkSize)
            }
            if (splitPoint <= 0) {
                splitPoint = maxChunkSize
            } else {
                splitPoint += 1 // Include trailing period/space
            }

            result.add(remaining.substring(0, splitPoint).trim())
            remaining = remaining.substring(splitPoint).trim()
        }
        if (remaining.isNotBlank()) {
            result.add(remaining)
        }
        return result
    }

    private data class PageDraft(
        val paragraphs: List<String>,
        val startIdx: Int,
        val endIdx: Int
    )
}
