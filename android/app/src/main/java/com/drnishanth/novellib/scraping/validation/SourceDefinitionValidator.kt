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
