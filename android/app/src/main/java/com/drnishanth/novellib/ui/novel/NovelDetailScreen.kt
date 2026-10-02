package com.drnishanth.novellib.ui.novel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.utils.HtmlSanitizer
import com.drnishanth.novellib.downloads.models.ChapterDownloadStatus
import com.drnishanth.novellib.downloads.models.DownloadState
import com.drnishanth.novellib.ui.reader.ReaderScreen
import com.drnishanth.novellib.ui.reader.ReaderViewModel
import kotlinx.coroutines.launch

enum class NovelDetailTab(val title: String, val icon: ImageVector) {
    SYNOPSIS("Synopsis", Icons.Default.Description),
    CHAPTERS("Chapters", Icons.Default.FormatListNumbered),
    READER("Reader", Icons.Default.AutoStories)
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun NovelDetailScreen(
    viewModel: NovelDetailViewModel,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit = {}
) {
    val novel by viewModel.novel.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val progress by viewModel.readingProgress.collectAsState()
    val readChapterIds by viewModel.readChapterIds.collectAsState()
    val isAscending by viewModel.isAscending.collectAsState()
    val libraryEntry by viewModel.libraryEntry.collectAsState()
    val downloadStatuses by viewModel.downloadStatuses.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInputText by remember { mutableStateOf("") }

    val sortedChapters = remember(chapters, isAscending) {
        if (isAscending) chapters else chapters.reversed()
    }

    val currentProgressChapter = remember(chapters, progress) {
        chapters.firstOrNull { it.id == progress?.chapterId }
    }
    val currentProgressNumber = currentProgressChapter?.chapterNumber

    fun isChapterRead(chapter: ChapterEntity): Boolean {
        if (chapter.id in readChapterIds) return true
        if (currentProgressNumber != null && chapter.chapterNumber < currentProgressNumber) return true
        if (chapter.id == progress?.chapterId && (progress?.progressPercent ?: 0f) >= 0.9f) return true
        return false
    }

    val readCount = remember(chapters, readChapterIds, currentProgressNumber, progress) {
        chapters.count { isChapterRead(it) }
    }

    val activeChapterId = remember(progress, chapters) {
        progress?.chapterId ?: chapters.firstOrNull()?.id ?: ""
    }

    val readerViewModel = viewModel<ReaderViewModel>(
        key = "reader-vm-${viewModel.novelId}",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(viewModel.novelId, activeChapterId) as T
            }
        }
    )
    val readerUiState by readerViewModel.uiState.collectAsState()

    // Android back navigation handling
    BackHandler(enabled = pagerState.currentPage != 0) {
        coroutineScope.launch {
            if (pagerState.currentPage == 2) {
                pagerState.animateScrollToPage(1)
            } else {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val showBottomBar = pagerState.currentPage != 2 || readerUiState.showControls

    Scaffold(
        topBar = {
            if (pagerState.currentPage != 2) {
                TopAppBar(
                    title = {
                        Text(
                            text = novel?.title ?: "Novel Details",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.checkForUpdates() },
                            enabled = !uiState.isCheckingUpdates
                        ) {
                            if (uiState.isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Check for Updates")
                            }
                        }
                        val notificationsEnabled = libraryEntry?.notificationsEnabled ?: true
                        IconButton(onClick = { viewModel.toggleNotifications() }) {
                            Icon(
                                if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = if (notificationsEnabled) "Disable Notifications" else "Enable Notifications",
                                tint = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showRemoveConfirmDialog = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove from Library",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationBar {
                    NovelDetailTab.values().forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = pagerState.currentPage != 2,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        // TAB 0: SYNOPSIS & METADATA
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(16.dp)
                        ) {
                            novel?.let { n ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Vertical Paperback Book Cover
                                    Card(
                                        modifier = Modifier
                                            .width(115.dp)
                                            .aspectRatio(0.68f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!n.coverUrl.isNullOrBlank()) {
                                                SubcomposeAsyncImage(
                                                    model = n.coverUrl,
                                                    contentDescription = n.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                    loading = {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                                        }
                                                    },
                                                    error = {
                                                        Icon(
                                                            Icons.Default.Book,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(44.dp),
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Book,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(44.dp),
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = n.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "By ${n.author}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = n.status,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Reading Action Button
                                val targetResumeId = progress?.chapterId ?: chapters.firstOrNull()?.id
                                if (targetResumeId != null) {
                                    Button(
                                        onClick = {
                                            readerViewModel.loadChapter(targetResumeId)
                                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Book, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (progress != null && currentProgressNumber != null) {
                                                "Resume Reading (Ch. $currentProgressNumber)"
                                            } else {
                                                "Start Reading"
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Statistics & Counters Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Reading Overview",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        val downloadedCount = chapters.count { it.downloadState == "available" }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            StatItem(label = "Total", count = "${chapters.size}", color = MaterialTheme.colorScheme.primary)
                                            StatItem(label = "Read", count = "$readCount", color = Color(0xFF2ECC71))
                                            StatItem(label = "Unread", count = "${chapters.size - readCount}", color = Color(0xFFF39C12))
                                            StatItem(label = "Downloaded", count = "$downloadedCount", color = Color(0xFF3498DB))
                                        }
                                    }
                                }

                                val warnings = tags.filter { it.isWarning }
                                val normalTags = tags.filter { !it.isWarning }

                                if (warnings.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Content Warnings",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (w in warnings) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(w.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                    }
                                }

                                if (normalTags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Tags & Genres",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (t in normalTags) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(t.name, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Storage Mode Chip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentMode = libraryEntry?.downloadMode ?: "hybrid"
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.showStoragePolicyDialog() },
                                        label = {
                                            Text(
                                                "Storage Mode: ${currentMode.replaceFirstChar { it.uppercase() }}",
                                                fontSize = 12.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Synopsis text
                                if (n.description.isNotBlank()) {
                                    Text(
                                        text = "Synopsis",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val cleanSynopsis = remember(n.description) {
                                        HtmlSanitizer.cleanHtmlSynopsis(n.description)
                                    }
                                    Text(
                                        text = cleanSynopsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: CHAPTERS LIST WITH HORIZONTAL SLIDER
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Chapters Control Bar
                            Surface(
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val currentMode = libraryEntry?.downloadMode ?: "hybrid"
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.showStoragePolicyDialog() },
                                        label = {
                                            Text(currentMode.take(4).uppercase(), fontSize = 11.sp)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )

                                    OutlinedButton(
                                        onClick = { viewModel.toggleSortOrder() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = "Toggle Sort Order",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isAscending) "1 → N" else "N → 1", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            jumpInputText = ""
                                            showJumpDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Jump #", fontSize = 11.sp)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = { viewModel.downloadAllChapters() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("All", fontSize = 11.sp)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteNovelDownloads() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Clear Downloads",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Lazy chapter list
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                items(sortedChapters) { chapter ->
                                    val status = downloadStatuses[chapter.id]
                                    val chapterRead = isChapterRead(chapter)
                                    ChapterRowWithDownload(
                                        chapter = chapter,
                                        liveStatus = status,
                                        isCurrentProgress = chapter.id == progress?.chapterId,
                                        isRead = chapterRead,
                                        onOpen = {
                                            readerViewModel.loadChapter(chapter.id)
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(2)
                                            }
                                        },
                                        onDownload = { viewModel.downloadChapter(chapter.id) },
                                        onDelete = { viewModel.deleteChapterDownload(chapter.id) },
                                        onToggleRead = { viewModel.toggleChapterRead(chapter.id, chapterRead) }
                                    )
                                }
                            }

                            // Bottom Chapter Horizontal Scroll Slider Extension Strip
                            if (sortedChapters.size > 1) {
                                ChapterHorizontalSliderStrip(
                                    chapters = sortedChapters,
                                    listState = listState,
                                    coroutineScope = coroutineScope
                                )
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: READER TAB
                        Box(modifier = Modifier.fillMaxSize()) {
                            ReaderScreen(
                                viewModel = readerViewModel,
                                onBack = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )

                            if (readerUiState.isLoading) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "Opening chapter content...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Storage Policy Configuration Dialog
            if (uiState.showStoragePolicyDialog) {
                StoragePolicyDialog(
                    currentMode = libraryEntry?.downloadMode ?: "hybrid",
                    currentLimit = libraryEntry?.downloadLimit ?: 10,
                    currentAutoDownload = libraryEntry?.autoDownloadEnabled ?: true,
                    onDismiss = { viewModel.dismissStoragePolicyDialog() },
                    onSave = { mode, limit, autoDownload ->
                        viewModel.updateStoragePolicy(mode, limit, autoDownload)
                    }
                )
            }

            // Jump to Chapter Number Dialog
            if (showJumpDialog) {
                AlertDialog(
                    onDismissRequest = { showJumpDialog = false },
                    title = { Text("Jump to Chapter") },
                    text = {
                        Column {
                            Text("Enter chapter number (1 to ${chapters.size}):")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = jumpInputText,
                                onValueChange = { jumpInputText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Chapter #") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetNum = jumpInputText.toIntOrNull()
                                if (targetNum != null) {
                                    val index = sortedChapters.indexOfFirst {
                                        if (isAscending) it.chapterNumber >= targetNum else it.chapterNumber <= targetNum
                                    }
                                    if (index != -1) {
                                        coroutineScope.launch {
                                            listState.scrollToItem(index)
                                        }
                                    }
                                }
                                showJumpDialog = false
                            },
                            enabled = jumpInputText.isNotBlank()
                        ) {
                            Text("Jump")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showJumpDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Remove from Library Confirmation Dialog
            if (showRemoveConfirmDialog) {
                var alsoDeleteDownloads by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { showRemoveConfirmDialog = false },
                    title = { Text("Remove from Library") },
                    text = {
                        Column {
                            Text("Are you sure you want to remove \"${novel?.title ?: "this novel"}\" from your library?")
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { alsoDeleteDownloads = !alsoDeleteDownloads }
                            ) {
                                Checkbox(
                                    checked = alsoDeleteDownloads,
                                    onCheckedChange = { alsoDeleteDownloads = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Also delete downloaded chapters", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.removeNovelFromLibrary(alsoDeleteDownloads) {
                                    showRemoveConfirmDialog = false
                                    onBack()
                                }
                            }
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChapterHorizontalSliderStrip(
    chapters: List<ChapterEntity>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val listScrollFraction by remember {
        derivedStateOf {
            val total = chapters.size
            if (total <= 1) 0f
            else (listState.firstVisibleItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)
        }
    }

    val effectiveFraction = if (isDragging) dragFraction else listScrollFraction
    val targetIndex = (effectiveFraction * (chapters.size - 1)).toInt().coerceIn(0, chapters.size - 1)
    val targetChapter = chapters.getOrNull(targetIndex)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scroll to Chapter:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ch. ${targetChapter?.chapterNumber ?: (targetIndex + 1)} (${targetIndex + 1}/${chapters.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch { listState.scrollToItem(0) }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.FirstPage,
                        contentDescription = "First Chapter",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Slider(
                    value = effectiveFraction,
                    onValueChange = { fraction ->
                        isDragging = true
                        dragFraction = fraction
                        val idx = (fraction * (chapters.size - 1)).toInt().coerceIn(0, chapters.size - 1)
                        coroutineScope.launch {
                            listState.scrollToItem(idx)
                        }
                    },
                    onValueChangeFinished = {
                        isDragging = false
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = {
                        coroutineScope.launch { listState.scrollToItem(chapters.size - 1) }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.LastPage,
                        contentDescription = "Last Chapter",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChapterRowWithDownload(
    chapter: ChapterEntity,
    liveStatus: ChapterDownloadStatus?,
    isCurrentProgress: Boolean,
    isRead: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onToggleRead: () -> Unit
) {
    val currentState = liveStatus?.state ?: chapter.downloadState

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentProgress) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Read status dot (Green for read, Orange for unread)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isRead) Color(0xFF2ECC71) else Color(0xFFF39C12))
                    .clickable(onClick = onToggleRead)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "${chapter.chapterNumber}.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(36.dp)
            )

            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            when (currentState) {
                DownloadState.AVAILABLE.value -> {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Downloaded - click to delete",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DownloadState.DOWNLOADING.value -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                DownloadState.QUEUED.value -> {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Queued",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DownloadState.FAILED.value -> {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Download Failed - retry",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                else -> {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download Chapter",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoragePolicyDialog(
    currentMode: String,
    currentLimit: Int,
    currentAutoDownload: Boolean,
    onDismiss: () -> Unit,
    onSave: (mode: String, limit: Int, autoDownload: Boolean) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    var selectedLimit by remember { mutableIntStateOf(currentLimit) }
    var autoDownload by remember { mutableStateOf(currentAutoDownload) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novel Storage Policy") },
        text = {
            Column {
                Text("Select chapter storage behavior:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("online" to "Online", "hybrid" to "Hybrid", "offline" to "Offline")
                    modes.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMode.equals(key, ignoreCase = true),
                            onClick = { selectedMode = key },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedMode.equals("hybrid", ignoreCase = true)) {
                    Text("Pre-download next unread chapters:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val limits = listOf(5, 10, 25, 50)
                        limits.forEach { limit ->
                            FilterChip(
                                selected = selectedLimit == limit,
                                onClick = { selectedLimit = limit },
                                label = { Text("$limit ch", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-download as you read", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoDownload,
                        onCheckedChange = { autoDownload = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedMode, selectedLimit, autoDownload) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
