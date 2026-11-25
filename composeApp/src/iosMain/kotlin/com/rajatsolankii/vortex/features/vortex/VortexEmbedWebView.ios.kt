package com.rajatsolankii.vortex.features.vortex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun VortexEmbedWebView(
    url: String,
    modifier: Modifier,
    onLoadingChange: (Boolean) -> Unit,
    onBlocked: () -> Unit,
    onError: ((String) -> Unit)?,
) {
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)
    val currentOnBlocked by rememberUpdatedState(onBlocked)
    val currentOnError by rememberUpdatedState(onError)
    val delegate = remember(url) {
        VortexWebDelegate(
            embedHost = VortexAdShield.hostOf(url),
            onLoadingChange = { currentOnLoadingChange(it) },
            onBlocked = { currentOnBlocked() },
            onError = { msg -> currentOnError?.invoke(msg) },
        )
    }

    UIKitView(
        modifier = modifier,
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                allowsInlineMediaPlayback = true
                mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
                preferences.javaScriptCanOpenWindowsAutomatically = false
                userContentController.addUserScript(
                    WKUserScript(
                        source = VortexAdShield.ANTI_REDIRECT_JS,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                        forMainFrameOnly = false,
                    ),
                )
            }
            WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
                navigationDelegate = delegate
                UIDelegate = delegate
                setOpaque(false)
                backgroundColor = UIColor.blackColor
                scrollView.backgroundColor = UIColor.blackColor
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.navigationDelegate = null
            webView.UIDelegate = null
        },
    )
}

/** Applies the Vortex shield to WKWebView navigation and refuses pop-up windows. */
private class VortexWebDelegate(
    private val embedHost: String?,
    private val onLoadingChange: (Boolean) -> Unit,
    private val onBlocked: () -> Unit,
    private val onError: (String) -> Unit = {},
) : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val target = decidePolicyForNavigationAction.request.URL?.absoluteString
        val isMainFrame = decidePolicyForNavigationAction.targetFrame?.isMainFrame() ?: true
        val block = target != null && (
            VortexAdShield.isAdUrl(target) ||
                (isMainFrame && VortexAdShield.shouldBlockNavigation(target, embedHost))
            )
        if (block) {
            onBlocked()
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        onLoadingChange(true)
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onLoadingChange(false)
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        onLoadingChange(false)
        onError(withError.localizedDescription)
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        onBlocked()
        return null
    }
}
