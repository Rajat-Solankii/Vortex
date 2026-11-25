package com.rajatsolankii.vortex.features.vortex

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun VortexEmbedWebView(
    url: String,
    modifier: Modifier,
    onLoadingChange: (Boolean) -> Unit,
    onBlocked: () -> Unit,
    onError: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)
    val currentOnBlocked by rememberUpdatedState(onBlocked)
    val currentOnError by rememberUpdatedState(onError)
    val embedHost = remember(url) { VortexAdShield.hostOf(url) }
    val holder = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(url) {
        onDispose {
            holder[0]?.apply {
                stopLoading()
                clearHistory()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
            holder[0] = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0xFF000000.toInt())
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // Anti-redirect / anti-pop-up hardening from the Vortex app.
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    allowFileAccess = false
                    allowContentAccess = false
                }

                webChromeClient = VortexChromeClient(
                    activity = context.findActivity(),
                    onBlocked = { currentOnBlocked() },
                    onProgress = { progress ->
                        if (progress >= 30) evaluateJavascript(VortexAdShield.ANTI_REDIRECT_JS, null)
                        if (progress >= 80) currentOnLoadingChange(false)
                    },
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url?.toString() ?: return false
                        if (VortexAdShield.shouldBlockNavigation(target, embedHost)) {
                            currentOnBlocked()
                            return true
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val requestUrl = request?.url?.toString() ?: return null
                        if (VortexAdShield.isAdUrl(requestUrl)) {
                            view?.post { currentOnBlocked() }
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        currentOnLoadingChange(true)
                        view?.evaluateJavascript(VortexAdShield.ANTI_REDIRECT_JS, null)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        currentOnLoadingChange(false)
                        view?.evaluateJavascript(VortexAdShield.ANTI_REDIRECT_JS, null)
                        view?.evaluateJavascript(VortexAdShield.buildPipAdjustmentJs(false), null)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            currentOnLoadingChange(false)
                            val desc = error?.description?.toString()?.takeIf { it.isNotBlank() }
                                ?: "Web player failed to load"
                            currentOnError?.invoke(desc)
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        val status = errorResponse?.statusCode ?: return
                        if (request?.isForMainFrame == true && status >= 400) {
                            currentOnLoadingChange(false)
                            currentOnError?.invoke("Web player returned HTTP $status")
                        }
                    }
                }

                val refererHeader = VortexMediaExtractor.originOf(url)?.let { mapOf("Referer" to "$it/") }.orEmpty()
                loadUrl(url, refererHeader)
                holder[0] = this
            }
        },
    )
}

/** Refuses pop-up windows and shows the embed's HTML5 fullscreen video over the activity. */
private class VortexChromeClient(
    private val activity: Activity?,
    private val onBlocked: () -> Unit,
    private val onProgress: (Int) -> Unit,
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        onBlocked()
        return false
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        val decor = activity?.window?.decorView as? FrameLayout
        if (view == null || decor == null) {
            callback?.onCustomViewHidden()
            return
        }
        customView?.let { decor.removeView(it) }
        customView = view
        customViewCallback = callback
        view.setBackgroundColor(0xFF000000.toInt())
        decor.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun onHideCustomView() {
        val decor = activity?.window?.decorView as? FrameLayout
        customView?.let { decor?.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.confirm()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.confirm()
        return true
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgress(newProgress)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
