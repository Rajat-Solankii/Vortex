package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cast.CastManager
import com.example.cast.MediaSourceClassifier
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexDarkSurfaceVariant
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary
import com.example.util.CastHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastToTvBottomSheet(
    title: String,
    streamUrl: String,
    posterUrl: String? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showGuide by remember { mutableStateOf(false) }

    val castManager = remember { CastManager.getInstance(context) }
    val isCastConnected by castManager.isConnected.collectAsStateWithLifecycle()
    val connectedDeviceName by castManager.connectedDeviceName.collectAsStateWithLifecycle()
    val isCastPlaying by castManager.isPlaying.collectAsStateWithLifecycle()

    val classifiedSource = remember(streamUrl) { MediaSourceClassifier.classify(streamUrl) }

    var showEmbedCastDialog by remember { mutableStateOf(false) }
    var showEmbedExternalPlayerDialog by remember { mutableStateOf(false) }
    var showSamsungSmartViewDialog by remember { mutableStateOf(false) }

    // Dialog: Samsung Smart View Quick Settings Guidance
    if (showSamsungSmartViewDialog) {
        AlertDialog(
            onDismissRequest = { showSamsungSmartViewDialog = false },
            containerColor = Color(0xFF16161E),
            icon = {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = Color(0xFF42A5F5),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Samsung Smart View",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "On Samsung Galaxy devices, Smart View is built directly into the One UI Quick Settings panel for the best mirroring performance.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "How to connect:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF90CAF9)
                                )
                            )
                            Text(
                                text = "1. Swipe down from the top of your screen twice to expand Quick Settings.\n2. Tap the Smart View tile.\n3. Choose your Samsung TV, Windows Laptop, or wireless display from the list.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = VortexTextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                    Text(
                        text = "You can also open Android system Cast settings below if supported on your One UI version.",
                        style = MaterialTheme.typography.labelSmall.copy(color = VortexTextMuted)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showSamsungSmartViewDialog = false
                        val launched = CastHelper.openSystemCastSettings(context)
                        if (!launched) {
                            Toast.makeText(
                                context,
                                "Swipe down from top twice to access Smart View",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E88E5),
                        contentColor = Color.White
                    )
                ) {
                    Text("Open Cast Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSamsungSmartViewDialog = false
                    }
                ) {
                    Text("Got it", color = VortexTextSecondary)
                }
            }
        )
    }

    // Dialog: Explain why Web Embed cannot be cast directly to Chromecast
    if (showEmbedCastDialog) {
        AlertDialog(
            onDismissRequest = { showEmbedCastDialog = false },
            containerColor = Color(0xFF16161E),
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = VortexCyan,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Web Embed Stream Notice",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This stream is running through an interactive web player ($title). Direct Chromecast receiver streaming requires raw .mp4 or .m3u8 media files.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                    )
                    Text(
                        text = "To watch on your TV without issues:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = VortexTextPrimary
                        )
                    )
                    Text(
                        text = "1. Use Screen Mirroring / Smart View to mirror your screen directly.\n2. Or open the stream link in your Smart TV browser.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexCyan)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showEmbedCastDialog = false
                        CastHelper.startScreenMirroring(
                            context = context,
                            onShowSamsungInstructions = { showSamsungSmartViewDialog = true }
                        )
                        if (!CastHelper.isSamsungDevice()) {
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VortexPurple,
                        contentColor = Color.White
                    )
                ) {
                    Text("Use Screen Mirroring")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showEmbedCastDialog = false
                        CastHelper.copyStreamLink(context, streamUrl)
                    }
                ) {
                    Text("Copy TV Link", color = VortexTextPrimary)
                }
            }
        )
    }

    // Dialog: Explain why Web Embed cannot be played directly inside VLC/MX Player
    if (showEmbedExternalPlayerDialog) {
        AlertDialog(
            onDismissRequest = { showEmbedExternalPlayerDialog = false },
            containerColor = Color(0xFF16161E),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "External Player Notice",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The current source is an interactive web stream player, not a direct raw video file. External players like VLC or MX Player cannot render web page iframes.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                    )
                    Text(
                        text = "You can open this stream directly in Chrome, Samsung Internet, or mirror your display to your TV via Screen Mirroring.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexCyan)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showEmbedExternalPlayerDialog = false
                        CastHelper.openInBrowser(context, streamUrl)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VortexPurple,
                        contentColor = Color.White
                    )
                ) {
                    Text("Open in Web Browser")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEmbedExternalPlayerDialog = false
                    }
                ) {
                    Text("Cancel", color = VortexTextMuted)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F14),
        contentColor = VortexTextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(VortexPurple, Color(0xFF7B1FA2))),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCastConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Cast",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cast to TV & Displays",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = VortexTextPrimary,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "Watch \"$title\" on the big screen",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VortexTextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = VortexTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Active Google Cast Session Card (If Connected)
            if (isCastConnected && connectedDeviceName != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = VortexPurple.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, VortexPurple.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = VortexPurpleLight,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected to $connectedDeviceName",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (isCastPlaying) "Playing on TV" else "Cast session active",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = VortexCyan,
                                    fontSize = 11.5.sp
                                )
                            )
                        }

                        // Play/Pause
                        IconButton(onClick = { castManager.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isCastPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White
                            )
                        }

                        // Disconnect
                        TextButton(onClick = { castManager.disconnect() }) {
                            Text("Disconnect", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // =========================================================================
            // EXACTLY 4 PRIMARY OPTIONS (NO DUPLICATION)
            // =========================================================================

            // OPTION 1: Screen Mirroring (Merged Single Option)
            CastOptionCard(
                icon = Icons.Default.Devices,
                iconTint = Color(0xFF42A5F5),
                title = "Screen Mirroring",
                subtitle = "Mirror your phone screen to Samsung Smart TVs, Windows laptops and compatible wireless displays.",
                badgeText = "Smart View • Miracast • Wireless Display",
                badgeColor = Color(0xFF1E3A5F),
                onClick = {
                    CastHelper.startScreenMirroring(
                        context = context,
                        onShowSamsungInstructions = { showSamsungSmartViewDialog = true }
                    )
                    if (!CastHelper.isSamsungDevice()) {
                        onDismissRequest()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // OPTION 2: Cast Video to TV (Google Cast / Sender SDK)
            CastOptionCard(
                icon = Icons.Default.Cast,
                iconTint = VortexPurpleLight,
                title = "Cast Video to TV (Google Cast)",
                subtitle = if (isCastConnected) {
                    "Tap to stream '$title' to $connectedDeviceName"
                } else {
                    "Direct hardware streaming to Chromecast, Android TV, Google TV & Cast displays"
                },
                badgeText = if (isCastConnected) "Connected to $connectedDeviceName" else "Chromecast • Android TV",
                badgeColor = if (isCastConnected) Color(0xFF2E7D32) else VortexPurple.copy(alpha = 0.25f),
                onClick = {
                    if (classifiedSource.isDirectPlayable) {
                        if (isCastConnected) {
                            castManager.loadMedia(
                                title = title,
                                mediaUrl = streamUrl,
                                posterUrl = posterUrl,
                                mimeType = classifiedSource.mimeType,
                                onSuccess = {
                                    Toast.makeText(context, "Playing '$title' on TV!", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                },
                                onError = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            CastHelper.openInCastApp(context, streamUrl, title)
                            onDismissRequest()
                        }
                    } else {
                        // Web Embed source: Show informative modal explaining fallback
                        showEmbedCastDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // OPTION 3: Play in External Video Player (VLC, MX Player, Kodi)
            CastOptionCard(
                icon = Icons.Default.PlayCircle,
                iconTint = Color(0xFFFFB74D),
                title = "Play in External Video Player",
                subtitle = "Open video in VLC, MX Player, or Kodi with hardware decoding and local network casting",
                badgeText = "VLC • MX Player • Kodi",
                badgeColor = Color(0xFF4A3410),
                onClick = {
                    if (classifiedSource.isDirectPlayable) {
                        val launched = CastHelper.openInExternalPlayer(context, streamUrl, title)
                        if (launched) {
                            onDismissRequest()
                        }
                    } else {
                        showEmbedExternalPlayerDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // OPTION 4: Watch in Smart TV Browser
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VortexDarkCard,
                border = BorderStroke(1.dp, VortexDarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF004D40), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Browser",
                                tint = VortexCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Watch in Smart TV Browser",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = VortexTextPrimary
                                )
                            )
                            Text(
                                text = "Open directly in your TV's browser (Samsung Internet, LG webOS, Silk)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = VortexTextSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // URL Preview and Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = streamUrl,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = VortexCyan,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Copy Link
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VortexPurple.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, VortexPurple.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                CastHelper.copyStreamLink(context, streamUrl)
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = VortexPurpleLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy",
                                    color = VortexPurpleLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Share
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.clickable {
                                CastHelper.shareStreamLink(context, streamUrl, title)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expandable TV & Display Connection Guide
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VortexDarkSurfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGuide = !showGuide }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Guide",
                            tint = VortexTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "How to connect to different TVs & Displays",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = VortexTextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Guide",
                            tint = VortexTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = showGuide,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GuideItem(
                                brand = "Samsung Smart TV",
                                instruction = "Swipe down twice from the top of your screen to expand Quick Settings, tap 'Smart View', and choose your Samsung TV. Or open Samsung Internet on your TV and paste the copied link."
                            )
                            GuideItem(
                                brand = "Windows 10 & 11 Laptop (Miracast)",
                                instruction = "Ensure your Windows PC supports Miracast and is connected to the same Wi-Fi. Open Settings > System > Projecting to this PC (or open the 'Wireless Display' app). Then tap 'Screen Mirroring' above to project."
                            )
                            GuideItem(
                                brand = "LG Smart TV",
                                instruction = "Swipe down notification shade, tap 'Screen Cast' / 'Screen Share', and select your LG webOS TV. Or use Web Video Caster for smooth subtitle streaming."
                            )
                            GuideItem(
                                brand = "Chromecast & Google TV",
                                instruction = "Tap 'Cast Video to TV' above for direct hardware streaming without battery drain, or use Quick Settings Screen Cast to mirror your phone display in 1080p."
                            )
                            GuideItem(
                                brand = "Fire TV & Roku",
                                instruction = "On your Fire TV or Roku, enable Screen Mirroring in Settings > Display. Or cast via Web Video Caster directly to your Fire TV / Roku player."
                            )
                            GuideItem(
                                brand = "Apple TV",
                                instruction = "Use Web Video Caster to cast video directly to Apple TV via AirPlay."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CastOptionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VortexDarkCard,
        border = BorderStroke(1.dp, VortexDarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = VortexTextSecondary,
                        fontSize = 11.5.sp
                    ),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badgeText,
                        color = iconTint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideItem(brand: String, instruction: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text = brand,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = VortexCyan
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodySmall.copy(
                color = VortexTextSecondary,
                fontSize = 11.5.sp
            ),
            lineHeight = 15.sp
        )
    }
}
