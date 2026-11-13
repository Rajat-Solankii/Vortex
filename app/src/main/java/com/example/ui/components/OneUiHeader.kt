package com.example.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LocalVortexColors

@Composable
fun OneUiHeader(
    collapseFraction: Float,
    onSearchClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalVortexColors.current
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val eased = LinearOutSlowInEasing.transform(fraction)

    // Dynamic clean height: from 68.dp (expanded) down to 54.dp (collapsed)
    val headerHeight = (68 - (14 * eased)).dp
    val titleSize = (24 - (4 * eased)).sp

    Surface(
        color = colors.background.copy(alpha = 0.92f + (0.08f * eased)),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.vortex_logo),
                    contentDescription = "Vortex Logo",
                    modifier = Modifier
                        .size((32 - (4 * eased)).dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Vortex",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = titleSize,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}
