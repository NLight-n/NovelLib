package com.drnishanth.novellib.ui.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.core.utils.HtmlSanitizer
import com.drnishanth.novellib.downloads.models.ChapterDownloadStatus
import com.drnishanth.novellib.downloads.models.DownloadState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    viewModel: NovelDetailViewModel,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit
) {
    val novel by viewModel.novel.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val progress by viewModel.readingProgress.collectAsState()
    val readChapterIds by viewModel.readChapterIds.collectAsState()
    val isAscending by viewModel.isAscending.collectAsState()
    val libraryEntry by viewModel.libraryEntry.collectAsState()
    val downloadStatuses by viewModel.downloadStatuses.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = novel?.title ?: "Novel",
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
                    IconButton(onClick = { viewModel.showStoragePolicyDialog() }) {
                        Icon(Icons.Default.Tune, contentDescription = "Storage Policy")
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                item {
                    novel?.let { n ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .width(92.dp)
                                        .height(132.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                                        modifier = Modifier.size(40.dp),
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                    )
                                                }
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Book,
                                                contentDescription = null,
                                                modifier = Modifier.size(40.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(132.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = n.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "By ${n.author}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = n.status,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            // Storage Mode Badge & Controls
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
                                            "Mode: ${currentMode.replaceFirstChar { it.uppercase() }}",
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

                                Row {
                                    OutlinedButton(
                                        onClick = { viewModel.downloadAllChapters() },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download All", fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteNovelDownloads() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Clear Downloads", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Start or Resume Button
                            val resumeChapterId = progress?.chapterId ?: chapters.firstOrNull()?.id
                            if (resumeChapterId != null) {
                                Button(
                                    onClick = { onOpenChapter(resumeChapterId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Book, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (progress != null) "Resume Reading" else "Start Reading")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            if (n.description.isNotBlank()) {
                                Text(
                                    text = "Synopsis",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val cleanSynopsis = remember(n.description) {
                                    HtmlSanitizer.cleanHtmlSynopsis(n.description)
                                }
                                Text(
                                    text = cleanSynopsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            val downloadedCount = chapters.count { it.downloadState == "available" }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Chapters (${chapters.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2ECC71))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$readCount read  •  $downloadedCount downloaded",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFF39C12))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${chapters.size - readCount} unread",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = {
                                            jumpInputText = ""
                                            showJumpDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Jump #", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.toggleSortOrder() },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = "Toggle Sort Order",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isAscending) "1 → N" else "N → 1", fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                items(sortedChapters) { chapter ->
                    val status = downloadStatuses[chapter.id]
                    val chapterRead = isChapterRead(chapter)
                    ChapterRowWithDownload(
                        chapter = chapter,
                        liveStatus = status,
                        isCurrentProgress = chapter.id == progress?.chapterId,
                        isRead = chapterRead,
                        onOpen = { onOpenChapter(chapter.id) },
                        onDownload = { viewModel.downloadChapter(chapter.id) },
                        onDelete = { viewModel.deleteChapterDownload(chapter.id) },
                        onToggleRead = { viewModel.toggleChapterRead(chapter.id, chapterRead) }
                    )
                }
            }

            FastScrollbarWithChapterBadge(
                listState = listState,
                chapters = sortedChapters,
                coroutineScope = coroutineScope,
                modifier = Modifier.align(Alignment.CenterEnd)
            )

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
                                showRemoveConfirmDialog = false
                                viewModel.removeNovelFromLibrary(deleteDownloads = alsoDeleteDownloads) {
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

            // Jump to Chapter Number Dialog
            if (showJumpDialog) {
                AlertDialog(
                    onDismissRequest = { showJumpDialog = false },
                    title = { Text("Jump to Chapter") },
                    text = {
                        Column {
                            Text(
                                text = "Enter chapter number (1 to ${chapters.size}):",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = jumpInputText,
                                onValueChange = { jumpInputText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Chapter Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetNum = jumpInputText.toIntOrNull()
                                if (targetNum != null && sortedChapters.isNotEmpty()) {
                                    val targetIndex = sortedChapters.indexOfFirst {
                                        if (isAscending) it.chapterNumber >= targetNum else it.chapterNumber <= targetNum
                                    }.takeIf { it >= 0 } ?: (sortedChapters.size - 1)
                                    coroutineScope.launch {
                                        // item 0 is the novel header card, chapter items start at item 1
                                        listState.animateScrollToItem(1 + targetIndex)
                                    }
                                    showJumpDialog = false
                                    jumpInputText = ""
                                }
                            }
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
    onToggleRead: () -> Unit = {}
) {
    val currentState = liveStatus?.state?.value ?: chapter.downloadState

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentProgress) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Read/Unread Visual Indicator Dot: Green (#2ECC71) for Read, Yellow/Orange (#F39C12) for Unread
            Box(
                modifier = Modifier
                    .size(9.dp)
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

@Composable
fun FastScrollbarWithChapterBadge(
    listState: LazyListState,
    chapters: List<ChapterEntity>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    if (chapters.size < 10) return

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Natural scroll fraction derived from LazyListState
    val scrollFraction by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) 0f
            else {
                val firstVisible = listState.firstVisibleItemIndex
                (firstVisible.toFloat() / (totalItems - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }

    val effectiveFraction = if (isDragging) dragFraction else scrollFraction
    val currentChapterIndex = remember(effectiveFraction, chapters.size) {
        (effectiveFraction * (chapters.size - 1)).toInt().coerceIn(0, chapters.size - 1)
    }
    val currentChapter = chapters.getOrNull(currentChapterIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(44.dp)
            .padding(vertical = 16.dp)
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightDp = 44.dp
        val density = LocalDensity.current
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
        val maxThumbTravel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        val thumbOffsetY = with(density) {
            (effectiveFraction * maxThumbTravel).toDp()
        }

        val badgeOffsetY = with(density) {
            (effectiveFraction * trackHeightPx - 24.dp.toPx()).coerceIn(0f, (trackHeightPx - 48.dp.toPx()).coerceAtLeast(0f)).toDp()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chapters.size, trackHeightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        var touchY = down.position.y.coerceIn(0f, trackHeightPx)
                        dragFraction = (touchY / trackHeightPx).coerceIn(0f, 1f)
                        val idx = (dragFraction * (chapters.size - 1)).toInt().coerceIn(0, chapters.size - 1)
                        coroutineScope.launch {
                            listState.scrollToItem(1 + idx)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                break
                            }
                            change.consume()
                            touchY = change.position.y.coerceIn(0f, trackHeightPx)
                            dragFraction = (touchY / trackHeightPx).coerceIn(0f, 1f)
                            val targetIdx = (dragFraction * (chapters.size - 1)).toInt().coerceIn(0, chapters.size - 1)
                            coroutineScope.launch {
                                listState.scrollToItem(1 + targetIdx)
                            }
                        }
                        isDragging = false
                    }
                }
        ) {
            // Track line (subtle vertical guide on right side)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDragging) 0.25f else 0.08f))
            )

            // Scrollbar Thumb (pill handle)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffsetY)
                    .padding(end = 2.dp)
                    .width(8.dp)
                    .height(thumbHeightDp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
            )

            // Floating Tooltip Badge showing Chapter Number while dragging or active
            AnimatedVisibility(
                visible = isDragging && currentChapter != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-32).dp, y = badgeOffsetY)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ch. ${currentChapter?.chapterNumber ?: (currentChapterIndex + 1)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
