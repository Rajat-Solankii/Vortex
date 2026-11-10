package com.example.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    PLAYBACK("Playback & Player", "Streaming quality, player gestures, skip intervals, PiP", Icons.Default.PlayCircle),
    SUBTITLES("Audio & Subtitles", "Default audio track, subtitle font size, styling & opacity", Icons.Default.Subtitles),
    DISPLAY("Display & Smoothness", "Adaptive 120 FPS refresh rate, real-time FPS overlay", Icons.Default.Speed),
    APPEARANCE("Appearance & Theme", "AMOLED black, accent colors, floating nav bar style", Icons.Default.ColorLens),
    DOWNLOADS("Downloads & Offline", "Download quality, Wi-Fi only, storage location", Icons.Default.Download),
    CONTENT("Content & Region", "Adult content filter, streaming provider region", Icons.Default.Public),
    STORAGE("Storage & Cache", "Manage cached posters and watch history space", Icons.Default.Storage),
    ABOUT("About & Diagnostics", "Check releases, Telegram monitor, device diagnostics", Icons.Default.Info)
}
