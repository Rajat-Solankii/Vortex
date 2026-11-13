package com.example.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.UserPreferencesManager
import com.example.data.remote.SupabaseService
import com.example.data.repository.VortexRepository
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.VortexCyan
import com.example.ui.theme.VortexGold
import com.example.ui.theme.VortexPink
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexPurpleLight
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class AvatarOption(val id: String, val emoji: String, val label: String, val bg: Color)

val AVATAR_OPTIONS = listOf(
    AvatarOption("cosmic", "🚀", "Cosmic", Color(0xFF673AB7)),
    AvatarOption("cinema", "🎬", "Cinema", Color(0xFFE91E63)),
    AvatarOption("fox", "🦊", "Cyber Fox", Color(0xFFFF9800)),
    AvatarOption("nebula", "🌌", "Nebula", Color(0xFF00BCD4)),
    AvatarOption("lightning", "⚡", "Flash", Color(0xFFFFEB3B)),
    AvatarOption("crown", "👑", "Vortex VIP", Color(0xFFFFC107)),
    AvatarOption("popcorn", "🍿", "Movie Buff", Color(0xFF4CAF50)),
    AvatarOption("dragon", "🐉", "Mythic", Color(0xFF9C27B0))
)

fun saveProfilePhotoFromUri(context: Context, uri: Uri): String? {
    return try {
        val destinationFile = File(context.filesDir, "custom_profile_avatar.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }
        destinationFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

@Composable
fun ProfileScreen(
    repository: VortexRepository,
    userPreferencesManager: UserPreferencesManager,
    onNavigateToHistory: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalVortexColors.current
    val coroutineScope = rememberCoroutineScope()

    val displayName by userPreferencesManager.displayName.collectAsStateWithLifecycle(initialValue = "Vortex Explorer")
    val avatarId by userPreferencesManager.avatarId.collectAsStateWithLifecycle(initialValue = "cosmic")
    val customAvatarPath by userPreferencesManager.customAvatarPath.collectAsStateWithLifecycle(initialValue = null)
    val historyList by repository.watchHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarksList by repository.bookmarksFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var showEditProfileDialog by remember { mutableStateOf(false) }

    val currentAvatar = AVATAR_OPTIONS.firstOrNull { it.id == avatarId } ?: AVATAR_OPTIONS[0]
    val hasCustomPhoto = !customAvatarPath.isNullOrBlank() && File(customAvatarPath!!).exists()

    // Calculate real stats from user history
    val moviesWatchedCount = historyList.count { it.mediaType != "tv" }
    val episodesWatchedCount = historyList.count { it.mediaType == "tv" }
    val totalWatchTimeMinutes = (historyList.sumOf { it.lastPositionMs } / 60000L).coerceAtLeast(0L)
    val watchHours = totalWatchTimeMinutes / 60
    val watchRemainingMinutes = totalWatchTimeMinutes % 60

    val currentUser = remember { SupabaseService.client.auth.currentSessionOrNull()?.user }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // App Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = colors.textPrimary
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Profile Card (Pixel-inspired tonal container)
            item(key = "profile_header") {
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Avatar (Custom photo from gallery or preset emoji)
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(if (hasCustomPhoto) Color.Black else currentAvatar.bg)
                                .border(2.dp, colors.primary.copy(alpha = 0.5f), CircleShape)
                                .clickable { showEditProfileDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasCustomPhoto) {
                                AsyncImage(
                                    model = File(customAvatarPath!!),
                                    contentDescription = "Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = currentAvatar.emoji,
                                    fontSize = 44.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display Name with edit icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showEditProfileDialog = true }
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit profile",
                                tint = colors.primaryLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Cloud Sync Status Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentUser != null) Color(0xFF1B5E20).copy(alpha = 0.35f) else colors.surfaceVariant,
                            border = BorderStroke(1.dp, if (currentUser != null) Color(0xFF4CAF50).copy(alpha = 0.5f) else colors.cardBorder),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = if (currentUser != null) "✓ Cloud Synced (${currentUser.email ?: "Active"})" else "Local Guest Profile",
                                color = if (currentUser != null) Color(0xFF81C784) else colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Edit Profile Pill Action
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.primary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { showEditProfileDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = colors.primaryLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Edit Profile",
                                    color = colors.primaryLight,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }

            // Viewing Stats Row (3 Tonal Cards)
            item(key = "stats_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Movies
                    StatCard(
                        count = "$moviesWatchedCount",
                        label = "Movies",
                        color = VortexCyan,
                        modifier = Modifier.weight(1f)
                    )
                    // Episodes
                    StatCard(
                        count = "$episodesWatchedCount",
                        label = "Episodes",
                        color = VortexPurple,
                        modifier = Modifier.weight(1f)
                    )
                    // Time
                    StatCard(
                        count = if (watchHours > 0) "${watchHours}h" else "${watchRemainingMinutes}m",
                        label = "Watch Time",
                        color = VortexGold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Nav Section
            item(key = "library_shortcuts") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.card)
                        .padding(vertical = 4.dp)
                ) {
                    ProfileMenuRow(
                        icon = Icons.Default.History,
                        title = "Watch History",
                        subtitle = "${historyList.size} titles watched",
                        tint = VortexCyan,
                        onClick = onNavigateToHistory
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Bookmark,
                        title = "My List",
                        subtitle = "${bookmarksList.size} saved titles",
                        tint = VortexPurpleLight,
                        onClick = onNavigateToBookmarks
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        title = "All Settings",
                        subtitle = "Themes, servers, performance & about",
                        tint = colors.textPrimary,
                        onClick = onNavigateToSettings
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // Comprehensive Edit Profile Modal
    if (showEditProfileDialog) {
        var stagedName by remember(showEditProfileDialog) { mutableStateOf(displayName) }
        var stagedAvatarId by remember(showEditProfileDialog) { mutableStateOf(avatarId) }
        var stagedCustomPhotoPath by remember(showEditProfileDialog) { mutableStateOf(customAvatarPath) }
        var nameError by remember(showEditProfileDialog) { mutableStateOf<String?>(null) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                val savedPath = saveProfilePhotoFromUri(context, uri)
                if (savedPath != null) {
                    stagedCustomPhotoPath = savedPath
                }
            }
        }

        val stagedAvatar = AVATAR_OPTIONS.firstOrNull { it.id == stagedAvatarId } ?: AVATAR_OPTIONS[0]
        val stagedHasPhoto = !stagedCustomPhotoPath.isNullOrBlank() && File(stagedCustomPhotoPath!!).exists()

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text(
                    text = "Edit Profile",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Live Avatar Preview
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(if (stagedHasPhoto) Color.Black else stagedAvatar.bg)
                            .border(2.dp, colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (stagedHasPhoto) {
                            AsyncImage(
                                model = File(stagedCustomPhotoPath!!),
                                contentDescription = "Avatar Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(text = stagedAvatar.emoji, fontSize = 38.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Photo Actions (Photo Picker & Remove)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (stagedHasPhoto) "Change Photo" else "Upload Photo",
                                fontSize = 12.sp,
                                color = colors.primary
                            )
                        }

                        if (stagedHasPhoto) {
                            TextButton(
                                onClick = { stagedCustomPhotoPath = null }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove", fontSize = 12.sp, color = Color(0xFFFF5252))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display Name Input with Validation
                    OutlinedTextField(
                        value = stagedName,
                        onValueChange = {
                            if (it.length <= 25) {
                                stagedName = it
                                if (it.trim().isNotEmpty()) nameError = null
                            }
                        },
                        label = { Text("Display Name") },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = {
                            if (nameError != null) {
                                Text(text = nameError!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(text = "${stagedName.length}/25", color = colors.textMuted)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset Avatars Section
                    Text(
                        text = "PRESET AVATARS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            color = colors.textMuted
                        ),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val rows = AVATAR_OPTIONS.chunked(4)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { rowOptions ->
                            Row(
                                horizontalArrangement = Arrangement.SpaceAround,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowOptions.forEach { opt ->
                                    val isSelected = !stagedHasPhoto && stagedAvatarId == opt.id
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(opt.bg)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                stagedAvatarId = opt.id
                                                stagedCustomPhotoPath = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = opt.emoji, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset to Default Action
                    TextButton(
                        onClick = {
                            stagedName = "Vortex Explorer"
                            stagedAvatarId = "cosmic"
                            stagedCustomPhotoPath = null
                            nameError = null
                        },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset to Default",
                            style = MaterialTheme.typography.labelMedium.copy(color = colors.textSecondary)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = stagedName.trim()
                        if (trimmed.isEmpty()) {
                            nameError = "Display name cannot be empty"
                            return@Button
                        }
                        coroutineScope.launch {
                            userPreferencesManager.setDisplayName(trimmed)
                            userPreferencesManager.setAvatarId(stagedAvatarId)
                            userPreferencesManager.setCustomAvatarPath(stagedCustomPhotoPath)
                        }
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.card,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun StatCard(
    count: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    val colors = LocalVortexColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.textSecondary
                )
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
