package tech.ittools.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import tech.ittools.app.ui.ITToolsScreen
import tech.ittools.app.ui.ITToolsTheme
import tech.ittools.app.web.WebHost

/**
 * The single Activity hosting the Compose shell + offline WebView. It also acts
 * as the [WebHost], owning the ActivityResult plumbing for `<input type=file>`
 * pickers and runtime camera/mic permission prompts.
 */
class MainActivity : ComponentActivity(), WebHost {

    private var fileCallback: ValueCallback<Array<Uri>?>? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    // Hold the splash until the WebView paints real content (perceived speed),
    // with a hard timeout so a stalled load can never trap the user on it.
    @Volatile
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !contentReady }
        Handler(Looper.getMainLooper()).postDelayed({ contentReady = true }, SPLASH_TIMEOUT_MS)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerLaunchers()

        setContent {
            ITToolsTheme {
                ITToolsScreen(host = this, onReady = { contentReady = true })
            }
        }
    }

    private fun registerLaunchers() {
        fileChooserLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            val callback = fileCallback
            fileCallback = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
        }
        permissionLauncher = registerForActivityResult(RequestMultiplePermissions()) { grants ->
            val callback = permissionCallback
            permissionCallback = null
            callback?.invoke(grants.values.all { it })
        }
    }

    override fun chooseFiles(
        callback: ValueCallback<Array<Uri>?>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        // Release a previously pending input, if any, before taking a new one.
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (_: ActivityNotFoundException) {
            fileCallback = null
            callback.onReceiveValue(null)
            false
        }
    }

    override fun requestWebPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onResult(true)
            return
        }
        permissionCallback?.invoke(false)
        permissionCallback = onResult
        permissionLauncher.launch(permissions)
    }

    private companion object {
        const val SPLASH_TIMEOUT_MS = 3500L
    }
}
