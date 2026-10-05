# Reader-Mode Fallback Implementation Plan

## Decision

Yes, a reader-mode backup is feasible and is the best next reliability layer for NovelLib. It should be implemented as **NovelLib's own local reader-mode extractor**, not by trying to control or depend on Chrome/Edge's reader-mode UI.

Use a pinned, bundled copy of Mozilla's Readability algorithm as the primary reader-mode engine. It is the standalone library used for Firefox Reader View and produces article title, byline, excerpt, and cleaned article HTML. It must still be followed by NovelLib's sanitizer and validator; Readability itself is not a security sanitizer.

Reader mode is not foolproof. It cannot recover content that is not present in the DOM, content behind a login/paywall/CAPTCHA, image/canvas-only text, or pages where the site intentionally makes the chapter indistinguishable from navigation or comments. It can, however, provide a strong selector-free fallback for the normal case: a rendered HTML chapter containing readable text.

## Goal

When source-specific extraction fails, NovelLib should be able to:

1. Load the real page in a WebView with the user's existing cookies and JavaScript enabled.
2. Wait until the page's chapter-like content is genuinely present.
3. Run a local, bundled reader-mode algorithm against the rendered DOM.
4. Sanitize and validate the result.
5. Save/display it only if it is safe and plausibly a chapter.
6. Offer a visible **Reader mode** / **Choose reading area** recovery flow when automatic extraction remains uncertain.

The reader remains NovelLib's native reader, with offline storage, e-ink pagination, themes, and reading progress. WebView is only the acquisition/runtime needed to construct a clean chapter snapshot.

## Review of the current implementation

The resilient pipeline is a solid base, but it is not yet browser-quality reader mode.

### What exists

- `SourceDefinitionEngine.extractChapter` tries the source selector, configured fallback selectors, then `GenericArticleExtractor`.
- It retries with a rendered WebView document in `auto` mode after HTTP extraction fails.
- `ChapterContentValidator` rejects many challenge, login, short, and link-heavy results.
- `WebViewDocumentFetcher` serializes headless WebView fetches and has a time limit.

### Why it still fails

`GenericArticleExtractor` scores a small list of candidate containers. It does not perform the iterative DOM cleanup and scoring that a mature reader-mode engine uses. It will fail or choose extra content when a source uses unusual markup, multiple nested containers, author notes, comments, or SPA shells.

There are also immediate weaknesses to address before treating the WebView path as reliable:

1. **Readiness measures the entire body, not the candidate content.** Navigation/footer text can satisfy `min_text_characters` before the chapter has hydrated.
2. **`scroll_until_stable` is not implemented as named.** The current code performs one scroll and captures after 500 ms; it neither waits for document height to stabilize nor gathers multiple lazy-loaded segments.
3. **WebView errors are not classified.** The fetcher does not currently use `onReceivedError`, `onReceivedHttpError`, or render-process-gone handling, so different failures often collapse into a timeout.
4. **The generic fallback sees only the fetched DOM.** It has no reader-mode knowledge of unlikely content, sibling-merging, boilerplate removal, or title matching.
5. **Reader-mode output needs a quality gate.** A broad heuristic can successfully return the wrong article, such as comments or a chapter index. A successful parse is not sufficient evidence that it is the chapter.

## Scope

### In scope

- A bundled, pinned Readability implementation.
- Reader-mode extraction from a fully rendered WebView DOM.
- Reader-mode extraction from previously fetched static HTML when WebView is unnecessary.
- Sanitization, validation, metrics, fallback policy, local persistence, and user recovery UX.
- Fixtures and regression tests.

### Out of scope

- Automating CAPTCHA completion, login, subscription purchase, or paywall bypass.
- Executing JavaScript supplied by remote source definitions.
- Embedding or automating Chrome/Edge reader mode.
- Guaranteeing correct extraction for every arbitrary website.

## Proposed architecture

```text
source selector chain ──── valid ───────────────────────────────> native reader
          │
          └── invalid/missing
                  │
            rendered WebView DOM
                  │
         bundled ReaderModeExtractor
                  │
            sanitize + validate
            ┌─────┴─────┐
          valid       uncertain/invalid
            │              │
       native reader   visible reader-mode browser
                            │
                    user chooses reading area
                            │
                    sanitize + validate + native reader
```

### Acquisition policy

The normal source-specific path stays first because it knows the chapter container best.

1. Try source selector(s) on an HTTP document.
2. Try source selector(s) on a rendered WebView document if HTTP content was invalid, incomplete, or JavaScript-dependent.
3. Run reader mode on the best available document:
   - Rendered DOM when it exists.
   - Static HTML otherwise.
4. Accept only a validated result.
5. If reader mode cannot produce an acceptable result, surface a recovery UI rather than silently saving a bad chapter.

Reader mode must be an **automatic fallback**, not the default for every chapter. It is slower than a direct extraction and can be less precise on sites with chapter navigation mixed into the article.

## Technology choice

### Recommended: bundled Mozilla Readability

Vendor a pinned release of `@mozilla/readability` in app assets together with its Apache-2.0 license/notice. Run only this app-owned, versioned code; never download or execute extractor code from the source registry.

Why this is the preferred choice:

- It is purpose-built for reader view rather than a small custom selector list.
- It operates on the actual page DOM, including DOM rendered by client-side JavaScript.
- It produces structured output suitable for validation.
- Updating the pinned version is a deliberate app release, test, and license-review event.

Do not expect it to perform sanitization. Send its returned HTML through NovelLib's existing sanitizer and a strengthened validator before use.

### Execution options

Use two explicit adapters behind one interface:

```kotlin
interface ReaderModeExtractor {
    suspend fun extract(input: ReaderModeInput): ReaderModeResult
}

sealed interface ReaderModeInput {
    data class RenderedWebView(val webView: WebView, val url: String) : ReaderModeInput
    data class HtmlSnapshot(val html: String, val url: String) : ReaderModeInput
}
```

1. **Rendered WebView adapter (first implementation)**

   Inject the bundled Readability script and a small, app-owned bootstrap into the already-rendered WebView. Parse a clone of `document`, never mutate the page currently visible to the user. This is the most reliable choice for SPAs and authenticated content.

2. **Static HTML adapter (second implementation)**

   Use an off-screen WebView loaded with the already-fetched HTML via `loadDataWithBaseURL`, then run the same bundled script. This gives static HTTP pages the same algorithm and avoids writing two different reader-mode heuristics.

The static adapter must not enable remote script execution or let the snapshot navigate away. It processes a known HTML string in an isolated, app-owned WebView.

## Safe WebView bridge design

Do not pass a whole chapter as one `evaluateJavascript` callback result. A large page can exceed binder/result-size limits or get heavily escaped.

Use this sequence instead:

1. Load and verify the intended source URL in WebView.
2. Inject the pinned Readability asset and an app-owned bootstrap.
3. Bootstrap parses a clone of the current document and stores the compact result in a private page variable as UTF-8/base64 data.
4. Kotlin requests bounded chunks (for example, 24 KiB each) through `evaluateJavascript` and reassembles them.
5. Kotlin enforces a total output limit (for example, 2 MiB), decodes it, parses its structured JSON, sanitizes HTML, and validates it.
6. Clear the temporary page variable, cancel callbacks, and destroy the headless WebView on every terminal path.

Never add a broad `addJavascriptInterface` to arbitrary source pages for this purpose. If message passing is needed later, use an origin-restricted bridge with an allow-list; initial implementation should use `evaluateJavascript` chunk retrieval only.

## Reader-mode result and validation

Add a result model that preserves diagnostics without persisting unsafe HTML.

```kotlin
data class ReaderModeResult(
    val title: String?,
    val byline: String?,
    val excerpt: String?,
    val html: String?,
    val textLength: Int,
    val confidence: ReaderModeConfidence,
    val failure: ReaderModeFailure? = null
)

enum class ReaderModeConfidence { HIGH, MEDIUM, LOW }

enum class ReaderModeFailure {
    NOT_READERABLE,
    OUTPUT_TOO_LARGE,
    PAGE_NOT_READY,
    WEBVIEW_ERROR,
    INVALID_CONTENT,
    TIMEOUT
}
```

The acceptance gate must use more signals than character count:

- Sanitized text character count and block/paragraph count.
- Link density.
- Number of likely prose blocks and average sentence length.
- Challenge/login/paywall detection over the full rendered document.
- Overlap with the known chapter title, where available.
- Penalty for heavy navigation/comment/related-content vocabulary.
- A maximum fraction of text inside links/buttons/list menus.
- A confidence threshold; low-confidence automatic results are not saved.

Keep a deliberate exception for genuinely short chapters, poetry, and announcements. Source definitions may lower thresholds for a known source, but the generic fallback must retain conservative defaults.

## Correct the current rendering path first

This work is a prerequisite and should be included in the reader-mode implementation.

### Content-specific readiness

Replace the body-wide test with a fixed, app-owned readiness probe that returns:

- The number of matched `ready_selector` elements.
- Visible character count inside the largest matching element.
- Element height/scroll height.
- Document URL/title and challenge/auth markers.

For sources with no ready selector, use a conservative document-level fallback. Do not capture merely because the navigation shell has 300 characters.

### Real bounded lazy-load support

Implement `scroll_until_stable` as a bounded state machine:

1. Measure document scroll height and candidate text size.
2. Scroll near the end.
3. Wait 300-500 ms.
4. Re-measure.
5. Stop after two consecutive unchanged measurements, a configured maximum number of steps, or the overall timeout.

Capture only after content is ready and the scroll state is stable. Never endlessly scroll or click arbitrary page controls.

### Classify WebView failures

Handle main-frame load errors, HTTP errors, SSL errors, renderer termination, redirects outside the expected source, and cancellation. Map them to typed failures; preserve a short, redacted diagnostic for the reader UI and source-health tests.

## User experience

### Automatic fallback

If direct extraction fails but reader mode produces a high-confidence valid result, open it in the normal NovelLib reader and cache it as a normal chapter. Record the extraction method as `READER_MODE` for diagnostics.

### Visible recovery

For medium-confidence results or automatic failure, offer:

- **Open in reader mode** — opens the source in the restricted browser and runs the bundled algorithm visibly.
- **Use this readable version** — preview title and first paragraphs, then save only on user confirmation.
- **Choose reading area** — a manual last resort.

### Choose reading area

Implement this only after automatic reader mode is stable.

In a visible source WebView, activate a local selection overlay. The user taps a paragraph/container that belongs to the chapter. App-owned JavaScript walks up its ancestors, scores a small bounded set of candidate containers, highlights each candidate, and returns the selected outer HTML. Then NovelLib sanitizes and validates it before showing a preview/save action.

This is intentionally a manual recovery path. It can handle sites where any automatic algorithm has insufficient context, without trying to infer a permanent selector from a single accidental tap.

## Persistence and retry policy

Add `FetchMethod.READER_MODE` and store, per downloaded chapter:

- Fetch/extraction method.
- Definition version used.
- Validation metrics and confidence.
- Timestamp and a non-sensitive failure reason, if any.

Never overwrite an existing valid cached chapter with a lower-confidence reader-mode result automatically. Present an explicit replace/keep choice when the cached content hash differs.

For background downloads:

- Run direct HTTP extraction first.
- Reader mode may use one serialized headless WebView attempt per chapter only when policy allows it.
- Do not repeatedly retry challenge/auth failures.
- Return `ACTION_REQUIRED` for a user session, sign-in, CAPTCHA, or low-confidence reader-mode result.

## Test plan

### Unit tests

- Reader-mode result decoding/chunk assembly, including escaped Unicode and oversized output.
- Sanitization of Readability output.
- Acceptance/rejection confidence rules.
- No content is persisted on any failed validation.
- Short valid chapter and poetry exceptions.

### HTML fixtures

Create fixtures for each maintained source covering:

- Normal chapter with ads/sidebar/comments.
- Dynamic page snapshot after rendering.
- Author note before/after chapter.
- Chapter index that must not be accepted as content.
- Login/challenge/paywall page.
- Very short valid chapter.

Assert that source selectors win where valid; reader mode is used only as fallback; and reader mode returns the intended chapter text without known boilerplate.

### Instrumented WebView tests

- SPA that populates chapter text after a delay.
- Lazy-loaded content requiring more than one scroll.
- Main-frame HTTP failure and renderer termination.
- Large chapter chunk transfer.
- Cancellation during load and cleanup verification.

### Real-device acceptance

Test on the Android System WebView versions used by the target e-ink devices as well as a regular Android device. Verify reader mode with third-party cookies, a fresh install, and an expired authenticated session.

## Rollout phases

### Phase 1 — Repair current renderer behaviour

- Implement content-specific readiness.
- Replace single-scroll behavior with bounded stability detection.
- Add typed WebView error handling.
- Add regression tests for these cases.

**Exit criterion:** A delayed/lazy-loaded fixture is captured only after its content is present; failures are correctly classified rather than reported as generic timeouts.

### Phase 2 — Add the reader-mode engine

- Vendor the pinned Readability source and license notice.
- Implement script injection, chunked result transfer, output limits, and cleanup.
- Add `ReaderModeExtractor` and result types.
- Add sanitizer/validator integration.

**Exit criterion:** A fixture with advertisements, sidebar, and comments produces a valid clean chapter snapshot through the native reader.

### Phase 3 — Integrate fallback policy

- Add `READER_MODE` fetch/extraction diagnostics.
- Run reader mode after configured and generic extraction failures.
- Preserve existing valid cached chapters.
- Add source/definition feature flag: `reader_mode_fallback: enabled|disabled`.

**Exit criterion:** A source with a deliberately broken content selector reads successfully through reader mode, while challenge/login pages are not saved.

### Phase 4 — User recovery UX

- Add reader-mode preview and explicit confirmation for medium confidence.
- Add action-specific failure messages.
- Add **Choose reading area** behind an experimental flag.

**Exit criterion:** A user can recover a valid chapter manually without re-importing the novel or editing a definition.

### Phase 5 — Source quality and operations

- Add fixture suites for every source definition.
- Run them in CI and before registry publication.
- Collect local, privacy-preserving source health diagnostics and show a source issue report the user can copy/share.

**Exit criterion:** Reader-mode regressions and source markup changes are caught before registry releases where fixtures exist.

## File-level implementation map

| Area | Change |
| --- | --- |
| `app/src/main/assets/readability/` | New pinned Readability asset, bootstrap script, Apache-2.0 notice/license. |
| `scraping/engine/ReaderModeExtractor.kt` | New interface and orchestration. |
| `scraping/engine/WebViewReaderModeExtractor.kt` | New rendered-DOM and static-HTML adapters, chunk transfer, cleanup. |
| `scraping/engine/WebViewDocumentFetcher.kt` | Correct readiness, stable scrolling, and typed WebView error reporting. |
| `scraping/engine/ChapterContentValidator.kt` | Add reader-mode confidence/quality checks. |
| `scraping/engine/SourceDefinitionEngine.kt` | Add reader-mode step after existing selector/generic fallbacks. |
| `scraping/models/SourceDefinitionModels.kt` | Optional `reader_mode_fallback` policy and `FetchMethod.READER_MODE`. |
| `data/repository/NovelRepository.kt` | Persist only accepted reader-mode content and diagnostics. |
| `downloads/DownloadManager.kt` | Limit background reader-mode attempts and handle action-required states. |
| `ui/reader/*`, `ui/browser/*` | Reader-mode preview, recovery, and manual selection UI. |
| `src/test` and `src/androidTest` | Fixture, parser, WebView, cancellation, and regression tests. |

## Definition of done

The reader-mode backup is complete when:

- It uses local, pinned extractor code rather than Chrome/Edge UI or remotely supplied script.
- It works on a rendered, authenticated WebView DOM and static HTML snapshots.
- It transfers large extracted content safely and cleans up WebViews in every outcome.
- It sanitizes and validates all output before rendering or caching.
- It never replaces a valid cached chapter with a lower-confidence result automatically.
- It provides a visible user recovery path when automatic extraction cannot be trusted.
- It has representative fixtures and instrumented WebView tests for every maintained source.

## Source references

- Mozilla's official standalone Readability project: <https://github.com/mozilla/readability>
- Its README documents `Readability(document).parse()` and explicitly recommends sanitizing untrusted output before use.
