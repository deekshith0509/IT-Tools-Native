package tech.ittools.app.web

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/**
 * Bridges browser downloads to Android storage.
 *
 * Many it-tools utilities "download" results as `blob:` or `data:` URLs created
 * in JavaScript. A stock WebView [android.webkit.DownloadListener] never fires
 * for those, so files silently vanish. [INJECTION] intercepts anchor downloads
 * in the page and forwards the bytes here through [saveBase64]; [handleDownload]
 * covers the remaining `data:`/`http(s)` cases.
 *
 * Files land in the public Downloads collection via MediaStore on API 29+
 * (no storage permission needed); on older devices they go to the app's own
 * external Downloads dir.
 */
class DownloadBridge(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    /** JS-callable: persist base64 [data] as [filename] with the given [mime]. */
    @JavascriptInterface
    fun saveBase64(data: String, filename: String, mime: String) {
        io.execute {
            runCatching {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                writeToDownloads(sanitize(filename), nonBlank(mime), bytes)
            }.onSuccess { name ->
                toast("Saved $name to Downloads")
            }.onFailure {
                toast("Download failed: ${it.message}")
            }
        }
    }

    /** Native [android.webkit.DownloadListener] entry point. */
    fun handleDownload(url: String, contentDisposition: String?, mimeType: String?) {
        when {
            url.startsWith("data:") -> saveDataUri(url, contentDisposition, mimeType)
            URLUtil.isNetworkUrl(url) -> enqueueNetworkDownload(url, mimeType)
            // blob: is handled in-page by INJECTION; nothing to do here.
        }
    }

    private fun saveDataUri(url: String, contentDisposition: String?, mimeType: String?) {
        io.execute {
            runCatching {
                val comma = url.indexOf(',')
                val meta = url.substring("data:".length, comma)
                val payload = url.substring(comma + 1)
                val isBase64 = meta.contains(";base64")
                val mime = meta.substringBefore(';').ifBlank { nonBlank(mimeType) }
                val bytes = if (isBase64) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    Uri.decode(payload).toByteArray()
                }
                val name = URLUtil.guessFileName(url, contentDisposition, mime)
                writeToDownloads(sanitize(name), mime, bytes)
            }.onSuccess { name ->
                toast("Saved $name to Downloads")
            }.onFailure {
                toast("Download failed: ${it.message}")
            }
        }
    }

    private fun enqueueNetworkDownload(url: String, mimeType: String?) {
        runCatching {
            val name = URLUtil.guessFileName(url, null, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            toast("Downloading $name…")
        }.onFailure { toast("Download failed: ${it.message}") }
    }

    /** Returns the final file name written. */
    private fun writeToDownloads(filename: String, mime: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values)
                ?: error("Could not create Downloads entry")
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out).write(bytes)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return filename
        }

        // API 26–28: app-specific external Downloads dir (no permission).
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val file = uniqueFile(dir, filename)
        file.outputStream().use { it.write(bytes) }
        return file.name
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base($i)$ext")
            i++
        }
        return candidate
    }

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "download" }

    private fun nonBlank(mime: String?): String =
        mime?.takeIf { it.isNotBlank() } ?: "application/octet-stream"

    private fun toast(message: String) {
        main.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        /** JS name exposed via [android.webkit.WebView.addJavascriptInterface]. */
        const val NAME = "AndroidDownloader"

        /**
         * Injected after each page load: routes `blob:`/`data:` anchor
         * downloads (clicked or programmatic) back to [saveBase64]. Idempotent.
         */
        val INJECTION = """
            (function(){
              if (window.__ittoolsDL) return; window.__ittoolsDL = true;
              function save(b64, name, mime){
                try { $NAME.saveBase64(b64, name || 'download', mime || 'application/octet-stream'); }
                catch (e) { console.error('saveBase64 failed', e); }
              }
              function fromBlob(url, name){
                fetch(url).then(function(r){ return r.blob(); }).then(function(b){
                  var fr = new FileReader();
                  fr.onloadend = function(){ save(('' + fr.result).split(',')[1], name, b.type); };
                  fr.readAsDataURL(b);
                }).catch(function(e){ console.error('blob download failed', e); });
              }
              function fromData(url, name){
                var comma = url.indexOf(',');
                var meta = url.substring(5, comma);
                var data = url.substring(comma + 1);
                var mime = (meta.split(';')[0]) || 'application/octet-stream';
                var b64 = meta.indexOf(';base64') >= 0 ? data : btoa(unescape(encodeURIComponent(decodeURIComponent(data))));
                save(b64, name, mime);
              }
              function intercept(href, name){
                if (!href) return false;
                if (href.indexOf('blob:') === 0){ fromBlob(href, name); return true; }
                if (href.indexOf('data:') === 0){ fromData(href, name); return true; }
                return false;
              }
              var origClick = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function(){
                try {
                  if (this.hasAttribute('download') && intercept(this.href, this.getAttribute('download'))) return;
                } catch (e) {}
                return origClick.apply(this, arguments);
              };
              document.addEventListener('click', function(e){
                var a = (e.target && e.target.closest) ? e.target.closest('a[download]') : null;
                if (!a) return;
                try {
                  if (intercept(a.href, a.getAttribute('download'))){ e.preventDefault(); e.stopPropagation(); }
                } catch (err) {}
              }, true);
            })();
        """.trimIndent()
    }
}
