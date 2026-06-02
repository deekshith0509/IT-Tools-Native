package tech.ittools.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import tech.ittools.app.R
import tech.ittools.app.web.AssetServer
import tech.ittools.app.web.WebHost
import tech.ittools.app.web.buildItToolsWebView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ITToolsScreen(host: WebHost, onReady: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(100) }
    var menuOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var showSystemInfo by remember { mutableStateOf(false) }

    // One WebView for the app's lifetime; survives config changes because the
    // Activity declares configChanges and isn't recreated.
    val webView = remember {
        buildItToolsWebView(context, host, onProgress = { progress = it }, onReady = onReady)
    }

    fun openTool(path: String) {
        webView.loadUrl(AssetServer.START_URL + path)
    }

    // Back button: close System Info first, else walk in-app history, else exit.
    BackHandler(enabled = !showSystemInfo) {
        if (webView.canGoBack()) webView.goBack() else (context as? Activity)?.finish()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(),
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { showSystemInfo = true }) {
                            Icon(Icons.Filled.Terminal, contentDescription = stringResource(R.string.system_info))
                        }
                        IconButton(onClick = { webView.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home)) },
                                leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    webView.loadUrl(AssetServer.START_URL)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_source)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://it-tools.tech"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about)) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    aboutOpen = true
                                },
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // System Info overlays the WebView (kept attached so its state survives).
        if (showSystemInfo) {
            BackHandler { showSystemInfo = false }
            SystemInfoScreen(
                onBack = { showSystemInfo = false },
                onOpenTool = { path ->
                    showSystemInfo = false
                    openTool(path)
                },
            )
        }
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Text(
                "A native, fully offline Android wrapper around the open-source " +
                    "it-tools collection (it-tools.tech by Corentin Thomasset). " +
                    "All ~150 developer utilities run on-device — no server, no browser, " +
                    "no network required.",
            )
        },
    )
}
