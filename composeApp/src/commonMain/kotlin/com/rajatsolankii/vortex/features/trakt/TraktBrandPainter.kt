package com.rajatsolankii.vortex.features.trakt

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun traktBrandPainter(asset: TraktBrandAsset): Painter
