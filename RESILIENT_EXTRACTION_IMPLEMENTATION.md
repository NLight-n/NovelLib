# Resilient Content Extraction Implementation Plan

## Objective

Make NovelLib reliably acquire readable chapter content despite website redesigns, JavaScript rendering, authentication, and bot-protection changes. The system must **never save a page unless it passes extraction-quality validation**.

This is deliberately a hybrid approach:

```text
Official API / RSS / JSON-LD ─┐
HTTP fetch + source rules ────┼─> candidate document ─> extract candidates ─> validate ─> save
Rendered WebView ─────────────┘                                      │
                                                                    failure
                                                                      │
                                                       retry / user action / source update
```

An embedded WebView is a rendering and authenticated-session tool. It is not a universal parser: website DOM changes still require fallback selectors or source updates.

## Principles and constraints

- Prefer stable, documented data sources (official APIs, RSS/Atom, JSON-LD, EPUB) over visual HTML scraping.
- Keep remotely delivered source definitions declarative. Do not permit arbitrary JavaScript from a source definition.
- Treat source output as untrusted until it passes content validation.
- Use the visible in-app browser for login, consent, or CAPTCHA steps that require a person.
- Background work should prefer HTTP. A background WebView is best-effort only and must never be required for a queued batch to finish.
- Preserve current definitions and behaviour while migrating; the new fields are optional and additive.
- Respect source terms, access controls, and paid-content boundaries. Do not implement CAPTCHA bypassing.

## Current foundation

The application already has useful pieces to build on:

- `SourceDefinitionEngine` performs declarative extraction with Jsoup.
- `WebViewDocumentFetcher` can render a page and return its DOM after an HTTP failure or a 403/503 response.
- The in-app source browser can extract the currently rendered DOM during import.
- `WebKitCookieJar` shares cookies between WebView and OkHttp.
- Source definitions are remotely updateable and the app has rollback support.

The primary current weakness is that a fetched page is accepted whenever a single configured selector returns something. A challenge page, login screen, or changed markup can therefore become an extraction failure or potentially be saved as chapter content.

## Architecture changes

### 1. Introduce fetch and extraction result models

Add internal result types rather than returning a raw `Document` from every acquisition path.

```kotlin
enum class FetchMode { HTTP_ONLY, AUTO, WEBVIEW_ONLY }

enum class ExtractionFailureReason {
    NETWORK,
    HTTP_ERROR,
    AUTH_REQUIRED,
    CHALLENGE,
    NO_CONTENT,
    CONTENT_INVALID,
    TIMEOUT
}

data class FetchedDocument(
    val document: Document,
    val finalUrl: String,
    val fetchMethod: FetchMethod, // HTTP or WEBVIEW
    val elapsedMs: Long
)

sealed interface ChapterExtractionResult {
    data class Success(val content: ScrapedChapterContent) : ChapterExtractionResult
    data class Failure(
        val reason: ExtractionFailureReason,
        val message: String,
        val fetchMethod: FetchMethod? = null
    ) : ChapterExtractionResult
}
```

Keep the existing public repository API initially by converting a failure result into the current `Result.failure(...)`. Once the reader UI supports it, retain the typed failure so it can show a useful recovery action.

### 2. Extend definitions without breaking existing sources

Keep `chapter.content` as the legacy primary selector and add optional fields.

```json
{
  "rendering": {
    "mode": "auto",
    "ready_selector": ".chapter-content",
    "min_text_characters": 500,
    "max_wait_ms": 12000,
    "scroll_until_stable": false
  },
  "chapter": {
    "content": { "selector": ".chapter-content", "type": "html" },
    "content_selectors": [
      { "selector": ".chapter-content", "type": "html" },
      { "selector": "article .entry-content", "type": "html" },
      { "selector": "main article", "type": "html" }
    ],
    "validation": {
      "min_text_characters": 500,
      "min_paragraphs": 3,
      "max_link_density": 0.35,
      "reject_title_patterns": [
        "just a moment",
        "checking your browser",
        "access denied",
        "verify you are human",
        "sign in"
      ]
    }
  }
}
```

Suggested Kotlin additions:

```kotlin
@Serializable
data class RenderingRule(
    val mode: String = "auto",
    @SerialName("ready_selector") val readySelector: String? = null,
    @SerialName("min_text_characters") val minTextCharacters: Int = 300,
    @SerialName("max_wait_ms") val maxWaitMs: Long = 12_000L,
    @SerialName("scroll_until_stable") val scrollUntilStable: Boolean = false
)

@Serializable
data class ContentValidationRule(
    @SerialName("min_text_characters") val minTextCharacters: Int = 300,
    @SerialName("min_paragraphs") val minParagraphs: Int = 2,
    @SerialName("max_link_density") val maxLinkDensity: Double = 0.50,
    @SerialName("reject_title_patterns") val rejectTitlePatterns: List<String> = emptyList()
)
```

Place `rendering` on `SourceDefinition`; place `contentSelectors` and `validation` on `ChapterRule`. The definition validator must enforce sensible limits:

- `max_wait_ms`: 1,000 through 20,000 ms.
- A bounded number of fallback selectors (for example, 8).
- A bounded number of patterns and maximum pattern length.
- `mode` must be one of `http_only`, `auto`, or `webview_only`.

### 3. Add extraction validation

Create `ChapterContentValidator` that receives sanitized HTML and its source document/title.

Validation should include:

- Visible text character count.
- Paragraph/block count.
- Link-density limit: link text / all visible text.
- Rejected page-title and visible-text patterns.
- Rejection of empty shell markup, redirect notices, and known challenge/login forms.
- Optional source-specific required/forbidden selectors.

Return both `valid` and diagnostic metrics. Never persist content when validation fails.

```kotlin
data class ContentValidationResult(
    val valid: Boolean,
    val reason: ExtractionFailureReason?,
    val characterCount: Int,
    val paragraphCount: Int,
    val linkDensity: Double,
    val matchedRejection: String? = null
)
```

Run validation after sanitization. This evaluates what the reader will actually display rather than the raw source markup.

### 4. Use layered extraction

For a chapter document, extraction order should be:

1. Existing `chapter.content` selector.
2. Additional source-defined `content_selectors`, in order.
3. A built-in generic article extractor.
4. Failure with a typed reason and diagnostics.

The generic article extractor is a local fallback, not a replacement for adapters. It should consider candidates such as `article`, `main article`, `main`, `[role=main]`, and nodes with IDs/classes containing `chapter`, `content`, `entry`, `post`, or `reader`.

Score candidates using positive signals (text length, paragraph count, text density) and negative signals (link density, navigation/comment/ad/footer/sidebar terms). Send every candidate through `ChapterContentValidator` before accepting it.

This will recover many standard chapter pages after small markup changes while preserving source-specific rules for accuracy.

### 5. Replace timing-based WebView capture with readiness checks

`onPageFinished` does not mean that a JavaScript application has rendered its chapter. Upgrade `WebViewDocumentFetcher` to wait for an app-owned readiness condition:

1. Load the URL.
2. Poll at a short fixed interval, up to `max_wait_ms`.
3. In each poll, use a fixed JavaScript expression to check the configured `ready_selector`, visible text length, and document title.
4. Capture `document.documentElement.outerHTML` only when ready.
5. If `scroll_until_stable` is enabled, perform a small bounded number of local scrolls and wait for document height/text to stop changing.
6. Classify challenge/auth pages before returning the document.
7. Stop loading and destroy the WebView in all success, timeout, cancellation, and error paths.

The app must generate the selector check safely; it must not execute arbitrary JavaScript delivered from the registry. Selector strings must be serialized/escaped before being injected.

Use a process-wide `Mutex`/queue so at most one headless WebView fetch runs at a time. WebView uses shared state and must execute on the main thread.

### 6. Define fetch fallback policy

Implement a `DocumentAcquirer` used by `SourceDefinitionEngine`:

| Definition mode | Acquisition order |
| --- | --- |
| `http_only` | HTTP only |
| `auto` | HTTP -> validate/extract -> WebView on challenge, JS shell, invalid content, or configured render requirement |
| `webview_only` | WebView -> validate/extract |

Do not fall back to a WebView for every normal HTTP/network failure; it wastes battery and makes scheduled work slower. Do use it for a detected browser challenge, an empty JavaScript shell, or failed extraction after a successful HTTP response.

Persist lightweight diagnostics per source/chapter attempt: fetch method, failure reason, timestamp, elapsed time, and definition version. Do not persist page HTML when validation fails.

### 7. Treat user-interactive sessions separately

The visible source browser is the right path for human-required actions:

- Sign in.
- Cookie/age-consent dialog.
- CAPTCHA or other browser challenge.
- A manual retry after an extraction failure.

After a successful interactive session, continue using the shared CookieManager/OkHttp cookie bridge for normal fetches.

For WorkManager downloads:

- Prefer HTTP and retain the existing concurrency/rate limits.
- Permit a single serialized, best-effort WebView fallback only for sources that declare it.
- If authentication or a challenge requires a person, mark the chapter as `ACTION_REQUIRED`, do not keep retrying it, and notify the user to open the source in NovelLib.
- Do not attempt CAPTCHA bypasses or hidden interaction automation.

### 8. Improve chapter-index discovery independently

Chapter listings should support a separate preference order:

1. Official API/feed.
2. RSS/Atom feed.
3. JSON/JSON-LD data embedded in the page.
4. Static HTML list with source selectors.
5. Rendered HTML list using bounded scrolling or a fixed "load more" action.

Represent this as explicit, limited declarative capabilities rather than arbitrary scripts. For example, permit a configured pagination URL/selector or a boolean `scroll_until_stable`; do not permit remote JavaScript.

## User recovery experience

When chapter acquisition fails, show a reason-specific action instead of a generic failure:

| Failure | User-facing message | Action |
| --- | --- | --- |
| `AUTH_REQUIRED` | "This source needs an active sign-in session." | Open source browser |
| `CHALLENGE` | "This source needs a browser verification." | Open source browser |
| `CONTENT_INVALID` | "The source page layout may have changed." | Retry rendered page; report source issue |
| `NETWORK` / `TIMEOUT` | "Could not reach this chapter." | Retry later |

For a chapter open in the browser, add **Save readable content**. It captures the rendered DOM and runs the same layered extraction and validation pipeline. It must not save if validation fails.

## Registry quality workflow

Source definitions should be tested before publication.

### Fixtures

For every maintained source, store sanitized test fixtures outside the production APK or in test resources:

- Novel landing page.
- Chapter index including pagination where applicable.
- Representative chapter page.
- Known challenge/login/error page if applicable.

### Tests

For each definition/fixture pair, assert:

- Title and chapter list are extracted.
- Extracted content validates.
- Content does not contain scripts, challenge text, navigation, or login forms.
- Fallback selectors work when the primary selector is removed.
- Invalid/challenge fixtures return a classified failure and are never persisted.

### Release gate

Run fixture tests on every definition pull request and in a scheduled source-health job. A source-health result should include definition ID/version, status, chapter count, fetch method, and failure reason. Publish an updated definition only when it passes its fixture suite.

## Phased rollout

### Phase 0 — Baseline and tests

- Add fixtures for the current built-in sources.
- Record baseline chapter extraction success/failure locally.
- Add tests around the existing `SourceDefinitionEngine`.

**Exit criterion:** Current sources have reproducible extraction tests.

### Phase 1 — Safe validation and diagnostics

- Add validation schema and `ChapterContentValidator`.
- Add typed acquisition/extraction failures.
- Update repositories/download manager to avoid persisting invalid content.
- Surface a useful reader error instead of a generic failure.

**Exit criterion:** Challenge, login, and short-page fixtures are rejected and no invalid chapter file is created.

### Phase 2 — Selector resilience

- Add ordered source fallback selectors.
- Implement and test the generic article extractor.
- Migrate existing definitions to include validation thresholds and fallbacks.

**Exit criterion:** A deliberately broken primary selector is recovered by a fallback or becomes a safe failure.

### Phase 3 — Robust browser rendering

- Implement the `DocumentAcquirer` and rendering rules.
- Replace fixed-delay WebView capture with readiness polling.
- Serialize headless rendering and enforce cancellation/timeout cleanup.
- Add `http_only`, `auto`, and `webview_only` per-source mode.

**Exit criterion:** A JavaScript-rendered fixture/site is extracted only after its configured content is ready; a timed-out render releases the WebView cleanly.

### Phase 4 — Interactive recovery and background policy

- Add `ACTION_REQUIRED` chapter state and notification.
- Add browser recovery actions and **Save readable content**.
- Ensure WorkManager never loops indefinitely on auth/challenge failures.

**Exit criterion:** A user can re-establish a session in the in-app browser and retry a chapter without deleting/re-adding the novel.

### Phase 5 — Structured sources and registry health

- Add API/RSS/JSON-LD discovery modes.
- Configure high-value sources to use their most stable available data source.
- Enforce the fixture and source-health release gate for registry updates.

**Exit criterion:** Definitions have automated regression coverage and release only after validation.

## Suggested file-level changes

| Area | Expected change |
| --- | --- |
| `scraping/models/SourceDefinitionModels.kt` | Add optional rendering, selector-fallback, and validation fields. |
| `scraping/validation/SourceDefinitionValidator.kt` | Validate new field bounds and allowed enums. |
| `scraping/engine/SourceDefinitionEngine.kt` | Delegate acquisition, layered extraction, validation, and typed failures. |
| `scraping/engine/WebViewDocumentFetcher.kt` | Add readiness polling, challenge detection, serialization, and reliable cleanup. |
| `scraping/engine/ChapterContentValidator.kt` | New: validate sanitized chapter content. |
| `scraping/engine/GenericArticleExtractor.kt` | New: score and select a readable article fallback. |
| `scraping/engine/DocumentAcquirer.kt` | New: apply the per-source HTTP/WebView acquisition policy. |
| `data/repository/NovelRepository.kt` | Map typed failures to reader/retry state; never save invalid content. |
| `downloads/DownloadManager.kt` | Stop retries for user-action failures and retain diagnostics. |
| `ui/browser/*` and `ui/reader/*` | Add recovery actions and user-facing failure states. |
| `sources/definitions/*.json` | Add validation and fallback selectors incrementally. |
| `android/app/src/test/...` | Add fixture-driven definition/extraction tests. |

## Definition migration example

Start with a high-value source and conservative rules:

```json
"rendering": {
  "mode": "auto",
  "ready_selector": ".chapter-content",
  "min_text_characters": 500,
  "max_wait_ms": 10000
},
"chapter": {
  "content": { "selector": ".chapter-content", "type": "html" },
  "content_selectors": [
    { "selector": ".chapter-content", "type": "html" },
    { "selector": "article", "type": "html" }
  ],
  "validation": {
    "min_text_characters": 500,
    "min_paragraphs": 3,
    "max_link_density": 0.35,
    "reject_title_patterns": ["just a moment", "checking your browser"]
  },
  "sanitize": {
    "remove_tags": ["script", "style", "iframe", "button"],
    "allow_tags": ["p", "br", "b", "i", "em", "strong", "h1", "h2", "h3", "blockquote"]
  }
}
```

Review each source after migration. Thresholds should match real chapter length; very short prologues and poem-format chapters may need source-specific exceptions.

## Definition of done

The implementation is complete when:

- A page is saved only after content validation succeeds.
- A source can define a bounded HTTP/WebView acquisition policy and selector fallbacks.
- JavaScript-rendered content is captured after readiness, not an arbitrary delay.
- Login/challenge cases become user-action requests, not repeated background failures.
- Existing definitions remain compatible.
- Every maintained source has fixtures and automated regression tests.
- A user can recover from common source-session failures without re-importing a novel.
