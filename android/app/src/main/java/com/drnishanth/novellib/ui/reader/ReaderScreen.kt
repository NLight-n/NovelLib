package com.drnishanth.novellib.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drnishanth.novellib.core.database.entities.ReaderPreferencesEntity
import com.drnishanth.novellib.ui.theme.NovelLibTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs = uiState.preferences
    var showSettingsSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.initialScrollIndex
    )

    // Save reading position when user scrolls
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                viewModel.saveScrollIndex(index)
            }
    }

    // Scroll to initial saved position when chapter loads
    LaunchedEffect(uiState.initialScrollIndex) {
        if (uiState.initialScrollIndex > 0 && uiState.initialScrollIndex < uiState.paragraphs.size) {
            listState.scrollToItem(uiState.initialScrollIndex)
        }
    }

    val selectedFontFamily = when (prefs.fontFamily.lowercase()) {
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }

    NovelLibTheme(themeName = prefs.theme) {
        Scaffold(
            topBar = {
                if (uiState.showControls) {
                    TopAppBar(
                        title = {
                            Text(
                                text = uiState.currentChapter?.title ?: "Reader",
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
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
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (uiState.showControls) {
                    ReaderControlsBottomBar(
                        preferences = prefs,
                        onThemeChanged = { viewModel.updateTheme(it) },
                        onFontSizeChanged = { viewModel.updateFontSize(it) }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                } else if (uiState.errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error loading chapter",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            uiState.currentChapter?.let { viewModel.loadChapter(it.id) }
                        }) {
                            Text("Retry")
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = prefs.margins.dp, vertical = 12.dp)
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
                                    color = MaterialTheme.colorScheme.onBackground,
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
                                color = MaterialTheme.colorScheme.onBackground,
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

                // Advanced Reader Settings Sheet
                if (showSettingsSheet) {
                    ReaderSettingsBottomSheet(
                        preferences = prefs,
                        onDismiss = { showSettingsSheet = false },
                        onFontFamilySelected = { viewModel.updateFontFamily(it) },
                        onLineHeightSelected = { viewModel.updateLineHeight(it) },
                        onNavigationModeSelected = { viewModel.updateNavigationMode(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderControlsBottomBar(
    preferences: ReaderPreferencesEntity,
    onThemeChanged: (String) -> Unit,
    onFontSizeChanged: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Font Size Controls
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onFontSizeChanged(2f) },
                        modifier = Modifier.size(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("A+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsBottomSheet(
    preferences: ReaderPreferencesEntity,
    onDismiss: () -> Unit,
    onFontFamilySelected: (String) -> Unit,
    onLineHeightSelected: (Float) -> Unit,
    onNavigationModeSelected: (String) -> Unit
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
                text = "Reader Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Font Family
            Text("Font Family", style = MaterialTheme.typography.bodyMedium)
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
            Text("Line Height", style = MaterialTheme.typography.bodyMedium)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Mode: Scroll vs Paging
            Text("Navigation Mode", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf("scroll" to "Continuous Scroll", "paging" to "Page Turns")
                modes.forEach { (mode, label) ->
                    FilterChip(
                        selected = preferences.pageNavigationMode.equals(mode, ignoreCase = true),
                        onClick = { onNavigationModeSelected(mode) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
