package com.rajatsolankii.vortex.features.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerResizeModeTest {

    @Test
    fun resizeModeCyclesThroughAllDisplayAndAspectOptions() {
        var mode = PlayerResizeMode.Fit
        val visited = mutableListOf(mode)
        repeat(PlayerResizeMode.entries.size - 1) {
            mode = mode.next()
            visited += mode
        }
        assertEquals(
            listOf(
                PlayerResizeMode.Fit,
                PlayerResizeMode.Fill,
                PlayerResizeMode.Zoom,
                PlayerResizeMode.FitWidth,
                PlayerResizeMode.FitHeight,
                PlayerResizeMode.Ratio16x9,
                PlayerResizeMode.Ratio4x3,
            ),
            visited,
        )
        assertEquals(PlayerResizeMode.Fit, mode.next())
    }

    @Test
    fun pinchPanOffsetResetsAtOneXAndClampsInsideScaledBounds() {
        val screen = IntSize(1920, 1080)
        assertEquals(
            Offset.Zero,
            clampVideoPanOffset(Offset(250f, -180f), scale = 1f, layoutSize = screen),
        )
        assertEquals(
            Offset(960f, -540f),
            clampVideoPanOffset(Offset(2000f, -2000f), scale = 2f, layoutSize = screen),
        )
    }
}
