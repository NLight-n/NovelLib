package com.drnishanth.novellib.core.utils

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

object HtmlSanitizer {

    /**
     * Sanitizes raw novel description / synopsis:
     * - Strips HTML tags (<p>, </p>, <div>, etc.)
     * - Preserves paragraph line breaks from <p> and <br>
     * - Decodes HTML entities (&amp; -> &, &#039; -> ', etc.)
     * - Normalizes multiple blank lines into clean paragraph spacing
     */
    fun cleanHtmlSynopsis(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        val unescaped = Parser.unescapeEntities(raw, false).trim()
        if (!unescaped.contains("<") && !unescaped.contains(">")) {
            return unescaped
        }

        val doc = Jsoup.parseBodyFragment(unescaped)
        // Convert br tags to newlines
        doc.select("br").append("\\n")
        // Convert p and div tags to paragraph breaks
        doc.select("p, div, li").prepend("\\n\\n")

        val text = doc.text().replace("\\n", "\n")
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    /**
     * Cleans chapter or novel titles:
     * - Decodes HTML entities (&amp; -> &, &quot; -> ", etc.)
     * - Strips any stray HTML tags
     * - Trims whitespace
     */
    fun cleanTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val unescaped = Parser.unescapeEntities(raw, false).trim()
        if (!unescaped.contains("<") && !unescaped.contains(">")) {
            return unescaped
        }
        return Jsoup.parseBodyFragment(unescaped).text().trim()
    }
}
