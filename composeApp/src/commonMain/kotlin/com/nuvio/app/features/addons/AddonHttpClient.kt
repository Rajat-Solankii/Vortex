package com.nuvio.app.features.addons

import com.nuvio.app.features.vortex.VortexBuiltinAddon

internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
): String =
    when {
        // The built-in Vortex source is answered locally from the Vortex API.
        VortexBuiltinAddon.handles(url) -> VortexBuiltinAddon.respond(url)
        forceRefresh -> httpGetTextWithHeaders(
            url = url,
            headers = mapOf("Cache-Control" to "no-cache"),
        )
        else -> httpGetText(url)
    }
