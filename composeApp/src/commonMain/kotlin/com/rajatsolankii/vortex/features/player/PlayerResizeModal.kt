package com.rajatsolankii.vortex.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rajatsolankii.vortex.core.ui.nuvio
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.compose_player_pinch_zoom_label
import com.rajatsolankii.vortex.generated.resources.compose_player_reset_zoom
import com.rajatsolankii.vortex.generated.resources.compose_player_resize_modal_title
import com.rajatsolankii.vortex.generated.resources.compose_player_resize_mode_label
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerResizeModal(
    visible: Boolean,
    selectedMode: PlayerResizeMode,
    zoomScale: Float,
    onModeSelected: (PlayerResizeMode) -> Unit,
    onZoomScaleChanged: (Float) -> Unit,
    onResetZoom: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 52.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = minOf(maxWidth, 420.dp)
            val panelMaxHeight = (maxHeight - 36.dp).coerceAtLeast(160.dp).coerceAtMost(540.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(panelWidth)
                    .heightIn(max = panelMaxHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF14151A).copy(alpha = 0.94f))
                    .clickable(enabled = false) {}
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.compose_player_resize_modal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.nuvio.colors.textPrimary,
                )

                Text(
                    text = stringResource(Res.string.compose_player_resize_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerResizeMode.entries.forEach { mode ->
                        val isSelected = mode == selectedMode
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.nuvio.colors.accent.copy(alpha = 0.22f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                )
                                .clickable { onModeSelected(mode) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.nuvio.colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = stringResource(mode.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.nuvio.colors.textPrimary
                                } else {
                                    MaterialTheme.nuvio.colors.textSecondary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stringResource(Res.string.compose_player_pinch_zoom_label)} · ${(zoomScale * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.nuvio.colors.textSecondary,
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onResetZoom() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.nuvio.colors.textPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.compose_player_reset_zoom),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.nuvio.colors.textPrimary,
                            )
                        }
                    }

                    Slider(
                        value = zoomScale.coerceIn(1f, 3f),
                        onValueChange = { value -> onZoomScaleChanged(value.coerceIn(1f, 3f)) },
                        valueRange = 1f..3f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.nuvio.colors.accent,
                            activeTrackColor = MaterialTheme.nuvio.colors.accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}
