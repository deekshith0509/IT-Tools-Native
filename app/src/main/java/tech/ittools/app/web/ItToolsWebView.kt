package tech.ittools.app.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

private const val TAG = "ITTools"

/**
 * Builds the single [WebView] that renders the bundled it-tools app, fully
 * offline, over the [AssetServer] https origin. All the "native" behaviour the
 * old Kivy + browser setup lacked lives here: in-app navigation, file inputs,
 * camera/mic permission prompts, blob downloads and external-link hand-off.
 *
 * [onReady] fires once when the first page has finished loading — used to drop
 * the splash screen only after real content is on screen.
 */
@SuppressLint("SetJavaScriptEnabled")
fun buildItToolsWebView(
    context: Context,
    host: WebHost,
    onProgress: (Int) -> Unit,
    onReady: () -> Unit,
): WebView {
    val assetLoader = AssetServer.create(context)
    val downloads = DownloadBridge(context)

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        // Paint the WebView the same dark as the splash so there's no white
        // flash between splash and first content paint.
        setBackgroundColor(Color.parseColor("#1E1E2A"))

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            // Content is local; deny file:// access for safety.
            allowFileAccess = false
            allowContentAccess = false
            // Everything is bundled — prefer the cache hard, never block on net.
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        // Keep the renderer at high priority even when backgrounded briefly, and
        // pre-raster just-off-screen content so scrolling/resume stays smooth.
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        settings.offscreenPreRaster = true

        addJavascriptInterface(downloads, DownloadBridge.NAME)

        webViewClient = ItToolsWebViewClient(assetLoader, onReady)
        webChromeClient = ItToolsWebChromeClient(host, onProgress)

        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            downloads.handleDownload(url, contentDisposition, mimeType)
        }

        loadUrl(AssetServer.START_URL)
    }
}

private class ItToolsWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val onReady: () -> Unit,
) : WebViewClient() {

    private var firstLoadDone = false

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url
        // Keep our own origin inside the WebView; everything else (real http
        // links, mailto:, tel:, intent:) goes to the system handler.
        if (url.host == AssetServer.DOMAIN) return false
        return openExternally(view.context, url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        // Re-arm the blob/data download interceptor on every navigation.
        view.evaluateJavascript(DownloadBridge.INJECTION, null)
        if (!firstLoadDone) {
            firstLoadDone = true
            ConsoleLog.add("INFO", "First page loaded: ${url ?: ""}", "native")
            onReady()
        }
    }

    private fun openExternally(context: Context, uri: Uri): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "No handler for $uri", e)
        false
    }
}

private class ItToolsWebChromeClient(
    private val host: WebHost,
    private val onProgress: (Int) -> Unit,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>?>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.chooseFiles(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) {
        val needed = buildList {
            if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                add(Manifest.permission.CAMERA)
            }
            if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (needed.isEmpty()) {
            request.grant(request.resources)
            return
        }
        host.requestWebPermissions(needed.toTypedArray()) { granted ->
            if (granted) request.grant(request.resources) else request.deny()
        }
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val source = message.sourceId()?.substringAfterLast('/').orEmpty() +
            ":" + message.lineNumber()
        Log.d(TAG, "[web] ${message.message()} ($source)")
        // Mirror every console line into the in-app log so the System Info
        // screen shows exactly what the page is doing — nothing hidden.
        ConsoleLog.add(message.messageLevel().name, message.message(), source)
        return true
    }
}
