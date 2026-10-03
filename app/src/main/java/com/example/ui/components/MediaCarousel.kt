package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.ui.theme.VortexPurple
import com.example.ui.theme.VortexTextPrimary
import com.example.ui.theme.VortexTextSecondary

@Composable
fun MediaCarousel(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onViewAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = VortexPurple
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Accent indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = VortexTextPrimary
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            if (onViewAllClick != null) {
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .clickable { onViewAllClick?.invoke() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = "${items.size} titles",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = VortexTextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontal Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = items,
                key = { "${it.id}_${it.mediaType}" }
            ) { media ->
                MediaPosterCard(
                    media = media,
                    onClick = { onItemClick(media) }
                )
            }
        }
    }
}
