package tech.ittools.app.web

import android.content.Context
import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.PathHandler
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Serves the bundled it-tools build (`assets/it-tools/`) over a real
 * `https://appassets.androidplatform.net` origin via [WebViewAssetLoader].
 *
 * Using a proper https origin instead of `file://` is what makes the SPA
 * behave: the History API, `localStorage` and `fetch()` of code-split chunks
 * all work the same way they do on the live site.
 *
 * The custom [PathHandler] adds two things [WebViewAssetLoader]'s default
 * assets handler lacks:
 *  1. an `index.html` fallback for Vue Router history-mode routes (e.g.
 *     `/base64-string-converter`), mirroring the old Python handler; and
 *  2. **neutralising the workbox service worker** — see [DISABLED_SW]. The PWA
 *     SW would otherwise precache the whole ~14 MB bundle into CacheStorage on
 *     launch, freezing low-end devices, for zero benefit: the asset loader
 *     already serves everything locally and offline.
 */
object AssetServer {

    const val DOMAIN = "appassets.androidplatform.net"
    const val START_URL = "https://$DOMAIN/"

    private const val ASSET_ROOT = "it-tools"
    private const val INDEX = "$ASSET_ROOT/index.html"

    /** Content-hashed Vite assets are immutable; let the WebView cache them hard. */
    private const val IMMUTABLE = "public, max-age=31536000, immutable"
    private const val NO_CACHE = "no-cache"

    /**
     * Replacement body served for `/sw.js`: a service worker that unregisters
     * itself and drops any caches a previous build created. This prevents the
     * workbox precache storm on fresh installs and cleans up devices that had
     * registered the heavy SW — without forcing an extra page reload.
     */
    private val DISABLED_SW = """
        self.addEventListener('install', function () { self.skipWaiting(); });
        self.addEventListener('activate', function (event) {
          event.waitUntil((async function () {
            try {
              var keys = await caches.keys();
              await Promise.all(keys.map(function (k) { return caches.delete(k); }));
            } catch (e) {}
            try { await self.registration.unregister(); } catch (e) {}
          })());
        });
    """.trimIndent()

    fun create(context: Context): WebViewAssetLoader {
        val handler = ItToolsPathHandler(context.applicationContext.assets)
        return WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/", handler)
            .build()
    }

    private class ItToolsPathHandler(private val assets: AssetManager) : PathHandler {

        override fun handle(path: String): WebResourceResponse {
            val relative = path.removePrefix("/")
            if (relative.isEmpty()) return serveIndex()

            // Disarm the workbox service worker (see DISABLED_SW).
            if (relative == "sw.js") {
                return response(
                    ByteArrayInputStream(DISABLED_SW.toByteArray()),
                    "application/javascript",
                    NO_CACHE,
                )
            }

            return try {
                val stream = assets.open("$ASSET_ROOT/$relative")
                response(stream, mimeOf(relative), cacheFor(relative))
            } catch (_: IOException) {
                // No such file. If the request looks like a client-side route
                // (no file extension on the last segment) hand back the SPA
                // shell; otherwise it's a genuinely missing asset -> 404.
                if (!relative.substringAfterLast('/').contains('.')) {
                    serveIndex()
                } else {
                    notFound()
                }
            }
        }

        private fun serveIndex(): WebResourceResponse =
            response(assets.open(INDEX), "text/html", NO_CACHE)

        private fun cacheFor(relative: String): String =
            if (relative.startsWith("assets/")) IMMUTABLE else NO_CACHE

        private fun response(
            stream: InputStream,
            mime: String,
            cacheControl: String,
        ): WebResourceResponse {
            val encoding = if (isText(mime)) "utf-8" else null
            return WebResourceResponse(mime, encoding, stream).apply {
                responseHeaders = mapOf("Cache-Control" to cacheControl)
            }
        }

        private fun notFound(): WebResourceResponse = WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun isText(mime: String): Boolean =
        mime.startsWith("text/") ||
            mime == "application/javascript" ||
            mime == "application/json" ||
            mime == "application/manifest+json" ||
            mime == "image/svg+xml"

    private fun mimeOf(path: String): String =
        when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "webmanifest" -> "application/manifest+json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "wasm" -> "application/wasm"
            "xml" -> "text/xml"
            "txt" -> "text/plain"
            "map" -> "application/json"
            else -> "application/octet-stream"
        }
}
