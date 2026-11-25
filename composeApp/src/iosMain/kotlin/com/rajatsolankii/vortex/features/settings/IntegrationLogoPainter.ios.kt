package com.rajatsolankii.vortex.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.rajatsolankii.vortex.features.simkl.SimklBrandAsset
import com.rajatsolankii.vortex.features.simkl.simklBrandPainter
import com.rajatsolankii.vortex.generated.resources.Res
import com.rajatsolankii.vortex.generated.resources.introdb_favicon
import com.rajatsolankii.vortex.generated.resources.mdblist_logo
import com.rajatsolankii.vortex.generated.resources.rating_tmdb
import com.rajatsolankii.vortex.generated.resources.trakt_tv_favicon
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun integrationLogoPainter(logo: IntegrationLogo): Painter =
    when (logo) {
        IntegrationLogo.Tmdb -> painterResource(Res.drawable.rating_tmdb)
        IntegrationLogo.Trakt -> painterResource(Res.drawable.trakt_tv_favicon)
        IntegrationLogo.Simkl -> simklBrandPainter(SimklBrandAsset.Glyph)
        IntegrationLogo.MdbList -> painterResource(Res.drawable.mdblist_logo)
        IntegrationLogo.IntroDb -> painterResource(Res.drawable.introdb_favicon)
    }
