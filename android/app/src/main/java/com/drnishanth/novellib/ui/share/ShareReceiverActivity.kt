package com.drnishanth.novellib.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.ui.theme.NovelLibTheme
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = extractSharedUrl(intent)
        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, "No valid URL found in shared content", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            NovelLibTheme {
                ShareImportDialog(
                    url = sharedText,
                    onDismiss = { finish() },
                    onImportComplete = { novelTitle ->
                        Toast.makeText(this, "Added \"$novelTitle\" to library!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val urlRegex = "https?://[^\\s]+".toRegex()
        val match = urlRegex.find(text)
        return match?.value ?: text.trim()
    }
}

@Composable
fun ShareImportDialog(
    url: String,
    onDismiss: () -> Unit,
    onImportComplete: (String) -> Unit
) {
    val app = NovelLibApplication.instance
    val activeProfile by app.profileRepository.activeProfile.collectAsState()
    val scope = rememberCoroutineScope()

    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Novel to Library") },
        text = {
            Column {
                if (activeProfile != null) {
                    Text(
                        "Importing for @${activeProfile?.username}:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        "Please open Novel Library and select a profile first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
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
                        Text("Scraping novel info...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val profile = activeProfile ?: return@Button
                    scope.launch {
                        isImporting = true
                        errorMessage = null
                        val result = app.novelRepository.importNovelFromUrl(url, profile.id)
                        isImporting = false
                        if (result.isSuccess) {
                            onImportComplete(result.getOrNull()?.title ?: "Novel")
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to import novel"
                        }
                    }
                },
                enabled = activeProfile != null && !isImporting
            ) {
                Text("Add to Library")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("Cancel")
            }
        }
    )
}
