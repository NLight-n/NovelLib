package com.drnishanth.novellib.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drnishanth.novellib.core.database.entities.UserProfileEntity

enum class PasswordOption {
    KEEP, CHANGE, REMOVE
}

@Composable
fun EditProfileDialog(
    profile: UserProfileEntity,
    canDelete: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (
        newUsername: String,
        newDisplayName: String,
        currentPassword: String?,
        newPassword: String?,
        removePassword: Boolean
    ) -> Unit,
    onDelete: () -> Unit = {}
) {
    var username by remember { mutableStateOf(profile.username) }
    var displayName by remember { mutableStateOf(profile.displayName) }

    // If profile currently has password
    var currentPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var passwordOption by remember { mutableStateOf(PasswordOption.KEEP) }

    // If profile currently does NOT have password
    var addPasswordEnabled by remember { mutableStateOf(false) }

    // New password fields
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }

    var localValidationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (profile.passwordEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Profile: @${profile.username}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Profile Identity Fields
                Text(
                    text = "Profile Info",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.filter { ch -> !ch.isWhitespace() }
                        localValidationError = null
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        localValidationError = null
                    },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password & Security Section
                Text(
                    text = "Security & Password",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (profile.passwordEnabled) {
                            Text(
                                text = "Current Status: Protected with Argon2id",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = {
                                    currentPassword = it
                                    localValidationError = null
                                },
                                label = { Text("Current Password *") },
                                singleLine = true,
                                visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                        Icon(
                                            if (showCurrentPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Password Action:", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = passwordOption == PasswordOption.KEEP,
                                    onClick = { passwordOption = PasswordOption.KEEP },
                                    label = { Text("Keep", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = passwordOption == PasswordOption.CHANGE,
                                    onClick = { passwordOption = PasswordOption.CHANGE },
                                    label = { Text("Change", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = passwordOption == PasswordOption.REMOVE,
                                    onClick = { passwordOption = PasswordOption.REMOVE },
                                    label = { Text("Remove", fontSize = 11.sp) }
                                )
                            }

                            if (passwordOption == PasswordOption.CHANGE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = {
                                        newPassword = it
                                        localValidationError = null
                                    },
                                    label = { Text("New Password") },
                                    singleLine = true,
                                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                            Icon(
                                                if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = {
                                        confirmPassword = it
                                        localValidationError = null
                                    },
                                    label = { Text("Confirm New Password") },
                                    singleLine = true,
                                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (passwordOption == PasswordOption.REMOVE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Password will be removed. Profile will be accessible without authentication.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        } else {
                            // Profile currently has NO password
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Add Password Protection",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Secure this profile with Argon2id",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = addPasswordEnabled,
                                    onCheckedChange = {
                                        addPasswordEnabled = it
                                        localValidationError = null
                                    }
                                )
                            }

                            if (addPasswordEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = {
                                        newPassword = it
                                        localValidationError = null
                                    },
                                    label = { Text("New Password") },
                                    singleLine = true,
                                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                            Icon(
                                                if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = {
                                        confirmPassword = it
                                        localValidationError = null
                                    },
                                    label = { Text("Confirm Password") },
                                    singleLine = true,
                                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Error Messages
                val activeError = localValidationError ?: errorMessage
                if (activeError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = activeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Delete profile option (if allowed)
                if (canDelete) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Profile")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isBlank()) {
                        localValidationError = "Username cannot be empty"
                        return@Button
                    }

                    if (profile.passwordEnabled) {
                        if (currentPassword.isBlank()) {
                            localValidationError = "Current password is required"
                            return@Button
                        }
                        when (passwordOption) {
                            PasswordOption.KEEP -> {
                                onSave(username.trim(), displayName.trim(), currentPassword, null, false)
                            }
                            PasswordOption.CHANGE -> {
                                if (newPassword.isBlank()) {
                                    localValidationError = "New password cannot be empty"
                                    return@Button
                                }
                                if (newPassword != confirmPassword) {
                                    localValidationError = "New passwords do not match"
                                    return@Button
                                }
                                onSave(username.trim(), displayName.trim(), currentPassword, newPassword, false)
                            }
                            PasswordOption.REMOVE -> {
                                onSave(username.trim(), displayName.trim(), currentPassword, null, true)
                            }
                        }
                    } else {
                        // Profile currently has no password
                        if (addPasswordEnabled) {
                            if (newPassword.isBlank()) {
                                localValidationError = "Password cannot be empty"
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                localValidationError = "Passwords do not match"
                                return@Button
                            }
                            onSave(username.trim(), displayName.trim(), null, newPassword, false)
                        } else {
                            onSave(username.trim(), displayName.trim(), null, null, false)
                        }
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Profile") },
            text = {
                Text("Are you sure you want to permanently delete profile \"@${profile.username}\"? All reading data associated with this profile will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
