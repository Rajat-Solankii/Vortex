package com.rajatsolankii.vortex.features.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import com.rajatsolankii.vortex.core.ui.appTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.rememberResourceEnvironment

// Normalized X center of the Vortex swirl 'O' mark inside app_logo_wordmark* (294 / 1085).
private const val WORDMARK_O_CENTER_X_RATIO = 294f / 1085f
private val VortexGlowAmber = Color(0xFFF59E0B)
private val VortexGlowViolet = Color(0xFF9333EA)

@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: AppIconOption? = null,
) {
    val resource = rememberAppBrandWordmarkResource(icon)
    val environment = rememberResourceEnvironment()
    val bitmap by produceState(AppBrandWordmarkCache[resource], resource, environment) {
        if (value == null) value = AppBrandWordmarkCache.load(resource, environment)
    }
    val loaded = bitmap
    val painter = if (loaded != null) {
        remember(loaded) { BitmapPainter(loaded) }
    } else {
        painterResource(resource)
    }
    var glowSettled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { glowSettled = true }
    val glowStrength by animateFloatAsState(
        targetValue = if (glowSettled) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "vortexWordmarkGlow",
    )

    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.drawBehind {
            val intrinsic = painter.intrinsicSize
            val srcW = if (intrinsic.width.isFinite() && intrinsic.width > 0f) intrinsic.width else 720f
            val srcH = if (intrinsic.height.isFinite() && intrinsic.height > 0f) intrinsic.height else 200f
            val scale = min(size.width / srcW, size.height / srcH)
            val drawnW = srcW * scale
            val drawnH = srcH * scale
            val left = (size.width - drawnW) / 2f
            val top = (size.height - drawnH) / 2f
            val center = Offset(
                x = left + drawnW * WORDMARK_O_CENTER_X_RATIO,
                y = top + drawnH * 0.5f,
            )
            val radius = (drawnH * 0.98f).coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to VortexGlowAmber.copy(alpha = 0.17f * glowStrength),
                        0.45f to VortexGlowViolet.copy(alpha = 0.25f * glowStrength),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        },
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun PreloadAppBrandWordmark() {
    val resource = rememberAppBrandWordmarkResource(icon = null)
    val environment = rememberResourceEnvironment()
    LaunchedEffect(resource, environment) {
        AppBrandWordmarkCache.load(resource, environment)
    }
}

@Composable
private fun rememberAppBrandWordmarkResource(icon: AppIconOption?): DrawableResource {
    val state by remember {
        AppIconRepository.ensureLoaded()
        AppIconRepository.state
    }.collectAsStateWithLifecycle()
    return icon?.wordmarkResource ?: MaterialTheme.appTheme.wordmarkResource(state.selected)
}

private object AppBrandWordmarkCache {
    // Keep only the currently active wordmark decoded in memory instead of pinning old themes.
    private val active = MutableStateFlow<Pair<DrawableResource, ImageBitmap>?>(null)

    operator fun get(resource: DrawableResource): ImageBitmap? =
        active.value?.takeIf { it.first == resource }?.second

    suspend fun load(resource: DrawableResource, environment: ResourceEnvironment): ImageBitmap {
        get(resource)?.let { return it }
        val bitmap = withContext(Dispatchers.Default) {
            getDrawableResourceBytes(environment, resource).decodeToImageBitmap()
        }
        active.value = resource to bitmap
        return bitmap
    }
}
