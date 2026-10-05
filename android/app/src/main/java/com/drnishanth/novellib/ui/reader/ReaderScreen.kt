package com.drnishanth.novellib.ui.reader

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.core.eink.ReaderPagingEngine
import com.drnishanth.novellib.ui.theme.BorderEInk
import com.drnishanth.novellib.ui.theme.NovelLibTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenSourceBrowser: ((url: String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs = uiState.preferences
    var showSettingsSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.initialScrollIndex
    )

    // Save reading position when user scrolls (Continuous Scroll mode)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (prefs.pageNavigationMode == "scroll") {
                    viewModel.saveScrollIndex(index)
                }
            }
    }

    // Reset/restore scroll position whenever chapter changes
    // If user has saved progress in this chapter (> 0), restore it; otherwise scroll to 0 (top of new chapter)
    LaunchedEffect(uiState.currentChapter?.id) {
        if (uiState.currentChapter?.id == null) return@LaunchedEffect
        if (prefs.pageNavigationMode == "scroll") {
            val target = if (uiState.initialScrollIndex in uiState.paragraphs.indices) {
                uiState.initialScrollIndex
            } else {
                0
            }
            listState.scrollToItem(target)
        }
    }

    // Scroll to initial saved position when chapter finishes loading paragraphs
    LaunchedEffect(uiState.initialScrollIndex, uiState.paragraphs.size) {
        if (prefs.pageNavigationMode == "scroll" && uiState.paragraphs.isNotEmpty()) {
            val target = if (uiState.initialScrollIndex in uiState.paragraphs.indices) {
                uiState.initialScrollIndex
            } else {
                0
            }
            listState.scrollToItem(target)
        }
    }

    // Request keyboard/hardware button focus
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
            // Ignore in testing
        }
    }

    val selectedFontFamily = when (prefs.fontFamily.lowercase()) {
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }

    NovelLibTheme(themeName = prefs.theme) {
        val isDark = prefs.theme.equals("dark", ignoreCase = true)
        val readerTextColor = if (isDark) {
            val b = prefs.textBrightness.coerceIn(0.40f, 1.0f)
            Color(red = b, green = b, blue = b)
        } else {
            MaterialTheme.colorScheme.onBackground
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && prefs.volumeKeysNavigation) {
                        when (keyEvent.key) {
                            Key.VolumeDown, Key.PageDown, Key.DirectionDown, Key.DirectionRight -> {
                                viewModel.nextPage()
                                true
                            }
                            Key.VolumeUp, Key.PageUp, Key.DirectionUp, Key.DirectionLeft -> {
                                viewModel.previousPage()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            // LAYER 1: Content Layer (Stationary, stable layout bounds)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                } else if (uiState.isAddictionLocked) {
                    AddictionLockoutScreen(
                        novelTitle = uiState.novelTitle,
                        limit = uiState.addictionLimit,
                        lockedUntil = uiState.lockedUntil,
                        onBack = onBack,
                        onExpired = { viewModel.onLockoutExpired() }
                    )
                } else if (uiState.errorMessage != null) {
                    val reason = uiState.failureReason
                    val isActionRequired = reason == com.drnishanth.novellib.scraping.models.ExtractionFailureReason.AUTH_REQUIRED ||
                            reason == com.drnishanth.novellib.scraping.models.ExtractionFailureReason.CHALLENGE
                    val isLayoutChanged = reason == com.drnishanth.novellib.scraping.models.ExtractionFailureReason.CONTENT_INVALID

                    val titleText = when (reason) {
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.AUTH_REQUIRED -> "Authentication Required"
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.CHALLENGE -> "Verification Required"
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.CONTENT_INVALID -> "Layout Changed"
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.TIMEOUT, com.drnishanth.novellib.scraping.models.ExtractionFailureReason.NETWORK -> "Network Unavailable"
                        else -> "Error Loading Chapter"
                    }

                    val userMessage = when (reason) {
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.AUTH_REQUIRED -> "This source needs an active sign-in session."
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.CHALLENGE -> "This source requires browser verification (CAPTCHA / Cloudflare)."
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.CONTENT_INVALID -> "The source page layout may have changed. You can retry with a rendered page."
                        com.drnishanth.novellib.scraping.models.ExtractionFailureReason.TIMEOUT, com.drnishanth.novellib.scraping.models.ExtractionFailureReason.NETWORK -> "Could not reach this chapter. Check your connection and retry."
                        else -> uiState.errorMessage ?: "Failed to load chapter content."
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isActionRequired -> Icons.Default.Lock
                                isLayoutChanged -> Icons.Default.Tune
                                else -> Icons.Default.Refresh
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = userMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                uiState.currentChapter?.let { viewModel.loadChapter(it.id) }
                            }) {
                                Text("Retry")
                            }
                            if (isActionRequired || onOpenSourceBrowser != null) {
                                OutlinedButton(onClick = {
                                    val sourceUrl = uiState.currentChapter?.sourceUrl
                                    if (!sourceUrl.isNullOrBlank()) {
                                        onOpenSourceBrowser?.invoke(sourceUrl)
                                    }
                                }) {
                                    Text("Open in Browser")
                                }
                            }
                        }
                    }
                } else {
                    if (prefs.pageNavigationMode.equals("paging", ignoreCase = true)) {
                        // Discrete Tap-to-Page Layout (E-Ink Optimized)
                        PaginatedReaderView(
                            uiState = uiState,
                            selectedFontFamily = selectedFontFamily,
                            textColor = readerTextColor,
                            onPrevious = { viewModel.previousPage() },
                            onNext = { viewModel.nextPage() },
                            onToggleControls = { viewModel.toggleControls() }
                        )
                    } else {
                        // Continuous Scroll Layout
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                top = 72.dp, // Stable safe margin at top so text starts below top bar area
                                bottom = 96.dp, // Stable safe margin at bottom so next/prev buttons clear bottom bar area
                                start = prefs.margins.dp,
                                end = prefs.margins.dp
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    viewModel.toggleControls()
                                }
                        ) {
                            item {
                                uiState.currentChapter?.let { chapter ->
                                    Text(
                                        text = chapter.title,
                                        fontSize = (prefs.fontSize + 4).sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = selectedFontFamily,
                                        color = readerTextColor,
                                        modifier = Modifier.padding(bottom = 20.dp, top = 8.dp)
                                    )
                                }
                            }

                            itemsIndexed(uiState.paragraphs) { _, paragraph ->
                                Text(
                                    text = paragraph,
                                    fontSize = prefs.fontSize.sp,
                                    lineHeight = (prefs.fontSize * prefs.lineHeight).sp,
                                    fontFamily = selectedFontFamily,
                                    color = readerTextColor,
                                    modifier = Modifier.padding(bottom = prefs.paragraphSpacing.dp)
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            uiState.previousChapterId?.let { viewModel.loadChapter(it) }
                                        },
                                        enabled = uiState.previousChapterId != null
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Previous")
                                    }

                                    Button(
                                        onClick = {
                                            uiState.nextChapterId?.let { viewModel.loadChapter(it) }
                                        },
                                        enabled = uiState.nextChapterId != null
                                    ) {
                                        Text("Next")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                    }
                                }
                                Spacer(modifier = Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }

            // LAYER 2: Top Bar Floating Overlay
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.currentChapter?.title ?: "Reader",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (uiState.isEInkDevice) {
                                    Text(
                                        text = "E-Ink Mode Active",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            // E-Ink Manual Full-Screen Refresh Button
                            IconButton(onClick = { viewModel.triggerScreenRefresh(context, view) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh E-Ink Display")
                            }

                            val isDownloaded = uiState.currentChapter?.downloadState == "available"
                            IconButton(
                                onClick = {
                                    if (isDownloaded) {
                                        viewModel.deleteCurrentChapter()
                                    } else {
                                        viewModel.downloadCurrentChapter()
                                    }
                                }
                            ) {
                                if (isDownloaded) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = "Download Chapter")
                                }
                            }
                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Reader Settings")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }

            // LAYER 3: Bottom Bar Floating Overlay
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ReaderControlsBottomBar(
                        preferences = prefs,
                        isEInkDevice = uiState.isEInkDevice,
                        onThemeChanged = { viewModel.updateTheme(it) },
                        onFontSizeChanged = { viewModel.updateFontSize(it) },
                        onTextBrightnessChanged = { viewModel.updateTextBrightness(it) },
                        onManualRefresh = { viewModel.triggerScreenRefresh(context, view) }
                    )
                }
            }

            // Electrophoretic Clear-Flash Overlay
            if (uiState.isPageRefreshFlashing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }

            // Advanced Reader Settings Sheet
            if (showSettingsSheet) {
                ReaderSettingsBottomSheet(
                    preferences = prefs,
                    isEInkDevice = uiState.isEInkDevice,
                    batteryStatus = uiState.batteryStatus,
                    onDismiss = { showSettingsSheet = false },
                    onFontFamilySelected = { viewModel.updateFontFamily(it) },
                    onLineHeightSelected = { viewModel.updateLineHeight(it) },
                    onNavigationModeSelected = { viewModel.updateNavigationMode(it) },
                    onRefreshIntervalSelected = { viewModel.updateRefreshInterval(it) },
                    onVolumeKeysToggle = { viewModel.updateVolumeKeysNavigation(it) },
                    onTextBrightnessSelected = { viewModel.updateTextBrightness(it) }
                )
            }
        }
    }
}

/**
 * Discrete Tap-to-Page View for E-Ink Displays.
 * Provides static rendering of the current page with 3 tap zones (Previous 30%, Controls 40%, Next 30%).
 */
@Composable
fun PaginatedReaderView(
    uiState: ReaderUiState,
    selectedFontFamily: FontFamily,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit
) {
    val prefs = uiState.preferences
    val currentPage = if (uiState.pages.isNotEmpty() && uiState.currentPageIndex in uiState.pages.indices) {
        uiState.pages[uiState.currentPageIndex]
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        // Content rendering (static, zero scroll animation)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = prefs.margins.dp, vertical = 8.dp)
        ) {
            // E-Ink Header: Chapter title + Page X of Y
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.currentChapter?.title ?: "",
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (currentPage != null) {
                    Text(
                        text = "${currentPage.displayPageNumber} / ${currentPage.totalPages}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Paragraphs on this page
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (currentPage != null) {
                    currentPage.paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            fontSize = prefs.fontSize.sp,
                            lineHeight = (prefs.fontSize * prefs.lineHeight).sp,
                            fontFamily = selectedFontFamily,
                            color = textColor,
                            modifier = Modifier.padding(bottom = prefs.paragraphSpacing.dp)
                        )
                    }
                }
            }

            // E-Ink Footer: Reading progress + chapter boundary indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage?.isFirstPage == true && uiState.previousChapterId != null) {
                    Text("« Tap left for Prev Chapter", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (currentPage?.isLastPage == true && uiState.nextChapterId != null) {
                    Text("Tap right for Next Chapter »", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        // Invisible 3-zone Tap Navigation Overlay
        Row(modifier = Modifier.fillMaxSize()) {
            // Left 30%: Previous Page / Chapter
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPrevious
                    )
            )

            // Center 40%: Toggle Controls
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleControls
                    )
            )

            // Right 30%: Next Page / Chapter
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNext
                    )
            )
        }
    }
}

@Composable
fun ReaderControlsBottomBar(
    preferences: ReaderPreferencesEntity,
    isEInkDevice: Boolean,
    onThemeChanged: (String) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onTextBrightnessChanged: (Float) -> Unit = {},
    onManualRefresh: () -> Unit
) {
    val isEInk = preferences.theme.equals("eink", ignoreCase = true)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEInk) Modifier.border(1.dp, BorderEInk) else Modifier
            ),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isEInk) 0.dp else 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (isEInkDevice) {
                Text(
                    text = "E-Ink Display Controller Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Font Size Controls & E-Ink Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Font Size: ${preferences.fontSize.toInt()}sp",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onFontSizeChanged(-2f) },
                        modifier = Modifier.size(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onFontSizeChanged(2f) },
                        modifier = Modifier.size(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("A+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = onManualRefresh,
                        modifier = Modifier.height(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Themes: Light, Dark, Sepia, E-Ink
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val themes = listOf(
                    "light" to "Light",
                    "dark" to "Dark",
                    "sepia" to "Sepia",
                    "eink" to "E-Ink"
                )

                themes.forEach { (key, label) ->
                    FilterChip(
                        selected = preferences.theme.equals(key, ignoreCase = true),
                        onClick = { onThemeChanged(key) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Dark Mode Text Brightness Slider
            if (preferences.theme.equals("dark", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BrightnessLow,
                        contentDescription = "Dim text",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Text Color",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = preferences.textBrightness,
                        onValueChange = onTextBrightnessChanged,
                        valueRange = 0.40f..1.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.BrightnessHigh,
                        contentDescription = "Bright text",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsBottomSheet(
    preferences: ReaderPreferencesEntity,
    isEInkDevice: Boolean,
    batteryStatus: com.drnishanth.novellib.core.eink.BatteryDiagnostics.BatteryStatus?,
    onDismiss: () -> Unit,
    onFontFamilySelected: (String) -> Unit,
    onLineHeightSelected: (Float) -> Unit,
    onNavigationModeSelected: (String) -> Unit,
    onRefreshIntervalSelected: (Int) -> Unit,
    onVolumeKeysToggle: (Boolean) -> Unit,
    onTextBrightnessSelected: (Float) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Reader & Hardware Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Dark Mode Text Brightness Slider
            if (preferences.theme.equals("dark", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("Dark Mode Text Brightness", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Adjust text from soft light grey to pure white to reduce eye strain",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BrightnessLow,
                        contentDescription = "Dim text",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = preferences.textBrightness,
                        onValueChange = onTextBrightnessSelected,
                        valueRange = 0.40f..1.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.BrightnessHigh,
                        contentDescription = "Bright text",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // E-Ink Device Diagnostics Badge
            if (isEInkDevice) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "⚡ E-Ink Reader Detected (Bigme / Onyx Boox Mode)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (batteryStatus != null) {
                            Text(
                                text = "Battery: ${batteryStatus.batteryLevel}% ${if (batteryStatus.isCharging) "(Charging)" else ""}",
                                fontSize = 11.sp
                            )
                            batteryStatus.recommendations.forEach { rec ->
                                Text("• $rec", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Mode: Scroll vs Paging
            Text("Navigation Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf("scroll" to "Continuous Scroll", "paging" to "Tap to Page (E-Ink)")
                modes.forEach { (mode, label) ->
                    FilterChip(
                        selected = preferences.pageNavigationMode.equals(mode, ignoreCase = true),
                        onClick = { onNavigationModeSelected(mode) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // E-Ink Screen Full-Refresh Interval
            Text("E-Ink Ghosting Auto-Refresh", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Automatically clears E-Ink display residue every N page turns",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val intervals = listOf(0 to "Off", 1 to "Every 1", 5 to "Every 5", 10 to "Every 10", 20 to "Every 20")
                intervals.forEach { (inv, label) ->
                    FilterChip(
                        selected = preferences.einkFullRefreshInterval == inv,
                        onClick = { onRefreshIntervalSelected(inv) },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Volume Keys / Hardware Buttons Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hardware Page Buttons", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Turn pages using physical volume buttons and e-reader keys",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = preferences.volumeKeysNavigation,
                    onCheckedChange = { onVolumeKeysToggle(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font Family
            Text("Font Family", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fonts = listOf("serif" to "Serif", "sans" to "Sans", "mono" to "Monospace")
                fonts.forEach { (key, label) ->
                    FilterChip(
                        selected = preferences.fontFamily.equals(key, ignoreCase = true),
                        onClick = { onFontFamilySelected(key) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Line Height
            Text("Line Height", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val heights = listOf(1.4f to "Compact", 1.6f to "Normal", 1.8f to "Spacious")
                heights.forEach { (h, label) ->
                    FilterChip(
                        selected = preferences.lineHeight == h,
                        onClick = { onLineHeightSelected(h) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AddictionLockoutScreen(
    novelTitle: String,
    limit: Int,
    lockedUntil: Long,
    onBack: () -> Unit,
    onExpired: () -> Unit
) {
    var remainingMs by remember(lockedUntil) {
        mutableStateOf(maxOf(0L, lockedUntil - System.currentTimeMillis()))
    }

    LaunchedEffect(lockedUntil) {
        while (remainingMs > 0L) {
            kotlinx.coroutines.delay(1000L)
            remainingMs = maxOf(0L, lockedUntil - System.currentTimeMillis())
        }
        onExpired()
    }

    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeFormatted = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Reading Limit Reached",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (novelTitle.isNotBlank()) {
                "You've reached your session limit of $limit chapter(s) for \"$novelTitle\"."
            } else {
                "You've reached your session limit of $limit chapter(s) for this novel."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Novel unlocks in",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Take a break and let your mind unwind!\nOther novels in your library remain accessible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back to Library")
        }
    }
}
