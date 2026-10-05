package com.drnishanth.novellib.ui.browser

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.core.database.dao.NovelDao
import com.drnishanth.novellib.core.database.dao.SourceDefinitionDao
import com.drnishanth.novellib.core.database.entities.UserProfileEntity
import com.drnishanth.novellib.core.network.WebKitCookieJar
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import com.drnishanth.novellib.scraping.engine.DefaultSourceDefinitions
import com.drnishanth.novellib.scraping.engine.SourceDefinitionEngine
import com.drnishanth.novellib.scraping.models.SourceDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import kotlin.coroutines.resume

data class SourceBrowserUiState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val allowedHosts: Set<String> = emptySet(),
    val isNovelDetected: Boolean = false,
    val detectedDefinition: SourceDefinition? = null,
    val isInLibrary: Boolean = false,
    val isRestricted: Boolean = false,
    val restrictedTags: List<String> = emptyList(),
    val isImporting: Boolean = false,
    val importSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val isChapterDetected: Boolean = false,
    val detectedChapter: com.drnishanth.novellib.core.database.entities.ChapterEntity? = null,
    val isSavingChapter: Boolean = false
)

class SourceBrowserViewModel(
    private val novelRepository: NovelRepository = NovelLibApplication.instance.novelRepository,
    private val profileRepository: ProfileRepository = NovelLibApplication.instance.profileRepository,
    private val sourceDefinitionDao: SourceDefinitionDao = NovelLibApplication.instance.database.sourceDefinitionDao(),
    private val novelDao: NovelDao = NovelLibApplication.instance.database.novelDao(),
    private val scraperEngine: SourceDefinitionEngine = SourceDefinitionEngine()
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(SourceBrowserUiState())
    val uiState: StateFlow<SourceBrowserUiState> = _uiState.asStateFlow()

    private var availableDefinitions: List<SourceDefinition> = emptyList()

    val activeProfile: StateFlow<UserProfileEntity?> = profileRepository.activeProfile

    init {
        loadAllowedHosts()
    }

    private fun loadAllowedHosts() {
        viewModelScope.launch(Dispatchers.IO) {
            val definitions = mutableListOf<SourceDefinition>()
            // Builtin definitions
            definitions.addAll(DefaultSourceDefinitions.BUILTIN_DEFINITIONS)

            // Database definitions
            try {
                val dbDefs = sourceDefinitionDao.getAllDefinitionsDirect()
                for (entity in dbDefs) {
                    if (entity.enabled) {
                        try {
                            val parsed = json.decodeFromString<SourceDefinition>(entity.jsonContent)
                            definitions.removeAll { it.id == parsed.id }
                            definitions.add(parsed)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            availableDefinitions = definitions

            val hosts = definitions.flatMap { it.match.hosts }
                .map { it.lowercase().trim() }
                .toSet()

            _uiState.update { it.copy(allowedHosts = hosts) }
        }
    }

    fun isHostAllowed(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            val allowed = _uiState.value.allowedHosts
            allowed.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    fun onPageStarted(url: String) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                isLoading = true,
                errorMessage = null,
                importSuccessMessage = null
            )
        }
    }

    fun onPageFinished(url: String, title: String?, webView: WebView?) {
        // 1. Sync cookies to OkHttp
        WebKitCookieJar.syncCookies(url)

        _uiState.update {
            it.copy(
                currentUrl = url,
                pageTitle = title ?: it.pageTitle,
                isLoading = false
            )
        }

        // 2. Novel detection against registered source definitions
        checkNovelDetection(url, webView)
    }

    private fun checkNovelDetection(url: String, webView: WebView?) {
        viewModelScope.launch(Dispatchers.IO) {
            val matchingDef = availableDefinitions.firstOrNull { def ->
                scraperEngine.matchesNovelUrl(url, def)
            }

            if (matchingDef == null) {
                val detectedChap = novelRepository.findChapterByUrl(url)
                _uiState.update {
                    it.copy(
                        isNovelDetected = false,
                        detectedDefinition = null,
                        isInLibrary = false,
                        isRestricted = false,
                        restrictedTags = emptyList(),
                        isChapterDetected = detectedChap != null,
                        detectedChapter = detectedChap
                    )
                }
                return@launch
            }

            // Novel URL is detected!
            val profile = activeProfile.value
            val blocked = profile?.blockedTags ?: emptyList()
            val blockedLower = blocked.map { it.lowercase().trim() }.filter { it.isNotEmpty() }

            // Check if novel is already in library
            val existingSource = novelDao.getSourceByUrl(url)
            var inLibrary = false
            var isRestricted = false
            val matchedRestricted = mutableListOf<String>()

            if (existingSource != null && profile != null) {
                val count = novelDao.isNovelInLibrary(profile.id, existingSource.novelId)
                inLibrary = count > 0

                // Check local tags in Room
                val localTags = novelDao.getTagsForNovel(existingSource.novelId)
                for (tag in localTags) {
                    if (blockedLower.contains(tag.name.lowercase().trim()) || blocked.contains(tag.id)) {
                        isRestricted = true
                        matchedRestricted.add(tag.name)
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isNovelDetected = true,
                    detectedDefinition = matchingDef,
                    isInLibrary = inLibrary,
                    isRestricted = isRestricted,
                    restrictedTags = matchedRestricted,
                    isChapterDetected = false,
                    detectedChapter = null
                )
            }

            // If not already restricted and active profile has blocked tags, inspect DOM tags via WebView
            if (!isRestricted && blockedLower.isNotEmpty() && webView != null) {
                checkDomTags(matchingDef, blockedLower, webView)
            }
        }
    }

    private suspend fun checkDomTags(
        definition: SourceDefinition,
        blockedLower: List<String>,
        webView: WebView
    ) {
        val tagSelector = definition.novel.tags?.selector ?: "span.tags a, .fiction-tag"
        val warnSelector = definition.novel.contentWarnings?.selector ?: ".font-red-sunglo ul.list-inline li, ul.list-inline li"
        val combinedSelector = "$tagSelector, $warnSelector".replace("'", "\\'")

        val jsCode = """
            (function() {
                var items = [];
                try {
                    var els = document.querySelectorAll('$combinedSelector');
                    for (var i = 0; i < els.length; i++) {
                        var text = els[i].textContent || els[i].innerText || '';
                        text = text.trim();
                        if (text.length > 0) items.push(text);
                    }
                } catch(e) {}
                return JSON.stringify(items);
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(jsCode) { result ->
                if (result != null && result != "null" && result.length > 2) {
                    try {
                        val unquoted = if (result.startsWith("\"") && result.endsWith("\"")) {
                            // Unescape JSON string literal returned by evaluateJavascript
                            json.decodeFromString<String>(result)
                        } else {
                            result
                        }
                        val extracted = json.decodeFromString<List<String>>(unquoted)
                        val hitTags = extracted.filter { tag ->
                            blockedLower.contains(tag.lowercase().trim())
                        }
                        if (hitTags.isNotEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isRestricted = true,
                                    restrictedTags = hitTags.distinct()
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun importCurrentNovel(webView: WebView? = null) {
        val url = _uiState.value.currentUrl
        val profile = activeProfile.value ?: return

        if (_uiState.value.isRestricted) {
            _uiState.update { it.copy(errorMessage = "Cannot add: Novel contains tags restricted by this profile.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }

            // Extract outerHTML directly from active WebView to bypass Cloudflare and render SPA DOM
            val preloadedHtml: String? = if (webView != null) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        val extractionJs = """
                            (function() {
                                try {
                                    var host = window.location.hostname.toLowerCase();
                                    if (host.indexOf("scribblehub.com") !== -1) {
                                        var postIdInput = document.getElementById("mypostid") || document.querySelector("input[name='mypostid']");
                                        var postId = postIdInput ? postIdInput.value : null;
                                        if (!postId) {
                                            var m = window.location.pathname.match(/\/series\/(\d+)/);
                                            if (m) postId = m[1];
                                        }
                                        if (postId) {
                                            var existing = document.querySelectorAll("a.toc_a, li.toc_li");
                                            if (existing.length === 0) {
                                                var xhr = new XMLHttpRequest();
                                                xhr.open("POST", "/wp-admin/admin-ajax.php", false);
                                                xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                                                xhr.send("action=wi_getreleases_pagination&pagenum=-1&mypostid=" + postId);
                                                if (xhr.status === 200 && xhr.responseText) {
                                                    var container = document.getElementById("toc_list") || document.querySelector("ul.toc_w") || document.body;
                                                    var tempDiv = document.createElement("div");
                                                    tempDiv.id = "novellib-injected-toc";
                                                    tempDiv.innerHTML = xhr.responseText;
                                                    container.appendChild(tempDiv);
                                                }
                                            }
                                        }
                                    } else if (host.indexOf("novelupdates.com") !== -1) {
                                        var postIdInput = document.getElementById("mypostid") || document.querySelector("input[name='mypostid']");
                                        var postId = postIdInput ? postIdInput.value : null;
                                        if (!postId) {
                                            var m = document.body.innerHTML.match(/mypostid["\s:=]+(\d+)/);
                                            if (m) postId = m[1];
                                        }
                                        if (postId) {
                                            var existing = document.querySelectorAll("#myTable tr, a[href*='/extnu/']");
                                            if (existing.length === 0) {
                                                var xhr = new XMLHttpRequest();
                                                xhr.open("POST", "/wp-admin/admin-ajax.php", false);
                                                xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                                                xhr.send("action=nd_getchapters&mygrr=0&mypostid=" + postId);
                                                if (xhr.status === 200 && xhr.responseText) {
                                                    var container = document.getElementById("myTable") || document.body;
                                                    var tempDiv = document.createElement("div");
                                                    tempDiv.id = "novellib-injected-nu-toc";
                                                    tempDiv.innerHTML = xhr.responseText;
                                                    container.appendChild(tempDiv);
                                                }
                                            }
                                        }
                                    }
                                } catch (e) {}
                                return document.documentElement.outerHTML;
                            })()
                        """.trimIndent()

                        webView.evaluateJavascript(extractionJs) { result ->
                            if (result != null && result != "null" && result.length > 50) {
                                val unescaped = try {
                                    org.json.JSONTokener(result).nextValue() as? String ?: result
                                } catch (_: Exception) {
                                    result
                                }
                                if (continuation.isActive) {
                                    continuation.resume(unescaped)
                                }
                            } else {
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    }
                }
            } else {
                null
            }

            val result = novelRepository.importNovelFromUrl(url.trim(), profile.id, preloadedHtml)
            if (result.isSuccess) {
                val novel = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        isInLibrary = true,
                        importSuccessMessage = "Added \"${novel?.title}\" to library!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to import novel"
                    )
                }
            }
        }
    }

    /**
     * Captures pre-rendered DOM from the active WebView and validates/saves
     * the chapter content directly into the library.
     */
    fun saveReadableChapter(webView: WebView? = null) {
        val chapter = _uiState.value.detectedChapter ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingChapter = true, errorMessage = null) }
            val html = if (webView != null) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { result ->
                            val unescaped = try {
                                if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
                                    org.json.JSONTokener(result).nextValue() as? String ?: result
                                } else {
                                    result ?: ""
                                }
                            } catch (_: Exception) {
                                result ?: ""
                            }
                            if (continuation.isActive) continuation.resume(unescaped)
                        }
                    }
                }
            } else ""

            val saveResult = novelRepository.saveReadableChapterContent(chapter.id, html)
            if (saveResult.isSuccess) {
                _uiState.update {
                    it.copy(
                        isSavingChapter = false,
                        importSuccessMessage = "Saved readable chapter: \"${chapter.title}\""
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSavingChapter = false,
                        errorMessage = saveResult.exceptionOrNull()?.message ?: "Failed to validate/save chapter content"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, importSuccessMessage = null) }
    }
}
