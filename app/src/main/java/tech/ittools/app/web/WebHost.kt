package tech.ittools.app.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/**
 * Capabilities the hosting Activity provides to the WebView clients — things
 * that require an Activity + ActivityResult plumbing and so can't live in the
 * clients themselves.
 */
interface WebHost {

    /**
     * Launch a system file picker for an `<input type="file">`. The [callback]
     * MUST eventually be invoked (with selected uris or `null` on cancel) or the
     * input stays stuck. Returns false if no picker could be launched.
     */
    fun chooseFiles(
        callback: ValueCallback<Array<Uri>?>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean

    /** Request runtime [permissions]; [onResult] gets true iff all granted. */
    fun requestWebPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit)
}
