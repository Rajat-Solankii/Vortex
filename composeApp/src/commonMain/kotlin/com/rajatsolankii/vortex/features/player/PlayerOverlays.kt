package com.rajatsolankii.vortex.features.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatsolankii.vortex.core.ui.nuvioTypeScale
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.compose_player_episode_code_full
import com.rajatsolankii.vortex.generated.resources.compose_player_go_back
import com.rajatsolankii.vortex.generated.resources.compose_player_playback_error
import com.rajatsolankii.vortex.generated.resources.compose_player_youre_watching
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import com.rajatsolankii.vortex.core.i18n.localizedEpisodeTitle

internal enum class GestureFeedbackIcon {
    Speed,
    Brightness,
    Volume,
    VolumeMuted,
    SeekForward,
    SeekBackward,
}

internal data class GestureFeedbackState(
    val message: String? = null,
    val messageRes: StringResource? = null,
    val messageArgs: List<Any> = emptyList(),
    val icon: GestureFeedbackIcon = GestureFeedbackIcon.Speed,
    val isDanger: Boolean = false,
    val secondaryMessage: String? = null,
    val secondaryMessageRes: StringResource? = null,
    val secondaryMessageArgs: List<Any> = emptyList(),
    val secondaryMessageColor: Color? = null,
    val level: Float? = null,
)


@Composable
internal fun GestureFeedbackPill(
    feedback: GestureFeedbackState,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (feedback.isDanger) {
        Color(0xFF5D1F1F).copy(alpha = 0.88f)
    } else {
        Color.Black.copy(alpha = 0.75f)
    }
    val iconBackgroundColor = if (feedback.isDanger) {
        Color(0xFFFF8A80).copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.15f)
    }
    val icon = when (feedback.icon) {
        GestureFeedbackIcon.Speed -> Icons.Rounded.Speed
        GestureFeedbackIcon.Brightness -> Icons.Rounded.Brightness6
        GestureFeedbackIcon.Volume -> Icons.AutoMirrored.Rounded.VolumeUp
        GestureFeedbackIcon.VolumeMuted -> Icons.AutoMirrored.Rounded.VolumeOff
        GestureFeedbackIcon.SeekForward -> Icons.Rounded.FastForward
        GestureFeedbackIcon.SeekBackward -> Icons.Rounded.FastRewind
    }
    val iconTint = if (feedback.isDanger) Color(0xFFFFC1C1) else Color.White
    val messageText = feedback.messageRes?.let { resource ->
        stringResource(resource, *feedback.messageArgs.toTypedArray())
    } ?: feedback.message.orEmpty()
    val secondaryMessageText = feedback.secondaryMessageRes?.let { resource ->
        stringResource(resource, *feedback.secondaryMessageArgs.toTypedArray())
    } ?: feedback.secondaryMessage

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = messageText,
            style = MaterialTheme.nuvioTypeScale.bodyLg.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        secondaryMessageText?.let { secondaryMessage ->
            Text(
                text = secondaryMessage,
                style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                color = feedback.secondaryMessageColor ?: Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
internal fun PauseMetadataOverlay(
    title: String,
    logo: String?,
    isEpisode: Boolean,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    pauseDescription: String?,
    providerName: String,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: Dp,
    modifier: Modifier = Modifier,
) {
    var logoLoadError by remember(logo) { mutableStateOf(false) }
    val logoUrl = logo?.takeIf { it.isNotBlank() }

    BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        val compactHeight = maxHeight < 420.dp
        val veryCompactHeight = maxHeight < 340.dp
        val topPadding = if (compactHeight) 24.dp else 40.dp
        val bottomPadding = when {
            veryCompactHeight -> 24.dp
            compactHeight -> 40.dp
            else -> 120.dp
        }
        val logoHeight = when {
            veryCompactHeight -> 48.dp
            compactHeight -> 64.dp
            else -> 96.dp
        }
        val titleFontScale = if (compactHeight) 1.35f else 1.8f
        val descriptionStyle = if (compactHeight) {
            MaterialTheme.nuvioTypeScale.bodyMd.copy(lineHeight = 20.sp)
        } else {
            MaterialTheme.nuvioTypeScale.bodyLg.copy(lineHeight = 24.sp)
        }
        val descriptionMaxLines = if (compactHeight) 2 else 3
        val descriptionWidthFraction = if (compactHeight) 0.82f else 0.62f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalSafePadding + metrics.horizontalPadding,
                    end = horizontalSafePadding + metrics.horizontalPadding,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = stringResource(Res.string.compose_player_youre_watching),
                style = MaterialTheme.nuvioTypeScale.bodyLg,
                color = Color(0xFFB8B8B8),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(if (compactHeight) 8.dp else 12.dp))

            if (logoUrl != null && !logoLoadError) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.height(logoHeight),
                    onError = { logoLoadError = true },
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.nuvioTypeScale.displayMd.copy(
                        fontSize = max(metrics.titleSize.value * titleFontScale, 32f).sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = Color.White,
                    maxLines = if (compactHeight) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val episodeInfo = if (isEpisode && seasonNumber != null && episodeNumber != null) {
                stringResource(Res.string.compose_player_episode_code_full, seasonNumber, episodeNumber)
            } else {
                providerName
            }

            Text(
                text = episodeInfo,
                style = MaterialTheme.nuvioTypeScale.bodyLg,
                color = Color(0xFFCCCCCC),
                modifier = Modifier.padding(top = if (compactHeight) 6.dp else 8.dp),
            )

            if (!episodeTitle.isNullOrBlank()) {
                Text(
                    text = localizedEpisodeTitle(episodeTitle, episodeNumber) ?: episodeTitle,
                    style = MaterialTheme.nuvioTypeScale.titleLg,
                    color = Color.White,
                    maxLines = if (compactHeight) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (compactHeight) 8.dp else 12.dp),
                )
            }

            if (!pauseDescription.isNullOrBlank()) {
                Text(
                    text = pauseDescription,
                    style = descriptionStyle,
                    color = Color(0xFFD6D6D6),
                    softWrap = true,
                    textAlign = TextAlign.Start,
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = if (compactHeight) 10.dp else 16.dp)
                        .fillMaxWidth(descriptionWidthFraction),
                )
            }
        }
    }
}

@Composable
internal fun ErrorModal(
    message: String,
    onDismiss: () -> Unit,
    onTryNextServer: (() -> Unit)? = null,
    onOpenWebPlayer: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val hasBackupAction = onTryNextServer != null || onOpenWebPlayer != null || onRetry != null
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 300.dp || maxWidth < 460.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 18.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.compose_player_playback_error),
                style = if (compact) {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.nuvioTypeScale.displaySm.copy(fontWeight = FontWeight.Bold)
                },
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = if (compact) {
                    MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                } else {
                    MaterialTheme.nuvioTypeScale.bodyLg.copy(lineHeight = 24.sp)
                },
                color = Color.White.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
                maxLines = if (compact) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .padding(top = if (compact) 2.dp else 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onTryNextServer != null) {
                    ErrorActionButton(
                        label = "Next Server",
                        primary = true,
                        compact = compact,
                        onClick = onTryNextServer,
                    )
                }
                if (onOpenWebPlayer != null) {
                    ErrorActionButton(
                        label = "Web Player",
                        primary = onTryNextServer == null,
                        compact = compact,
                        onClick = onOpenWebPlayer,
                    )
                }
                if (onRetry != null) {
                    ErrorActionButton(
                        label = "Retry",
                        primary = onTryNextServer == null && onOpenWebPlayer == null,
                        compact = compact,
                        onClick = onRetry,
                    )
                }
                ErrorActionButton(
                    label = stringResource(Res.string.compose_player_go_back),
                    primary = !hasBackupAction,
                    compact = compact,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ErrorActionButton(
    label: String,
    primary: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (primary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
        border = if (primary) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 18.dp,
                vertical = if (compact) 8.dp else 11.dp,
            ),
            style = (if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.nuvioTypeScale.bodyLg)
                .copy(fontWeight = FontWeight.SemiBold),
            color = if (primary) MaterialTheme.colorScheme.onPrimary else Color.White,
            maxLines = 1,
        )
    }
}
