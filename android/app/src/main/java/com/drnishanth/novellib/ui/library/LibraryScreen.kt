package com.drnishanth.novellib.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.drnishanth.novellib.core.database.dao.NovelWithEntry
import com.drnishanth.novellib.ui.profiles.EditProfileDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNovelSelected: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSources: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenBrowser: (String) -> Unit = {}
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val novels by viewModel.novels.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val showEditProfile by viewModel.showEditProfileDialog.collectAsState()
    val editProfileError by viewModel.editProfileError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var novelPendingRemoval by remember { mutableStateOf<NovelWithEntry?>(null) }

    LaunchedEffect(uiState.importSuccessMessage) {
        uiState.importSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Novel Library",
                            style = MaterialTheme.typography.titleMedium
                        )
                        activeProfile?.let { profile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.openEditProfile() }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Hi @${profile.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkAllUpdates() },
                        enabled = !uiState.isRefreshing
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check All Updates")
                        }
                    }
                    IconButton(onClick = { onOpenBrowser("https://www.royalroad.com") }) {
                        Icon(Icons.Default.Public, contentDescription = "Browse Sources")
                    }
                    IconButton(onClick = onOpenSources) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Manage Sources")
                    }
                    IconButton(onClick = onOpenSync) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Devices")
                    }
                    IconButton(onClick = {
                        viewModel.switchProfile()
                        onSwitchProfile()
                    }) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = "Switch Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddNovelDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Add Novel")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (novels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Your library is empty",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Explore web fiction catalogs or add novel URLs directly to your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.showAddNovelDialog() }) {
                        Text("Add Novel by URL")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onOpenBrowser("https://www.royalroad.com") }) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Explore Sources in App")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(novels) { novel ->
                        NovelGridCard(
                            novel = novel,
                            onClick = { onNovelSelected(novel.id) },
                            onDelete = { novelPendingRemoval = novel }
                        )
                    }
                }
            }

            if (uiState.showAddDialog) {
                AddNovelDialog(
                    isImporting = uiState.isImporting,
                    errorMessage = uiState.importError,
                    onDismiss = { viewModel.dismissAddNovelDialog() },
                    onImport = { url -> viewModel.importNovel(url) }
                )
            }

            novelPendingRemoval?.let { novel ->
                var alsoDeleteDownloads by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { novelPendingRemoval = null },
                    title = { Text("Remove from Library") },
                    text = {
                        Column {
                            Text("Are you sure you want to remove \"${novel.title}\" from your library?")
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
                                viewModel.removeNovelFromLibrary(novel.id, alsoDeleteDownloads)
                                novelPendingRemoval = null
                            }
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { novelPendingRemoval = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showEditProfile && activeProfile != null) {
                EditProfileDialog(
                    profile = activeProfile!!,
                    canDelete = false,
                    errorMessage = editProfileError,
                    onDismiss = { viewModel.closeEditProfile() },
                    onSave = { newUsername, newDisplayName, currentPass, newPass, removePass, blockedTags ->
                        viewModel.updateProfile(
                            newUsername = newUsername,
                            newDisplayName = newDisplayName,
                            currentPassword = currentPass,
                            newPassword = newPass,
                            removePassword = removePass,
                            blockedTags = blockedTags
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun NovelGridCard(
    novel: NovelWithEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (!novel.coverUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = novel.coverUrl,
                        contentDescription = novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Remove from Library",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = novel.title,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = novel.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AddNovelDialog(
    isImporting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onImport: (url: String) -> Unit
) {
    var url by remember {
        mutableStateOf("https://www.royalroad.com/fiction/21220/mother-of-learning")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Novel to Library") },
        text = {
            Column {
                Text(
                    "Paste novel URL from a supported website (e.g. Royal Road):",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Novel URL") },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isImporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Scraping novel & chapters...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(url) },
                enabled = url.isNotBlank() && !isImporting
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("Cancel")
            }
        }
    )
}
