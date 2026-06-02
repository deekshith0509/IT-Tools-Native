# IT-Tools — Native Android App

A **native Android application** (Kotlin + Jetpack Compose) that bundles the
entire [it-tools](https://github.com/CorentinTh/it-tools) developer-utility
collection (~150 tools) and runs it **fully offline, on-device** — no Python
HTTP server, no external browser, no network required.

This replaces the previous Kivy approach (a Python `http.server` + "open Chrome
yourself"). Here the tools are loaded **directly inside the app** through an
embedded `WebView` backed by a real `https://` origin, so the whole thing feels
and behaves like one native app.

## Why this design

The it-tools UI is a large, mature Vue 3 single-page app. Re-writing 150+ tools
in native Compose would take months and immediately fall behind upstream.
Instead we ship a **thin, genuinely native shell** and load the *unmodified*
pre-built web bundle inside it:

| Concern | Old Kivy app | This app |
| --- | --- | --- |
| UI toolkit | Kivy (non-native) | Jetpack Compose (native) |
| Serving tools | Python `http.server` thread | `WebViewAssetLoader` (in-process) |
| Where tools open | external browser | in-app `WebView` |
| Network needed | yes (localhost) | **no — fully offline** |
| SPA routing / `localStorage` | flaky over `file://`/localhost | works (real https origin) |
| File pickers / camera / downloads | n/a | wired natively |
| Target | `android.api = 33` | **compileSdk/targetSdk 35 (Android 15)** |

### How "loading directly" works

- The built it-tools `dist/` is vendored at
  [app/src/main/assets/it-tools/](app/src/main/assets/it-tools/).
- [`AssetServer`](app/src/main/java/tech/ittools/app/web/AssetServer.kt) serves
  those files over `https://appassets.androidplatform.net/` via
  `androidx.webkit`'s `WebViewAssetLoader`. A custom `PathHandler` adds an
  `index.html` fallback for Vue Router history routes (e.g.
  `/base64-string-converter`) — the same trick the old Python handler used — and
  routes the PWA service worker through the same loader so workbox caching
  resolves to local files.
- A proper https origin (not `file://`) is what makes `localStorage`, the
  History API, code-split `import()` chunks and the service worker all behave.

## Native integrations

The shell adds the things a bare WebView lacks:

- **Compose `Scaffold`** with a top bar (reload / home / open it-tools.tech /
  about) and a load-progress indicator.
- **Native back navigation** that walks in-app history (incl. SPA route
  changes) before exiting.
- **File pickers** for `<input type="file">` tools (base64 file converter, etc.).
- **Camera / microphone** permission bridging for the camera-recorder & QR tools.
- **Downloads** — `blob:`/`data:` downloads produced in-page are captured by
  [`DownloadBridge`](app/src/main/java/tech/ittools/app/web/DownloadBridge.kt)
  and written to the system **Downloads** folder via `MediaStore`.
- **Splash screen** via `androidx.core:core-splashscreen`.

## Tech stack

- Kotlin **2.0.21**, Jetpack Compose (BOM 2024.10.01), Material 3
- Android Gradle Plugin **8.7.3**, Gradle **8.10.2**
- `compileSdk` / `targetSdk` **35** (Android 15), `minSdk` **26** (Android 8)
- `androidx.webkit` `WebViewAssetLoader` + `ServiceWorkerControllerCompat`

## Build

Prerequisites: JDK 17 and the Android SDK (platform 35 + build-tools 35; the
Gradle build will download them if missing and licenses are accepted).

```bash
cd IT-Tools
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
# install onto a connected device:
./gradlew installDebug
# or:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a release build, sign it and `./gradlew assembleRelease` (R8/minify is off by
default; see [app/proguard-rules.pro](app/proguard-rules.pro) before enabling it).

## CI

- [.github/workflows/build.yml](.github/workflows/build.yml) — portable workflow
  for when this folder is its own repository.
- In the current monorepo, the root-level
  `.github/workflows/it-tools-android.yml` builds this subproject and uploads the
  APK as an artifact.

## Notes on the vendored bundle

Two things about the `dist/` shipped here, both handled automatically at
runtime but worth knowing if you refresh it:

1. **Service worker is neutralised.** The stock it-tools `sw.js` (workbox)
   precaches the entire ~14 MB bundle into CacheStorage on first launch — a
   CPU/IO storm that freezes low-end devices, and pointless here because
   [`AssetServer`](app/src/main/java/tech/ittools/app/web/AssetServer.kt)
   already serves every file locally. `AssetServer` therefore serves a tiny
   self-unregistering `sw.js` instead. Nothing to do on your end.

2. **App env forced to `production`.** This particular `dist/` was built without
   `VITE_VERCEL_ENV`, so it-tools' config defaulted `app.env` to
   `"development"`, which exposed the internal `/c-lib` component-showcase route
   and a dev "UI lib" button in the header (tapping it jumps to `/c-lib`). The
   vendored `assets/it-tools/assets/index-*.js` has that one config default
   patched to `"production"` (`default:"development",env:"VITE_VERCEL_ENV"` →
   `default:"production",…`).

## Updating the bundled tools

Rebuild it-tools (set the env to avoid the dev-mode surface above) and replace
the vendored bundle:

```bash
git clone https://github.com/CorentinTh/it-tools && cd it-tools
pnpm install
VITE_VERCEL_ENV=production pnpm build      # produces a clean production dist/
rm -rf <repo>/IT-Tools/app/src/main/assets/it-tools
cp -r dist <repo>/IT-Tools/app/src/main/assets/it-tools
```

The build expects absolute asset paths (`/assets/...`), which is it-tools'
default. No native code changes are needed for a refresh. (If you can't set the
env var, re-apply the `production` patch from note 2 above.)

## License

The bundled it-tools content is GNU GPLv3 (© Corentin Thomasset). This native
wrapper follows the same license.
