package tech.ittools.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebView

class ITToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable chrome://inspect remote debugging for debuggable builds only.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
