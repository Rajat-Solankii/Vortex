package com.rajatsolankii.vortex.features.details.components

import androidx.compose.ui.graphics.ImageBitmap
import coil3.request.SuccessResult

internal expect fun loadedBackdropImageBitmap(result: SuccessResult): ImageBitmap?
