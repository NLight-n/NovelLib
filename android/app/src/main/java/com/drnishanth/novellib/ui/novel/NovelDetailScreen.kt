package com.drnishanth.novellib.ui.novel

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drnishanth.novellib.core.database.entities.ChapterEntity
import com.drnishanth.novellib.downloads.models.ChapterDownloadStatus
import com.drnishanth.novellib.downloads.models.DownloadState

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
    val libraryEntry by viewModel.libraryEntry.collectAsState()
    val downloadStatuses by viewModel.downloadStatuses.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(novel?.title ?: "Novel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showStoragePolicyDialog() }) {
                        Icon(Icons.Default.Tune, contentDescription = "Storage Policy")
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                item {
                    novel?.let { n ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = n.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "By ${n.author}  •  ${n.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

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
                                Text(
                                    text = n.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            val downloadedCount = chapters.count { it.downloadState == "available" }
                            Text(
                                text = "Chapters (${chapters.size})  •  $downloadedCount downloaded",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                items(chapters) { chapter ->
                    val status = downloadStatuses[chapter.id]
                    ChapterRowWithDownload(
                        chapter = chapter,
                        liveStatus = status,
                        isCurrentProgress = chapter.id == progress?.chapterId,
                        onOpen = { onOpenChapter(chapter.id) },
                        onDownload = { viewModel.downloadChapter(chapter.id) },
                        onDelete = { viewModel.deleteChapterDownload(chapter.id) }
                    )
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
        }
    }
}

@Composable
fun ChapterRowWithDownload(
    chapter: ChapterEntity,
    liveStatus: ChapterDownloadStatus?,
    isCurrentProgress: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val currentState = liveStatus?.state?.value ?: chapter.downloadState

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
