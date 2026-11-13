package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.cast.MediaSourceClassifier
import com.example.cast.StreamSourceType

object CastHelper {

    const val WEB_VIDEO_CASTER_PACKAGE = "com.instantbits.cast.webvideo"
    const val BUBBLE_UPNP_PACKAGE = "com.bubblesoft.android.bubbleupnp"
    const val VLC_PACKAGE = "org.videolan.vlc"

    /**
     * Detect whether the device manufacturer is Samsung.
     */
    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    /**
     * Launch standard documented Android Cast or Wireless Display settings.
     * Verifies that the intent resolves before attempting to start an Activity.
     *
     * @return true if an intent was successfully resolved and launched, false otherwise.
     */
    fun openSystemCastSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        )

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Unified Screen Mirroring handler for Samsung and non-Samsung devices.
     *
     * - On Samsung Galaxy devices: invokes [onShowSamsungInstructions] so users receive
     *   genuine, verified Quick Settings Smart View guidance without relying on undocumented APIs.
     * - On other Android devices: resolves and launches Android Cast settings, falling back
     *   to display settings with clear Quick Settings instructions.
     */
    fun startScreenMirroring(
        context: Context,
        onShowSamsungInstructions: () -> Unit
    ) {
        if (isSamsungDevice()) {
            onShowSamsungInstructions()
            return
        }

        // Non-Samsung Android devices: attempt system Cast settings
        val launched = openSystemCastSettings(context)
        if (launched) {
            Toast.makeText(
                context,
                "Select your TV, Windows PC, or Wireless Display receiver",
                Toast.LENGTH_LONG
            ).show()
        } else {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                Toast.makeText(
                    context,
                    "Swipe down from the top of your screen twice and tap 'Cast' or 'Screen Cast'",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Screen mirroring settings unavailable on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Cast direct stream via Web Video Caster, BubbleUPnP, or another casting app.
     * Web Video Caster supports Chromecast, Roku, Apple TV (AirPlay), Fire TV, LG webOS, and Samsung Tizen.
     */
    fun openInCastApp(context: Context, streamUrl: String, title: String) {
        val pm = context.packageManager
        val classified = MediaSourceClassifier.classify(streamUrl)

        // Check if Web Video Caster is installed
        val wvcIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(streamUrl)
            setPackage(WEB_VIDEO_CASTER_PACKAGE)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (wvcIntent.resolveActivity(pm) != null) {
            try {
                context.startActivity(wvcIntent)
                Toast.makeText(context, "Streaming to TV via Web Video Caster...", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Exception) {}
        }

        if (classified.isDirectPlayable) {
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(streamUrl), classified.mimeType)
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(genericIntent, "Cast '$title' to TV via:").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(chooser)
                return
            } catch (_: Exception) {}
        }

        // Offer to install Web Video Caster from Play Store
        openWebVideoCasterOnPlayStore(context)
    }

    /**
     * Open Google Play Store page for Web Video Caster (Free gold-standard web-to-TV casting app).
     */
    fun openWebVideoCasterOnPlayStore(context: Context) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$WEB_VIDEO_CASTER_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$WEB_VIDEO_CASTER_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
        Toast.makeText(
            context,
            "Install Web Video Caster to cast movies to Samsung, LG, Fire TV, Roku & Apple TV",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Open in external player like VLC, MX Player, or Just Player.
     * Accurately distinguishes between direct media streams (.m3u8, .mp4) and web embed pages.
     *
     * @return true if launched directly, false if source is a web embed requiring web browser fallback
     */
    fun openInExternalPlayer(context: Context, streamUrl: String, title: String): Boolean {
        val classified = MediaSourceClassifier.classify(streamUrl)

        if (classified.isDirectPlayable) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(streamUrl), classified.mimeType)
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Play '$title' with:").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(chooser)
                true
            } catch (_: Exception) {
                openInBrowser(context, streamUrl)
                false
            }
        } else {
            // Source is a web embed (HTML/iframe). Launching directly in VLC as video fails.
            return false
        }
    }

    /**
     * Open stream URL in device default browser (Chrome, Samsung Internet, Firefox, etc.)
     */
    fun openInBrowser(context: Context, url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copy stream link to clipboard for opening on Smart TV browser.
     */
    fun copyStreamLink(context: Context, streamUrl: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Vortex Stream URL", streamUrl)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            "TV Link Copied! Open this URL in your TV browser (Samsung Internet, LG webOS, Silk, or Android TV)",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Share stream link to TV or nearby device.
     */
    fun shareStreamLink(context: Context, streamUrl: String, title: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "Watch '$title' on TV:\n$streamUrl")
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Send link to TV or Device").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }
}
