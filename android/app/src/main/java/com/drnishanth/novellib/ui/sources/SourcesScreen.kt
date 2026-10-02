package com.drnishanth.novellib.ui.sources

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
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
    onBack: (() -> Unit)? = null,
    onOpenBrowser: (String) -> Unit = {}
) {
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
                title = { Text("Website Sources") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
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
        SourcesContent(
            viewModel = viewModel,
            onOpenBrowser = onOpenBrowser,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@Composable
fun SourcesContent(
    viewModel: SourcesViewModel,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val installed by viewModel.installedDefinitions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                        Text(if (uiState.isCheckingUpdates) "Checking for updates..." else "Check for Updates")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Installed Sources (${installed.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Tap card to browse",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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

fun resolveBrowseUrl(definition: SourceDefinitionEntity): String {
    return when (definition.id.lowercase()) {
        "royalroad" -> "https://www.royalroad.com"
        "novgo" -> "https://novgo.net"
        "scribblehub" -> "https://www.scribblehub.com"
        "litfic" -> "https://litfic.com/browse"
        "tapas" -> "https://tapas.io"
        "novelupdates" -> "https://www.novelupdates.com"
        else -> {
            try {
                val json = org.json.JSONObject(definition.jsonContent)
                val match = json.getJSONObject("match")
                val hosts = match.optJSONArray("hosts")
                if (hosts != null && hosts.length() > 0) "https://${hosts.getString(0)}" else "https://www.royalroad.com"
            } catch (_: Exception) {
                "https://www.royalroad.com"
            }
        }
    }
}

data class SourceBrandStyle(
    val domain: String,
    val monogram: String,
    val primaryColor: Color,
    val secondaryColor: Color
)

fun resolveBrandStyle(id: String, name: String, jsonContent: String): SourceBrandStyle {
    return when (id.lowercase()) {
        "royalroad" -> SourceBrandStyle(
            domain = "royalroad.com",
            monogram = "RR",
            primaryColor = Color(0xFF1E3A8A),
            secondaryColor = Color(0xFFD97706)
        )
        "scribblehub" -> SourceBrandStyle(
            domain = "scribblehub.com",
            monogram = "SH",
            primaryColor = Color(0xFF7C3AED),
            secondaryColor = Color(0xFFEC4899)
        )
        "novgo" -> SourceBrandStyle(
            domain = "novgo.net",
            monogram = "NG",
            primaryColor = Color(0xFF059669),
            secondaryColor = Color(0xFF0D9488)
        )
        "litfic" -> SourceBrandStyle(
            domain = "litfic.com",
            monogram = "LF",
            primaryColor = Color(0xFFEA580C),
            secondaryColor = Color(0xFFDC2626)
        )
        "tapas" -> SourceBrandStyle(
            domain = "tapas.io",
            monogram = "T",
            primaryColor = Color(0xFFF59E0B),
            secondaryColor = Color(0xFFB45309)
        )
        "novelupdates" -> SourceBrandStyle(
            domain = "novelupdates.com",
            monogram = "NU",
            primaryColor = Color(0xFF2563EB),
            secondaryColor = Color(0xFF0284C7)
        )
        else -> {
            val domain = try {
                val json = org.json.JSONObject(jsonContent)
                val match = json.getJSONObject("match")
                val hosts = match.optJSONArray("hosts")
                if (hosts != null && hosts.length() > 0) hosts.getString(0) else "web"
            } catch (_: Exception) {
                "web"
            }
            val mono = name.take(2).uppercase().ifBlank { id.take(2).uppercase() }
            SourceBrandStyle(
                domain = domain,
                monogram = mono,
                primaryColor = Color(0xFF475569),
                secondaryColor = Color(0xFF334155)
            )
        }
    }
}

@Composable
fun SourceBrandBadge(
    brandStyle: SourceBrandStyle,
    modifier: Modifier = Modifier
) {
    val faviconUrl = "https://www.google.com/s2/favicons?domain=${brandStyle.domain}&sz=128"

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(brandStyle.primaryColor, brandStyle.secondaryColor)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = faviconUrl,
            contentDescription = brandStyle.domain,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Fit,
            loading = {
                Text(
                    text = brandStyle.monogram,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            },
            error = {
                Text(
                    text = brandStyle.monogram,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            }
        )
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
    val brandStyle = remember(definition.id, definition.name) {
        resolveBrandStyle(definition.id, definition.name, definition.jsonContent)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBrowse),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceBrandBadge(
                    brandStyle = brandStyle,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = definition.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${brandStyle.domain}  •  v${definition.version}",
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
