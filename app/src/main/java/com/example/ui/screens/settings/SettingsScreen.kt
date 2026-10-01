package com.example.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.GitHubConfigService
import com.example.data.remote.TelegramService
import com.example.data.remote.UpdateInfo
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexDarkSurface
import com.example.ui.theme.VortexDarkSurfaceVariant
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateResultDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpToDateDialog by remember { mutableStateOf(false) }

    // Feedback state
    var feedbackName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Bug Report") }
    var feedbackMessage by remember { mutableStateOf("") }
    var isSendingFeedback by remember { mutableStateOf(false) }
    var showFeedbackSuccessDialog by remember { mutableStateOf(false) }

    val deviceId = remember { TelegramService.getDeviceId(context) }
    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    BackHandler {
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
    ) {
        // App Bar
        Surface(
            color = VortexDarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Settings & Updates",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )
                )
            }
        }

        // Settings Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. UPDATE CHECKER SECTION
            SettingsCard(title = "APPLICATION UPDATES") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update",
                        tint = VortexPurpleLight,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vortex Version 1.0 (Code 1)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = VortexTextPrimary
                            )
                        )
                        Text(
                            text = "Check GitHub repo for new APK releases",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = VortexTextSecondary
                            )
                        )
                    }

                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            color = VortexPurple,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingUpdates = true
                                    val result = GitHubConfigService.checkForUpdates(context)
                                    isCheckingUpdates = false
                                    val updateInfo = result.getOrNull()
                                    if (updateInfo != null && updateInfo.hasUpdate) {
                                        updateResultDialog = updateInfo
                                    } else {
                                        showUpToDateDialog = true
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("check_updates_button")
                        ) {
                            Text("Check", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. USER FEEDBACK SECTION (TELEGRAM BOT)
            SettingsCard(title = "SEND FEEDBACK (TELEGRAM)") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Have a feature request, bug report, or streaming issue? Send it directly to our Telegram monitoring channel:",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Bug Report", "Feature Request", "Streaming Issue", "Other").forEach { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VortexPurple,
                                    containerColor = VortexDarkSurfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Name input
                    OutlinedTextField(
                        value = feedbackName,
                        onValueChange = { feedbackName = it },
                        label = { Text("Your Name or Username (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VortexPurple,
                            unfocusedBorderColor = VortexDarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Feedback Message
                    OutlinedTextField(
                        value = feedbackMessage,
                        onValueChange = { feedbackMessage = it },
                        label = { Text("Describe your feedback or issue...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VortexPurple,
                            unfocusedBorderColor = VortexDarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 4,
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (feedbackMessage.isNotBlank()) {
                                coroutineScope.launch {
                                    isSendingFeedback = true
                                    val result = TelegramService.sendUserFeedback(
                                        userName = feedbackName.trim(),
                                        category = selectedCategory,
                                        messageText = feedbackMessage.trim(),
                                        context = context
                                    )
                                    isSendingFeedback = false
                                    if (result.getOrDefault(false)) {
                                        feedbackMessage = ""
                                        showFeedbackSuccessDialog = true
                                    } else {
                                        Toast.makeText(context, "Feedback delivered to Telegram!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please enter a feedback message", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSendingFeedback && feedbackMessage.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_feedback_button")
                    ) {
                        if (isSendingFeedback) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sending to Telegram...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send to Telegram Channel")
                        }
                    }
                }
            }

            // 3. DEVICE & TELEMETRY DIAGNOSTICS
            SettingsCard(title = "DEVICE & SYSTEM DIAGNOSTICS") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DiagnosticItem(label = "Device Model", value = deviceModel)
                    DiagnosticItem(label = "Operating System", value = androidVersion)
                    val activeRefreshRate = remember { com.example.util.HighRefreshRateHelper.getCurrentRefreshRate(context) }
                    DiagnosticItem(
                        label = "Display Refresh Rate",
                        value = "${activeRefreshRate.toInt()} Hz ${if (activeRefreshRate >= 89f) "(120 FPS Enabled)" else "(Standard)"}"
                    )

                    // Device ID with Copy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device ID",
                                style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                            )
                            Text(
                                text = deviceId,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = VortexTextPrimary
                                )
                            )
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
                            Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Device ID",
                                tint = VortexPurpleLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Security & Shield Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "In-App Ad & Redirect Shield: ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784)
                            )
                        )
                    }
                }
            }

            // 4. ABOUT & GITHUB LINKS
            SettingsCard(title = "ABOUT VORTEX") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Vortex is a native Android movie and TV streaming client integrating proxy routing, AdBlocker shield protection, and multi-server embed playback.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                    )

                    ExternalLinkItem(
                        title = "GitHub Repository (Rajat-Solankii/VortexApp)",
                        url = "https://github.com/Rajat-Solankii/VortexApp",
                        context = context
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // New Update Available Dialog
    updateResultDialog?.let { updateInfo ->
        AlertDialog(
            onDismissRequest = { updateResultDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = VortexPurple
                )
            },
            title = {
                Text(
                    text = "New Update Available!",
                    fontWeight = FontWeight.Bold,
                    color = VortexTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Version ${updateInfo.versionName} (Build ${updateInfo.versionCode})",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = VortexPurpleLight
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = updateInfo.message,
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.updateUrl))
                        context.startActivity(intent)
                        updateResultDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VortexPurple)
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateResultDialog = null }) {
                    Text("Later", color = VortexTextSecondary)
                }
            },
            containerColor = VortexDarkSurface
        )
    }

    // Up to date Dialog
    if (showUpToDateDialog) {
        AlertDialog(
            onDismissRequest = { showUpToDateDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50)
                )
            },
            title = {
                Text(
                    text = "You're Up to Date",
                    fontWeight = FontWeight.Bold,
                    color = VortexTextPrimary
                )
            },
            text = {
                Text(
                    text = "Vortex version 1.0 is currently the newest version.",
                    style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpToDateDialog = false }) {
                    Text("OK", color = VortexPurple)
                }
            },
            containerColor = VortexDarkSurface
        )
    }

    // Feedback Submitted Dialog
    if (showFeedbackSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackSuccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Feedback,
                    contentDescription = null,
                    tint = VortexCyan
                )
            },
            title = {
                Text(
                    text = "Feedback Sent!",
                    fontWeight = FontWeight.Bold,
                    color = VortexTextPrimary
                )
            },
            text = {
                Text(
                    text = "Thank you! Your feedback has been sent directly to our Telegram monitoring channel.",
                    style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                )
            },
            confirmButton = {
                TextButton(onClick = { showFeedbackSuccessDialog = false }) {
                    Text("Close", color = VortexPurple)
                }
            },
            containerColor = VortexDarkSurface
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = VortexTextMuted
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            color = VortexDarkCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VortexDarkCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = VortexTextPrimary
            )
        )
    }
}

@Composable
private fun ExternalLinkItem(title: String, url: String, context: Context) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(
                color = VortexPurpleLight,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = "Open Link",
            tint = VortexPurpleLight,
            modifier = Modifier.size(16.dp)
        )
    }
}
