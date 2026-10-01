package com.example.ui.screens.player

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {

    // Known ad networks, pop-under domains, tracking scripts, and malicious redirects
    private val AD_DOMAINS = listOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adsterra.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "monetag.com",
        "exoclick.com",
        "adkeeper.co.uk",
        "adkeeper.com",
        "yandex.ru/ads",
        "counter.yadro.ru",
        "histats.com",
        "onclickpredictiv.com",
        "onclickmega.com",
        "syndication.exoclick.com",
        "zeroredirect.com",
        "betting",
        "casino",
        "1xbet",
        "mostbet",
        "parimatch",
        "whomeptoo.com",
        "ouo.io",
        "shorte.st",
        "adnxs.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "revcontent.com",
        "trafficjunky.net",
        "tsyndicate.com",
        "clarium.global",
        "coinhive.com",
        "coin-hive.com",
        "crypto-loot.com",
        "juicyads.com",
        "ad-maven.com",
        "hilltopads.net",
        "clickadu.com",
        "adcash.com",
        "inpagepush.com",
        "pushame.com",
        "propu.sh",
        "pushwelcome.com",
        "vdo.ai"
    )

    // Keywords in URLs that indicate ad scripts, tracking beacons, or popup triggers
    private val AD_KEYWORDS = listOf(
        "/ads.js",
        "/advert",
        "/popunder",
        "/popup.js",
        "/track.js",
        "/analytics.js",
        "/vast.xml",
        "banner_id=",
        "ad_type=",
        "click_id=",
        "campaign_id=",
        "traffic_type=",
        "affiliate_id=",
        "/counter.js",
        "/beacon.js"
    )

    // Allowed streaming domains for the three selected servers
    private val ALLOWED_STREAM_HOSTS = listOf(
        "vaplayer.ru",
        "videasy.net",
        "player.videasy.net",
        "nhdapi.com",
        "tmdb.org",
        "image.tmdb.org"
    )

    /**
     * Checks if a request URL matches known ad/tracking signatures.
     */
    fun isAdUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()

        // Check if URL matches ad keywords
        for (keyword in AD_KEYWORDS) {
            if (lowerUrl.contains(keyword)) {
                return true
            }
        }

        // Check host against blacklisted ad domains
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            for (adDomain in AD_DOMAINS) {
                if (host == adDomain || host.endsWith(".$adDomain") || host.contains(adDomain)) {
                    return true
                }
            }
        } catch (_: Exception) {
            return false
        }

        return false
    }

    /**
     * Returns an empty WebResourceResponse to cancel loading ad scripts/requests.
     */
    fun createEmptyResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Validates whether a navigation attempt should be permitted or blocked.
     * Prevents malicious redirects away from the video player (e.g. app store links,
     * external betting sites, intent schemes, whatsapp links, etc.)
     *
     * @return true to BLOCK navigation, false to ALLOW navigation
     */
    fun shouldBlockNavigation(targetUrl: String, currentEmbedHost: String?): Boolean {
        val lower = targetUrl.lowercase()

        // Block non-web intent schemes (e.g. market://, intent://, tg://, etc.)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return true
        }

        // Block app store redirects
        if (lower.contains("play.google.com/store") || lower.contains("market.android.com")) {
            return true
        }

        // Block known ad domains immediately
        if (isAdUrl(targetUrl)) {
            return true
        }

        // Check host
        try {
            val targetUri = Uri.parse(targetUrl)
            val targetHost = targetUri.host?.lowercase() ?: return true

            // If navigating within the current embed host or allowed stream hosts, allow it
            if (currentEmbedHost != null && (targetHost == currentEmbedHost || targetHost.endsWith(".$currentEmbedHost"))) {
                return false
            }

            for (allowedHost in ALLOWED_STREAM_HOSTS) {
                if (targetHost == allowedHost || targetHost.endsWith(".$allowedHost")) {
                    return false
                }
            }

            // Block external site takeovers (redirects attempting to leave the video player)
            return true
        } catch (_: Exception) {
            return true
        }
    }

    /**
     * JavaScript shield code injected into the WebView to neutralize window.open,
     * alert spam, and clickjacking overlay redirects.
     */
    const val ANTI_REDIRECT_JS = """
        (function() {
            try {
                // Neutralize window.open popups
                window.open = function(url, target, features) {
                    console.log('Vortex Shield: Blocked window.open -> ' + url);
                    return null;
                };

                // Neutralize alert & prompt hijackers
                window.alert = function() { return true; };
                window.confirm = function() { return true; };
                window.prompt = function() { return null; };

                // Strip target="_blank" from any links to prevent opening external tabs
                function sanitizeLinks() {
                    var links = document.querySelectorAll('a[target="_blank"]');
                    for (var i = 0; i < links.length; i++) {
                        links[i].removeAttribute('target');
                    }
                }

                // Remove transparent full-screen overlay clickjacks often added by ad scripts
                function removeInvisibleOverlays() {
                    var allElements = document.querySelectorAll('div, a, span');
                    for (var i = 0; i < allElements.length; i++) {
                        var el = allElements[i];
                        var style = window.getComputedStyle(el);
                        if (style && style.position === 'fixed' && style.zIndex > 1000 && (style.opacity === '0' || style.backgroundColor === 'transparent')) {
                            var rect = el.getBoundingClientRect();
                            if (rect.width >= window.innerWidth * 0.8 && rect.height >= window.innerHeight * 0.8) {
                                el.remove();
                                console.log('Vortex Shield: Removed clickjacking overlay');
                            }
                        }
                    }
                }

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        sanitizeLinks();
                        setTimeout(removeInvisibleOverlays, 1000);
                    });
                } else {
                    sanitizeLinks();
                    setTimeout(removeInvisibleOverlays, 1000);
                }
            } catch(e) {}
        })();
    """
}
