plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tech.ittools.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tech.ittools.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            // The payload is a static, bundled web app; keep R8 off so the
            // @JavascriptInterface bridge and WebView callbacks are never
            // stripped/renamed. Flip on later with the rules in proguard-rules.pro.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Store the web bundle uncompressed so the WebView can read/mmap each
        // asset directly with no per-request inflate — meaningfully faster cold
        // loads for the big JS chunks. Costs a larger APK (text is already
        // gzipped over the wire in normal browsers; here it's all local).
        noCompress.addAll(
            listOf("js", "mjs", "css", "json", "webmanifest", "svg", "ttf", "woff", "woff2", "html", "map"),
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Don't fail the build on lint; this is a thin native shell over a
        // vendored web bundle.
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
