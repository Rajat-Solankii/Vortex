package com.rajatsolankii.vortex.features.simkl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.simkl_logo_glyph
import com.rajatsolankii.vortex.generated.resources.simkl_logo_wordmark
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun simklBrandPainter(asset: SimklBrandAsset): Painter =
    when (asset) {
        SimklBrandAsset.Glyph -> painterResource(Res.drawable.simkl_logo_glyph)
        SimklBrandAsset.Wordmark -> painterResource(Res.drawable.simkl_logo_wordmark)
    }
