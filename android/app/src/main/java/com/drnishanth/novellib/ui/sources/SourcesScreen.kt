package com.drnishanth.novellib.ui.sources

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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drnishanth.novellib.core.database.entities.SourceDefinitionEntity

import androidx.compose.material.icons.filled.Public

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit = {}
) {
    val installed by viewModel.installedDefinitions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar("Error: $it")
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Website Sources & Registry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onOpenBrowser("https://www.royalroad.com") }
                    ) {
                        Icon(Icons.Default.Public, contentDescription = "Open In-App Browser")
                    }
                    IconButton(
                        onClick = { viewModel.checkForUpdates() },
                        enabled = !uiState.isCheckingUpdates
                    ) {
                        if (uiState.isCheckingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for Updates")
                        }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Registry Source Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "GitHub Remote Registry",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.registryUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                enabled = !uiState.isCheckingUpdates,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (uiState.isCheckingUpdates) "Checking..." else "Check for Updates")
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Installed Sources (${installed.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(installed) { definition ->
                    val updateStatus = uiState.updateStatuses.firstOrNull { it.sourceId == definition.id }
                    SourceItemCard(
                        definition = definition,
                        updateStatus = updateStatus,
                        isUpdating = uiState.isUpdatingSourceId == definition.id,
                        onBrowse = { onOpenBrowser(resolveBrowseUrl(definition)) },
                        onUpdate = { viewModel.updateSource(definition.id) },
                        onRollback = { viewModel.rollbackSource(definition.id) },
                        onToggleEnabled = { viewModel.toggleSourceEnabled(definition.id, definition.enabled) }
                    )
                }
            }
        }
    }
}

private fun resolveBrowseUrl(definition: SourceDefinitionEntity): String {
    return when (definition.id) {
        "royalroad" -> "https://www.royalroad.com"
        "novgo" -> "https://novgo.net"
        "scribblehub" -> "https://www.scribblehub.com"
        else -> {
            try {
                val json = org.json.JSONObject(definition.jsonContent)
                val domains = json.getJSONObject("match").getJSONArray("domains")
                if (domains.length() > 0) "https://${domains.getString(0)}" else "https://www.royalroad.com"
            } catch (_: Exception) {
                "https://www.royalroad.com"
            }
        }
    }
}

@Composable
fun SourceItemCard(
    definition: SourceDefinitionEntity,
    updateStatus: com.drnishanth.novellib.scraping.registry.SourceUpdateStatus?,
    isUpdating: Boolean,
    onBrowse: () -> Unit,
    onUpdate: () -> Unit,
    onRollback: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = definition.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${definition.version}  •  ${definition.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = definition.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            definition.description?.let { desc ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            if (definition.consecutiveFailures > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${definition.consecutiveFailures} consecutive scrape failure(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onBrowse,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Browse", fontSize = 12.sp)
                }

                if (definition.previousJsonContent != null) {
                    OutlinedButton(
                        onClick = onRollback,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rollback (v${definition.previousVersion})", fontSize = 12.sp)
                    }
                }

                if (updateStatus != null && updateStatus.hasUpdate) {
                    Button(
                        onClick = onUpdate,
                        enabled = !isUpdating
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Update to v${updateStatus.latestVersion}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
