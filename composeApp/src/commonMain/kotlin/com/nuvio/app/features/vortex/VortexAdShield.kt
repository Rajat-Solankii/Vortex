package com.nuvio.app.features.vortex


/**
 * Vortex ad and redirect shield, shared by every platform's embed player.
 * Ported from the Vortex Android app.
 */
object VortexAdShield {

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

    // Allowed streaming domains for the verified video servers
    private val ALLOWED_STREAM_HOSTS = listOf(
        "vaplayer.ru",
        "videasy.net",
        "player.videasy.net",
        "nhdapi.com",
        "tmdb.org",
        "image.tmdb.org",
        "nxsha.space",
        "modiplay.xyz",
        "primesrc.me",
        "ythd.org",
        "vixsrc.to",
        "vidcore.io",
        "vidrock.net"
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
            val host = hostOf(url) ?: return false
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
            val targetHost = hostOf(targetUrl) ?: return true

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

                // Ensure viewport meta tag exists for responsive embed rendering
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    if (document.head) document.head.appendChild(meta);
                }
                if (meta) {
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                }

                // Inject CSS to ensure iframe and video container fit the mini player / PiP container 100%
                var style = document.getElementById('vortex-embed-style');
                if (!style && document.head) {
                    style = document.createElement('style');
                    style.id = 'vortex-embed-style';
                    style.innerHTML = `
                        html, body {
                            width: 100% !important;
                            height: 100% !important;
                            margin: 0 !important;
                            padding: 0 !important;
                            overflow: hidden !important;
                            background-color: #000000 !important;
                        }
                        iframe, video, embed, object, #player, .player, .artplayer, .jwplayer, .vjs-tech {
                            width: 100% !important;
                            height: 100% !important;
                            max-width: 100vw !important;
                            max-height: 100vh !important;
                            border: 0 !important;
                            object-fit: contain !important;
                        }
                        /* Mini player / PiP adjustments when viewport is compact */
                        @media (max-height: 360px), (max-width: 480px) {
                            .jw-controlbar, .art-controls, .vjs-control-bar {
                                transform: scale(0.85);
                                transform-origin: bottom center;
                            }
                        }
                    `;
                    document.head.appendChild(style);
                }

                // Prevent pause when activity loses focus or enters Picture-in-Picture
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                } catch(e) {}

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

    fun buildPipAdjustmentJs(isInPip: Boolean): String {
        return """
            (function() {
                try {
                    var inPip = $isInPip;
                    var style = document.getElementById('vortex-pip-style');
                    if (inPip) {
                        if (!style && document.head) {
                            style = document.createElement('style');
                            style.id = 'vortex-pip-style';
                            style.innerHTML = `
                                html, body {
                                    width: 100vw !important;
                                    height: 100vh !important;
                                    margin: 0 !important;
                                    padding: 0 !important;
                                    overflow: hidden !important;
                                    background: #000000 !important;
                                }
                                /* Hide in-embed headers, title bars, and branding in PiP mode */
                                .top-bar, #topBar, .title-bar, #titleBar, .title-text,
                                header, nav, [class*="header"], [class*="title-bar"],
                                [class*="top-bar"], [class*="watermark"], [class*="branding"],
                                .server-btn-wrap, .server-dropdown, #serverToggle,
                                [class*="badge"], [class*="logo"], [class*="back-btn"] {
                                    display: none !important;
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                }
                                /* Clean full-viewport embedding for video and inner players */
                                video, iframe, embed, object, #player, .player, #playerContainer, .player-container, #playerFrame, .artplayer, .jwplayer, .vjs-tech, [class*="player"] {
                                    position: fixed !important;
                                    top: 0 !important;
                                    left: 0 !important;
                                    width: 100vw !important;
                                    height: 100vh !important;
                                    max-width: 100vw !important;
                                    max-height: 100vh !important;
                                    margin: 0 !important;
                                    padding: 0 !important;
                                    border: none !important;
                                    object-fit: contain !important;
                                    z-index: 99999 !important;
                                    background: #000000 !important;
                                }
                                .jw-controlbar, .art-controls, .vjs-control-bar {
                                    opacity: 0.8 !important;
                                }
                            `;
                            document.head.appendChild(style);
                        }
                        // Resume video if paused during PiP transition
                        var videos = document.querySelectorAll('video');
                        videos.forEach(function(v) {
                            if (v.paused) {
                                v.play().catch(function(){});
                            }
                        });
                        // Trigger click on play button overlays if playback hasn't started
                        var playButtons = document.querySelectorAll('[class*="play" i], button[aria-label*="play" i], .jw-display-icon-container, .vjs-big-play-button');
                        playButtons.forEach(function(btn) {
                            try { btn.click(); } catch(e){}
                        });
                    } else {
                        if (style) {
                            style.remove();
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /** Lower-cased host of an http(s) URL, or null when it has none. */
    fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        return host.takeIf { it.isNotBlank() }
    }
}
