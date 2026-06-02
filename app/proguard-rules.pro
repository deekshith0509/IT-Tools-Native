# Keep the JS <-> Kotlin bridge intact if R8/minify is ever enabled.
-keepclassmembers class tech.ittools.app.web.DownloadBridge {
    public *;
}
-keep class tech.ittools.app.web.** { *; }

# WebView with JS interface (general safety net).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
