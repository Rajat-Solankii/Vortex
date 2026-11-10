package com.example.ui.screens.settings

import com.example.data.model.update.UpdateCheckResult
import com.example.data.update.AppUpdateDownloadManager
import com.example.ui.components.AppUpdateBottomSheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserPreferencesManager
import com.example.data.remote.GitHubConfigService
import com.example.data.remote.TelegramService
import com.example.data.remote.UpdateInfo
import com.example.data.repository.VortexRepository
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexDarkBackground
import com.example.ui.theme.VortexDarkCard
import com.example.ui.theme.VortexDarkCardBorder
import com.example.ui.theme.VortexDarkSurface
import com.example.ui.theme.VortexDarkSurfaceVariant
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleDeep
import com.example.ui.theme.VortexPurpleLight
import com.example.ui.theme.VortexTextMuted
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary
import com.example.util.DevicePerformanceUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    repository: VortexRepository,
    userPreferencesManager: UserPreferencesManager? = null,
    onRestartOnboarding: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    BackHandler {
        if (activeCategory != null) {
            activeCategory = null
        } else {
            onBack()
        }
    }

    AnimatedContent(
        targetState = activeCategory,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "SettingsNavTransition",
        modifier = modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
    ) { category ->
        if (category == null) {
            SettingsHubView(
                onBack = onBack,
                onCategoryClick = { activeCategory = it }
            )
        } else {
            SettingsCategoryDetailView(
                category = category,
                onBack = { activeCategory = null },
                repository = repository,
                userPreferencesManager = userPreferencesManager,
                onRestartOnboarding = onRestartOnboarding
            )
        }
    }
}

@Composable
private fun SettingsHubView(
    onBack: () -> Unit,
    onCategoryClick: (SettingsCategory) -> Unit
) {
    val context = LocalContext.current
    val currentRate = remember { DevicePerformanceUtils.getCurrentRefreshRate(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
    ) {
        // App Bar with One UI Style
        Surface(
            color = VortexDarkBackground,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
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

                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = VortexTextPrimary
                        )
                    )
                    Text(
                        text = "Preferences & Diagnostics Suite",
                        style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
                    )
                }
            }
        }

        // Quick Stats Banner (One UI Card)
        Surface(
            color = VortexDarkCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, VortexPurple.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VortexPurpleDeep),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = VortexCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Display: ${currentRate.toInt()} FPS Adaptive",
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "SoundWave Thermal Engine • Shield Active",
                        color = VortexTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Settings Categories List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsCategory.values().forEach { cat ->
                SettingsCategoryRow(
                    category = cat,
                    onClick = { onCategoryClick(cat) }
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    category: SettingsCategory,
    onClick: () -> Unit
) {
    Surface(
        color = VortexDarkCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VortexDarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VortexDarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = VortexPurpleLight,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = VortexTextPrimary
                )
                Text(
                    text = category.subtitle,
                    fontSize = 12.sp,
                    color = VortexTextSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = VortexTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SettingsCategoryDetailView(
    category: SettingsCategory,
    onBack: () -> Unit,
    repository: VortexRepository,
    userPreferencesManager: UserPreferencesManager?,
    onRestartOnboarding: (() -> Unit)?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
    ) {
        // App Bar with Category Title
        Surface(
            color = VortexDarkBackground,
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to categories",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )
                )
            }
        }

        // Category Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (category) {
                SettingsCategory.PLAYBACK -> {
                    PlaybackSettingsContent(userPreferencesManager)
                }
                SettingsCategory.SUBTITLES -> {
                    SubtitlesSettingsContent(userPreferencesManager)
                }
                SettingsCategory.DISPLAY -> {
                    DisplaySettingsContent(userPreferencesManager, context)
                }
                SettingsCategory.APPEARANCE -> {
                    AppearanceSettingsContent(userPreferencesManager)
                }
                SettingsCategory.DOWNLOADS -> {
                    DownloadsSettingsContent(userPreferencesManager)
                }
                SettingsCategory.CONTENT -> {
                    ContentSettingsContent(userPreferencesManager, onRestartOnboarding)
                }
                SettingsCategory.STORAGE -> {
                    StorageSettingsContent(repository, context)
                }
                SettingsCategory.ABOUT -> {
                    AboutSettingsContent(context)
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun PlaybackSettingsContent(pm: UserPreferencesManager?) {
    val streamRes by (pm?.streamResolution?.collectAsStateWithLifecycle(initialValue = "1080p FHD (Recommended)")
        ?: remember { mutableStateOf("1080p FHD (Recommended)") })
    val defaultServer by (pm?.defaultServer?.collectAsStateWithLifecycle(initialValue = "Server 1 (Auto CDN)")
        ?: remember { mutableStateOf("Server 1 (Auto CDN)") })
    val seekSkip by (pm?.seekSkipInterval?.collectAsStateWithLifecycle(initialValue = 10)
        ?: remember { mutableStateOf(10) })
    val autoplayNext by (pm?.isAutoPlayNext?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) })
    val backgroundAudio by (pm?.isBackgroundAudio?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) })
    val autoPip by (pm?.isAutoPip?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) })
    val gestureControls by (pm?.isGestureControls?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) })
    val scope = rememberCoroutineScope()

    SettingsGroupCard(title = "STREAM SERVER & RESOLUTION") {
        SettingOptionSelector(
            label = "Default Stream Server",
            currentValue = defaultServer,
            options = listOf("Server 1 (Auto CDN)", "Server 2 (Multi-Sub)", "Server 3 (Mirror)"),
            onSelect = { scope.launch { pm?.setDefaultServer(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Stream Resolution",
            currentValue = streamRes,
            options = listOf("Auto (Adaptive CDN)", "1080p FHD (Recommended)", "720p HD", "Data Saver (480p)"),
            onSelect = { scope.launch { pm?.setStreamResolution(it) } }
        )
    }

    SettingsGroupCard(title = "PLAYER CONTROLS & GESTURES") {
        SettingToggleRow(
            title = "Player Gesture Controls",
            subtitle = "Swipe vertically for brightness & volume, double-tap left/right to seek",
            checked = gestureControls,
            onCheckedChange = { scope.launch { pm?.setGestureControls(it) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingOptionSelector(
            label = "Seek Skip Interval",
            currentValue = "${seekSkip}s",
            options = listOf("5s", "10s", "15s", "30s"),
            onSelect = {
                val sec = it.replace("s", "").toIntOrNull() ?: 10
                scope.launch { pm?.setSeekSkipInterval(sec) }
            }
        )
    }

    SettingsGroupCard(title = "PLAYBACK AUTOMATION") {
        SettingToggleRow(
            title = "Auto-Play Next Episode",
            subtitle = "Automatically starts the next episode in TV series",
            checked = autoplayNext,
            onCheckedChange = { scope.launch { pm?.setAutoPlayNext(it) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingToggleRow(
            title = "Background Audio Mode",
            subtitle = "Keep audio playing when app is minimized or screen is turned off",
            checked = backgroundAudio,
            onCheckedChange = { scope.launch { pm?.setBackgroundAudio(it) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingToggleRow(
            title = "Auto Picture-in-Picture (PiP)",
            subtitle = "Seamlessly switch to floating PiP video when returning to home screen",
            checked = autoPip,
            onCheckedChange = { scope.launch { pm?.setAutoPip(it) } }
        )
    }
}

@Composable
private fun SubtitlesSettingsContent(pm: UserPreferencesManager?) {
    val autoSubs by (pm?.isAutoSubtitles?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) })
    val audioLang by (pm?.preferredAudioLanguage?.collectAsStateWithLifecycle(initialValue = "English")
        ?: remember { mutableStateOf("English") })
    val subLang by (pm?.preferredSubtitleLanguage?.collectAsStateWithLifecycle(initialValue = "English")
        ?: remember { mutableStateOf("English") })
    val fontSize by (pm?.subtitleFontSize?.collectAsStateWithLifecycle(initialValue = 16f)
        ?: remember { mutableStateOf(16f) })
    val subStyle by (pm?.subtitleStyle?.collectAsStateWithLifecycle(initialValue = "White with black border")
        ?: remember { mutableStateOf("White with black border") })
    val subOpacity by (pm?.subtitleOpacity?.collectAsStateWithLifecycle(initialValue = 0.8f)
        ?: remember { mutableStateOf(0.8f) })
    val scope = rememberCoroutineScope()

    SettingsGroupCard(title = "LANGUAGE PREFERENCES") {
        SettingToggleRow(
            title = "Auto-Load Subtitles",
            subtitle = "Automatically load subtitles when playing videos",
            checked = autoSubs,
            onCheckedChange = { scope.launch { pm?.setAutoSubtitles(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Preferred Audio Language",
            currentValue = audioLang,
            options = listOf("English", "Original", "Hindi", "Spanish", "Japanese"),
            onSelect = { scope.launch { pm?.setPreferredAudioLanguage(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Preferred Subtitle Language",
            currentValue = subLang,
            options = listOf("English", "Spanish", "French", "German", "Japanese", "Hindi"),
            onSelect = { scope.launch { pm?.setPreferredSubtitleLanguage(it) } }
        )
    }

    SettingsGroupCard(title = "SUBTITLE STYLING") {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Font Size", color = VortexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${fontSize.toInt()} sp", color = VortexCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Slider(
                value = fontSize,
                onValueChange = { scope.launch { pm?.setSubtitleFontSize(it) } },
                valueRange = 12f..24f,
                steps = 6,
                colors = SliderDefaults.colors(
                    thumbColor = VortexPurple,
                    activeTrackColor = VortexPurple,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingOptionSelector(
            label = "Subtitle Style",
            currentValue = subStyle,
            options = listOf("White with black border", "Yellow on black pill", "Custom Cyber"),
            onSelect = { scope.launch { pm?.setSubtitleStyle(it) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Background Opacity", color = VortexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${(subOpacity * 100).toInt()}%", color = VortexCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Slider(
                value = subOpacity,
                onValueChange = { scope.launch { pm?.setSubtitleOpacity(it) } },
                valueRange = 0f..1f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = VortexPurple,
                    activeTrackColor = VortexPurple,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun DisplaySettingsContent(pm: UserPreferencesManager?, context: Context) {
    val highRefresh by (pm?.isHighRefreshRate?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) })
    val fpsOverlay by (pm?.isFpsOverlayEnabled?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) })
    val reducedMotion by (pm?.isReducedMotion?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) })
    val scope = rememberCoroutineScope()
    val activeRefreshRate = remember { DevicePerformanceUtils.getCurrentRefreshRate(context) }

    SettingsGroupCard(title = "REFRESH RATE & FLUIDITY") {
        SettingToggleRow(
            title = "Adaptive 120 FPS High Refresh Rate",
            subtitle = "Employs SoundWave 8-second thermal dwell time and resolution locking for buttery smoothness.",
            checked = highRefresh,
            onCheckedChange = { scope.launch { pm?.setHighRefreshRate(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Active Display Mode", color = VortexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "${activeRefreshRate.toInt()} Hz Display Panel ${if (activeRefreshRate >= 89f) "• 120 FPS Peak" else "• Standard"}",
                    color = VortexCyan,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }

    SettingsGroupCard(title = "DIAGNOSTICS & ACCESSIBILITY") {
        SettingToggleRow(
            title = "Real-Time FPS & JankStats Overlay",
            subtitle = "Display on-screen frame-time graphs to monitor dropped frames",
            checked = fpsOverlay,
            onCheckedChange = { scope.launch { pm?.setFpsOverlayEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingToggleRow(
            title = "Reduced Motion",
            subtitle = "Replaces spring animations with fast fades for lower-end chipsets",
            checked = reducedMotion,
            onCheckedChange = { scope.launch { pm?.setReducedMotion(it) } }
        )
    }
}

@Composable
private fun AppearanceSettingsContent(pm: UserPreferencesManager?) {
    val amoled by (pm?.isAmoledTheme?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) })
    val accent by (pm?.accentColor?.collectAsStateWithLifecycle(initialValue = "Vortex Cyan")
        ?: remember { mutableStateOf("Vortex Cyan") })
    val navStyle by (pm?.navBarStyle?.collectAsStateWithLifecycle(initialValue = "Floating Expressive Pill")
        ?: remember { mutableStateOf("Floating Expressive Pill") })
    val posterDensity by (pm?.posterDensity?.collectAsStateWithLifecycle(initialValue = "Comfortable")
        ?: remember { mutableStateOf("Comfortable") })
    val scope = rememberCoroutineScope()

    SettingsGroupCard(title = "COLOR THEME") {
        SettingToggleRow(
            title = "Pure AMOLED Black Theme",
            subtitle = "Turns dark surfaces into true #000000 black to maximize battery savings on OLED panels",
            checked = amoled,
            onCheckedChange = { scope.launch { pm?.setAmoledTheme(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Accent Color",
            currentValue = accent,
            options = listOf("Vortex Cyan", "Neon Purple", "Emerald", "Sunset Orange"),
            onSelect = { scope.launch { pm?.setAccentColor(it) } }
        )
    }

    SettingsGroupCard(title = "NAVIGATION & LAYOUT") {
        SettingOptionSelector(
            label = "Navigation Bar Style",
            currentValue = navStyle,
            options = listOf("Floating Expressive Pill", "Classic Full Width"),
            onSelect = { scope.launch { pm?.setNavBarStyle(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Poster Display Density",
            currentValue = posterDensity,
            options = listOf("Comfortable (2-column)", "Compact (3-column)"),
            onSelect = { scope.launch { pm?.setPosterDensity(it) } }
        )
    }
}

@Composable
private fun DownloadsSettingsContent(pm: UserPreferencesManager?) {
    val quality by (pm?.downloadQuality?.collectAsStateWithLifecycle(initialValue = "1080p")
        ?: remember { mutableStateOf("1080p") })
    val wifiOnly by (pm?.isDownloadWifiOnly?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) })
    val storageLoc by (pm?.storageLocation?.collectAsStateWithLifecycle(initialValue = "Internal Storage")
        ?: remember { mutableStateOf("Internal Storage") })
    val scope = rememberCoroutineScope()

    SettingsGroupCard(title = "DOWNLOAD PREFERENCES") {
        SettingOptionSelector(
            label = "Download Quality",
            currentValue = quality,
            options = listOf("1080p", "720p", "480p"),
            onSelect = { scope.launch { pm?.setDownloadQuality(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingToggleRow(
            title = "Download Over Wi-Fi Only",
            subtitle = "Avoid consuming mobile carrier data while downloading media",
            checked = wifiOnly,
            onCheckedChange = { scope.launch { pm?.setDownloadWifiOnly(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Storage Location",
            currentValue = storageLoc,
            options = listOf("Internal Storage", "External SD Card"),
            onSelect = { scope.launch { pm?.setStorageLocation(it) } }
        )
    }
}

@Composable
private fun ContentSettingsContent(
    pm: UserPreferencesManager?,
    onRestartOnboarding: (() -> Unit)?
) {
    val safeSearch by (pm?.isSafeSearchEnabled?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) })
    val region by (pm?.watchRegion?.collectAsStateWithLifecycle(initialValue = "United States")
        ?: remember { mutableStateOf("United States") })
    val homeTab by (pm?.defaultHomeTab?.collectAsStateWithLifecycle(initialValue = "All")
        ?: remember { mutableStateOf("All") })
    val scope = rememberCoroutineScope()

    SettingsGroupCard(title = "FILTERS & REGION") {
        SettingToggleRow(
            title = "SafeSearch / Adult Content Filter",
            subtitle = "Hide 18+ and sensitive titles from TMDB discovery lists",
            checked = safeSearch,
            onCheckedChange = { scope.launch { pm?.setSafeSearchEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Watch Providers Country",
            currentValue = region,
            options = listOf("United States", "India", "United Kingdom", "Canada"),
            onSelect = { scope.launch { pm?.setWatchRegion(it) } }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingOptionSelector(
            label = "Default Home Tab",
            currentValue = homeTab,
            options = listOf("All", "Movies", "TV Shows", "Anime"),
            onSelect = { scope.launch { pm?.setDefaultHomeTab(it) } }
        )
    }

    if (onRestartOnboarding != null) {
        SettingsGroupCard(title = "ONBOARDING WIZARD") {
            Button(
                onClick = {
                    scope.launch {
                        pm?.setOnboardingCompleted(false)
                        onRestartOnboarding()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VortexDarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null, tint = VortexCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Re-launch Welcome Onboarding", color = Color.White)
            }
        }
    }
}

@Composable
private fun StorageSettingsContent(repository: VortexRepository, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf(calculateCacheSize(context)) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    SettingsGroupCard(title = "CACHE & MEMORY") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Posters & Thumbnail Cache", fontWeight = FontWeight.Bold, color = VortexTextPrimary, fontSize = 14.sp)
                Text(cacheSize, color = VortexCyan, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }

            Button(
                onClick = {
                    clearCache(context)
                    cacheSize = calculateCacheSize(context)
                    Toast.makeText(context, "Image cache cleared!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Clear")
            }
        }
    }

    SettingsGroupCard(title = "WATCH HISTORY & DATABASE") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Watch History", fontWeight = FontWeight.Bold, color = VortexTextPrimary, fontSize = 14.sp)
                Text("Remove all completed and in-progress viewing history", color = VortexTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }

            Button(
                onClick = { showClearHistoryDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset")
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Watch History?", fontWeight = FontWeight.Bold, color = VortexTextPrimary) },
            text = { Text("This will remove all continue watching titles from your history. This action cannot be undone.", color = VortexTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.clearWatchHistory()
                            showClearHistoryDialog = false
                            Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = VortexTextSecondary)
                }
            },
            containerColor = VortexDarkSurface
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AboutSettingsContent(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateCheckResult.Available?>(null) }
    var showUpToDateDialog by remember { mutableStateOf(false) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    val downloadManager = remember { AppUpdateDownloadManager(context) }

    var feedbackName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Bug Report") }
    var feedbackMessage by remember { mutableStateOf("") }
    var isSendingFeedback by remember { mutableStateOf(false) }
    var showFeedbackSuccessDialog by remember { mutableStateOf(false) }

    val deviceId = remember { TelegramService.getDeviceId(context) }
    val (versionName, versionCode) = remember { TelegramService.getAppVersionInfo(context) }
    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    SettingsGroupCard(title = "APPLICATION UPDATES") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.SystemUpdate, null, tint = VortexPurpleLight, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Vortex v$versionName (Code $versionCode)", fontWeight = FontWeight.Bold, color = VortexTextPrimary, fontSize = 14.sp)
                Text("Official GitHub releases & in-app updates", color = VortexTextSecondary, fontSize = 12.sp)
            }

            if (isCheckingUpdates) {
                CircularProgressIndicator(color = VortexPurple, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isCheckingUpdates = true
                            val result = GitHubConfigService.checkReleaseUpdate(context)
                            isCheckingUpdates = false
                            when (result) {
                                is UpdateCheckResult.Available -> {
                                    availableUpdate = result
                                }
                                is UpdateCheckResult.UpToDate -> {
                                    showUpToDateDialog = true
                                }
                                is UpdateCheckResult.Error -> {
                                    updateErrorMessage = result.message
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Check", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    SettingsGroupCard(title = "SEND FEEDBACK (TELEGRAM BOT)") {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Send bug reports or requests directly to our Telegram channel:",
                style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Bug Report", "Feature", "Stream Issue").forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VortexPurple,
                            containerColor = VortexDarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = feedbackName,
                onValueChange = { feedbackName = it },
                label = { Text("Your Name (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VortexPurple,
                    unfocusedBorderColor = VortexDarkCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                maxLines = 3
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
                                Toast.makeText(context, "Feedback sent!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isSendingFeedback && feedbackMessage.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSendingFeedback) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send to Telegram Channel")
                }
            }
        }
    }

    SettingsGroupCard(title = "DEVICE & SYSTEM DIAGNOSTICS") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Model:", color = VortexTextSecondary, fontSize = 13.sp, modifier = Modifier.width(100.dp))
                Text(deviceModel, color = VortexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("OS:", color = VortexTextSecondary, fontSize = 13.sp, modifier = Modifier.width(100.dp))
                Text(androidVersion, color = VortexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Device ID:", color = VortexTextSecondary, fontSize = 13.sp, modifier = Modifier.width(100.dp))
                Text(deviceId.take(16) + "...", color = VortexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
                    Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = VortexPurpleLight, modifier = Modifier.size(16.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Shield, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AdBlocker Shield: ACTIVE", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    SettingsGroupCard(title = "ABOUT VORTEX & SOURCE") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Vortex is a native Android cinema app featuring 120 FPS adaptive refresh rates, embed stream decoders, and smart handoff.",
                style = MaterialTheme.typography.bodySmall.copy(color = VortexTextSecondary)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Rajat-Solankii/VortexApp"))
                        context.startActivity(intent)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GitHub Repository (Rajat-Solankii/VortexApp)", color = VortexPurpleLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.OpenInNew, null, tint = VortexPurpleLight, modifier = Modifier.size(14.dp))
            }
        }
    }

    if (showUpToDateDialog) {
        AlertDialog(
            onDismissRequest = { showUpToDateDialog = false },
            title = { Text("You're Up to Date", color = VortexTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Vortex v$versionName is currently the newest available release.", color = VortexTextSecondary) },
            confirmButton = { TextButton(onClick = { showUpToDateDialog = false }) { Text("OK", color = VortexPurple) } },
            containerColor = VortexDarkSurface
        )
    }

    if (updateErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { updateErrorMessage = null },
            title = { Text("Update Check Failed", color = VortexTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(updateErrorMessage ?: "", color = VortexTextSecondary) },
            confirmButton = { TextButton(onClick = { updateErrorMessage = null }) { Text("Dismiss", color = VortexPurple) } },
            containerColor = VortexDarkSurface
        )
    }

    if (availableUpdate != null) {
        AppUpdateBottomSheet(
            release = availableUpdate!!.release,
            targetAsset = availableUpdate!!.targetAsset,
            currentVersion = availableUpdate!!.currentVersion,
            downloadManager = downloadManager,
            onDismissRequest = {
                availableUpdate = null
            }
        )
    }

    if (showFeedbackSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackSuccessDialog = false },
            title = { Text("Feedback Sent!", color = VortexTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Your message was dispatched to the Telegram monitor channel.", color = VortexTextSecondary) },
            confirmButton = { TextButton(onClick = { showFeedbackSuccessDialog = false }) { Text("Close", color = VortexPurple) } },
            containerColor = VortexDarkSurface
        )
    }
}

@Composable
private fun SettingsGroupCard(
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
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, VortexDarkCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = VortexTextPrimary, fontSize = 14.sp)
            Text(subtitle, color = VortexTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VortexPurple,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun SettingOptionSelector(
    label: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Bold, color = VortexTextPrimary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = VortexDarkSurfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currentValue, color = VortexCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = VortexTextMuted, modifier = Modifier.size(12.dp))
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VortexDarkSurface)
                    .padding(6.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) VortexPurpleDeep.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable {
                                onSelect(option)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option,
                            color = if (isSelected) Color.White else VortexTextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = VortexCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun calculateCacheSize(context: Context): String {
    val dir = context.cacheDir.resolve("image_cache")
    val legacyDir = context.cacheDir.resolve("vortex_image_cache")
    fun dirSize(file: java.io.File): Long {
        if (!file.exists()) return 0L
        return file.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
    val bytes = dirSize(dir) + dirSize(legacyDir)
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun clearCache(context: Context) {
    try {
        context.cacheDir.resolve("image_cache").deleteRecursively()
        context.cacheDir.resolve("vortex_image_cache").deleteRecursively()
        coil.Coil.imageLoader(context).memoryCache?.clear()
    } catch (_: Throwable) {}
}
