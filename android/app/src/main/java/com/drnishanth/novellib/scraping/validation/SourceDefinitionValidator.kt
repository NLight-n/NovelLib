package com.drnishanth.novellib.scraping.validation

import com.drnishanth.novellib.scraping.models.SelectorRule
import com.drnishanth.novellib.scraping.models.SourceDefinition

object SourceDefinitionValidator {

    const val CURRENT_ENGINE_VERSION = 1

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult() {
            val message: String get() = errors.joinToString("; ")
        }
    }

    /**
     * Validates a parsed SourceDefinition against strict schema and capability constraints.
     */
    fun validate(definition: SourceDefinition): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. Identification and Version
        if (definition.id.isBlank()) {
            errors.add("Source id cannot be blank")
        } else if (!definition.id.matches("^[a-z0-9_-]+$".toRegex())) {
            errors.add("Source id must contain only lowercase alphanumeric characters, hyphens, and underscores")
        }

        if (definition.version < 1) {
            errors.add("Source version must be >= 1")
        }

        if (definition.name.isBlank()) {
            errors.add("Source name cannot be blank")
        }

        // 2. Engine Compatibility
        if (definition.minimumEngineVersion > CURRENT_ENGINE_VERSION) {
            errors.add(
                "Source requires engine version ${definition.minimumEngineVersion}, but current engine version is $CURRENT_ENGINE_VERSION"
            )
        }

        // 3. Match Rules
        if (definition.match.hosts.isEmpty() && definition.match.urlPatterns.isEmpty()) {
            errors.add("Source must specify at least one host or URL pattern in match rules")
        }

        // 4. Novel Selectors
        validateSelector("novel.title", definition.novel.title, errors, required = true)
        definition.novel.author?.let { validateSelector("novel.author", it, errors) }
        definition.novel.description?.let { validateSelector("novel.description", it, errors) }
        definition.novel.cover?.let { validateSelector("novel.cover", it, errors) }
        definition.novel.tags?.let {
            if (it.selector.isBlank()) errors.add("novel.tags selector cannot be blank")
        }
        definition.novel.contentWarnings?.let {
            if (it.selector.isBlank()) errors.add("novel.contentWarnings selector cannot be blank")
        }

        // 5. Chapters Selectors
        if (definition.chapters.container.isBlank()) {
            errors.add("chapters.container selector cannot be blank")
        }
        validateSelector("chapters.title", definition.chapters.title, errors, required = true)
        validateSelector("chapters.url", definition.chapters.url, errors, required = true)

        // 6. Chapter Content Selector
        validateSelector("chapter.content", definition.chapter.content, errors, required = true)
        if (definition.chapter.contentSelectors.size > 8) {
            errors.add("chapter.content_selectors cannot contain more than 8 fallback selectors")
        }
        definition.chapter.contentSelectors.forEachIndexed { idx, rule ->
            validateSelector("chapter.content_selectors[$idx]", rule, errors, required = true)
        }

        // 7. Rendering Rules
        definition.rendering?.let { rendering ->
            val allowedModes = setOf("auto", "http_only", "webview_only")
            if (rendering.mode.lowercase() !in allowedModes) {
                errors.add("rendering.mode '${rendering.mode}' is invalid. Allowed modes: $allowedModes")
            }
            if (rendering.maxWaitMs !in 1_000L..20_000L) {
                errors.add("rendering.max_wait_ms must be between 1000 and 20000 ms (got ${rendering.maxWaitMs})")
            }
            if (rendering.minTextCharacters < 0) {
                errors.add("rendering.min_text_characters must be >= 0")
            }
        }

        // 8. Content Validation Rules
        definition.chapter.validation?.let { validation ->
            if (validation.minTextCharacters < 0) {
                errors.add("chapter.validation.min_text_characters must be >= 0")
            }
            if (validation.minParagraphs < 0) {
                errors.add("chapter.validation.min_paragraphs must be >= 0")
            }
            if (validation.maxLinkDensity !in 0.0..1.0) {
                errors.add("chapter.validation.max_link_density must be between 0.0 and 1.0 (got ${validation.maxLinkDensity})")
            }
            if (validation.rejectTitlePatterns.size > 20) {
                errors.add("chapter.validation.reject_title_patterns cannot exceed 20 patterns")
            }
            validation.rejectTitlePatterns.forEachIndexed { idx, pattern ->
                if (pattern.length > 100) {
                    errors.add("chapter.validation.reject_title_patterns[$idx] exceeds max length of 100")
                }
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    private fun validateSelector(
        fieldPath: String,
        rule: SelectorRule,
        errors: MutableList<String>,
        required: Boolean = false
    ) {
        if (required && rule.selector.isBlank()) {
            errors.add("$fieldPath selector cannot be blank")
        }

        val allowedTypes = setOf("text", "html", "attribute")
        if (rule.type.lowercase() !in allowedTypes) {
            errors.add("$fieldPath has unsupported type '${rule.type}'. Allowed: $allowedTypes")
        }

        if (rule.type.equals("attribute", ignoreCase = true) && rule.attribute.isNullOrBlank()) {
            errors.add("$fieldPath specifies type 'attribute' but attribute name is missing")
        }

        // Validate regex pattern if provided
        rule.regexPattern?.let { pattern ->
            try {
                pattern.toRegex()
            } catch (e: Exception) {
                errors.add("$fieldPath contains invalid regex pattern '$pattern': ${e.message}")
            }
        }
    }
}
