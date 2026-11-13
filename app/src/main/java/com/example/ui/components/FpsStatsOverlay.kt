package com.example.ui.components

import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalVortexColors
import com.example.util.DevicePerformanceUtils

@Composable
fun FpsStatsOverlay(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fps by remember { mutableIntStateOf(120) }
    var refreshRate by remember { mutableFloatStateOf(120f) }

    DisposableEffect(Unit) {
        var frameCount = 0
        var lastTime = System.nanoTime()
        var running = true

        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                frameCount++
                val elapsedNanos = frameTimeNanos - lastTime
                if (elapsedNanos >= 1_000_000_000L) {
                    fps = ((frameCount * 1_000_000_000L) / elapsedNanos).toInt()
                    frameCount = 0
                    lastTime = frameTimeNanos

                    val display = DevicePerformanceUtils.getTargetDisplay(context)
                    refreshRate = display?.refreshRate ?: 120f
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)

        onDispose {
            running = false
            choreographer.removeFrameCallback(callback)
        }
    }

    val colors = LocalVortexColors.current
    val isSmooth = fps >= 55
    val indicatorColor = if (isSmooth) Color(0xFF00E676) else Color(0xFFFF9100)

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 10.dp, end = 16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.82f))
                .border(1.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${fps.coerceAtMost(120)} FPS",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = " • ",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
            Text(
                text = "${refreshRate.toInt()}Hz",
                color = colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
