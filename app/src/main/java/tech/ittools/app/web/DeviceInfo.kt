package tech.ittools.app.web

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.webkit.WebSettings
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import java.net.NetworkInterface
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class InfoEntry(val key: String, val value: String)
data class InfoSection(val title: String, val entries: List<InfoEntry>)

/**
 * Collects as much device / OS / app / network / runtime context as the
 * platform will give us without special privileges. Everything is surfaced
 * verbatim in the System Info screen — the "expose everything" panel.
 */
object DeviceInfo {

    fun collect(context: Context): List<InfoSection> = buildList {
        add(appSection(context))
        add(deviceSection())
        add(osSection())
        add(cpuSection())
        add(displaySection(context))
        add(memorySection(context))
        add(batterySection(context))
        add(networkSection(context))
        add(interfacesSection())
        add(webViewSection(context))
        add(storageSection())
        add(localeTimeSection())
        add(permissionsSection(context))
    }

    fun dump(sections: List<InfoSection>): String = buildString {
        sections.forEach { s ->
            append("== ").append(s.title).append(" ==\n")
            s.entries.forEach { e -> append(e.key).append(": ").append(e.value).append('\n') }
            append('\n')
        }
    }

    // --- sections -----------------------------------------------------------

    private fun appSection(context: Context) = section("Application") {
        val pm = context.packageManager
        val pkg = context.packageName
        val pi: PackageInfo = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        val ai = pi.applicationInfo
        row("Package", pkg)
        row("Version", "${pi.versionName} (${PackageInfoCompat.getLongVersionCode(pi)})")
        row("First install", date(pi.firstInstallTime))
        row("Last update", date(pi.lastUpdateTime))
        row("Installer", installer(context, pkg))
        row("Debuggable", ((ai?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0).toString())
        row("Target SDK", (ai?.targetSdkVersion ?: -1).toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) row("Min SDK", (ai?.minSdkVersion ?: -1).toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) row("Compile SDK", (ai?.compileSdkVersion ?: -1).toString())
        row("Data dir", ai?.dataDir ?: "?")
        row("Native libs", ai?.nativeLibraryDir ?: "?")
        row("Process 64-bit", (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()).toString())
    }

    private fun deviceSection() = section("Device / Hardware") {
        row("Manufacturer", Build.MANUFACTURER)
        row("Brand", Build.BRAND)
        row("Model", Build.MODEL)
        row("Device", Build.DEVICE)
        row("Product", Build.PRODUCT)
        row("Board", Build.BOARD)
        row("Hardware", Build.HARDWARE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            row("SoC manufacturer", safe { Build.SOC_MANUFACTURER })
            row("SoC model", safe { Build.SOC_MODEL })
        }
        row("Bootloader", Build.BOOTLOADER)
        row("Radio", safe { Build.getRadioVersion() ?: "?" })
        row("Fingerprint", Build.FINGERPRINT)
        row("Host", Build.HOST)
        row("Build tags", Build.TAGS)
        row("Build type", Build.TYPE)
        row("Build user", Build.USER)
    }

    private fun osSection() = section("Operating System") {
        row("Android release", Build.VERSION.RELEASE)
        row("SDK / API level", Build.VERSION.SDK_INT.toString())
        row("Codename", Build.VERSION.CODENAME)
        row("Security patch", safe { Build.VERSION.SECURITY_PATCH })
        row("Build ID", Build.ID)
        row("Incremental", Build.VERSION.INCREMENTAL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) row("Base OS", safe { Build.VERSION.BASE_OS })
        row("Kernel", System.getProperty("os.version") ?: "?")
        row("VM", System.getProperty("java.vm.version") ?: "?")
        row("Rooted (heuristic)", isRooted().toString())
    }

    private fun cpuSection() = section("CPU / ABIs") {
        row("Cores", Runtime.getRuntime().availableProcessors().toString())
        row("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", "))
        row("32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", "))
        row("64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", "))
    }

    private fun displaySection(context: Context) = section("Display") {
        val dm = context.resources.displayMetrics
        row("Resolution", "${dm.widthPixels} x ${dm.heightPixels} px")
        row("Density", "${dm.density}x (${dm.densityDpi} dpi)")
        row("Scaled density", dm.scaledDensity.toString())
        row("Exact DPI", "x=${dm.xdpi}, y=${dm.ydpi}")
        row("Font scale", context.resources.configuration.fontScale.toString())
        row("Refresh rate", safe {
            val rr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display?.refreshRate else null
            rr?.let { "%.1f Hz".format(it) } ?: "?"
        })
        row("Night mode", (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES).toString())
    }

    private fun memorySection(context: Context) = section("Memory & Runtime") {
        val rt = Runtime.getRuntime()
        row("JVM max heap", bytes(rt.maxMemory()))
        row("JVM total", bytes(rt.totalMemory()))
        row("JVM free", bytes(rt.freeMemory()))
        row("JVM used", bytes(rt.totalMemory() - rt.freeMemory()))
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        row("System total RAM", bytes(mi.totalMem))
        row("System available RAM", bytes(mi.availMem))
        row("Low-memory threshold", bytes(mi.threshold))
        row("System low memory", mi.lowMemory.toString())
        row("Large heap limit", "${am.largeMemoryClass} MB")
        row("Standard heap limit", "${am.memoryClass} MB")
    }

    private fun batterySection(context: Context) = section("Battery") {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (i == null) {
            row("Status", "unavailable")
            return@section
        }
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        row("Level", if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "?")
        row("Status", batteryStatus(i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)))
        row("Health", batteryHealth(i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)))
        row("Plugged", batteryPlugged(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)))
        row("Temperature", "${i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0} °C")
        row("Voltage", "${i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)} mV")
        row("Technology", i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?")
    }

    private fun networkSection(context: Context) = section("Network") {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        if (caps == null) {
            row("Status", "offline / no active network")
            return@section
        }
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
        }
        row("Transport", transports.joinToString(", ").ifEmpty { "?" })
        row("Downstream", "${caps.linkDownstreamBandwidthKbps} kbps")
        row("Upstream", "${caps.linkUpstreamBandwidthKbps} kbps")
        row("Metered", (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).toString())
        row("Validated", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString())
        val lp = net.let { cm.getLinkProperties(it) }
        if (lp != null) {
            row("Interface", lp.interfaceName ?: "?")
            row("DNS", lp.dnsServers.joinToString(", ") { it.hostAddress ?: "" }.ifEmpty { "?" })
            row("Link addresses", lp.linkAddresses.joinToString(", ").ifEmpty { "?" })
            if (lp.domains != null) row("Domains", lp.domains!!)
        }
    }

    private fun interfacesSection() = section("Network Interfaces (IPs)") {
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .forEach { nif ->
                    val addrs = nif.inetAddresses.toList()
                        .filterNot { it.isLoopbackAddress }
                        .joinToString(", ") { it.hostAddress ?: "" }
                    if (addrs.isNotBlank()) row(nif.displayName ?: nif.name, addrs)
                }
        }
        if (none()) row("Addresses", "none / unavailable")
    }

    private fun webViewSection(context: Context) = section("WebView Engine") {
        val pkg = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
        row("Provider", pkg?.packageName ?: "?")
        row("Version", pkg?.versionName ?: "?")
        row("User agent", safe { WebSettings.getDefaultUserAgent(context) })
    }

    private fun storageSection() = section("Storage") {
        val data = StatFs(Environment.getDataDirectory().path)
        row("Internal total", bytes(data.totalBytes))
        row("Internal free", bytes(data.availableBytes))
        runCatching {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                val ext = StatFs(Environment.getExternalStorageDirectory().path)
                row("External total", bytes(ext.totalBytes))
                row("External free", bytes(ext.availableBytes))
            }
        }
    }

    private fun localeTimeSection() = section("Locale & Time") {
        val tz = TimeZone.getDefault()
        row("Locale", Locale.getDefault().toString())
        row("Time zone", "${tz.id} (UTC${offset(tz.rawOffset)})")
        row("Now", DateFormat.getDateTimeInstance().format(Date()))
        row("Uptime (since boot)", duration(SystemClock.elapsedRealtime()))
        row("Boot time", date(System.currentTimeMillis() - SystemClock.elapsedRealtime()))
    }

    private fun permissionsSection(context: Context) = section("Permissions") {
        val pi = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val names = pi.requestedPermissions
        val flags = pi.requestedPermissionsFlags
        if (names == null || names.isEmpty()) {
            row("(none declared)", "")
            return@section
        }
        names.forEachIndexed { idx, perm ->
            val granted = flags != null && idx < flags.size &&
                (flags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            row(perm.substringAfterLast('.'), if (granted) "GRANTED" else "denied")
        }
    }

    // --- helpers ------------------------------------------------------------

    private class SectionBuilder(val title: String) {
        val entries = mutableListOf<InfoEntry>()
        fun row(key: String, value: String) { entries.add(InfoEntry(key, value)) }
        fun none() = entries.isEmpty()
    }

    private inline fun section(title: String, block: SectionBuilder.() -> Unit): InfoSection {
        val b = SectionBuilder(title)
        runCatching { b.block() }
        return InfoSection(title, b.entries)
    }

    private inline fun safe(block: () -> String): String = runCatching(block).getOrDefault("?")

    private fun installer(context: Context, pkg: String): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(pkg).installingPackageName ?: "sideloaded"
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(pkg) ?: "sideloaded"
        }
    }.getOrDefault("?")

    private fun isRooted(): Boolean = runCatching {
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
            .any { java.io.File(it).exists() } ||
            Build.TAGS?.contains("test-keys") == true
    }.getOrDefault(false)

    private fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble(); var i = -1
        do { v /= 1024.0; i++ } while (v >= 1024.0 && i < units.size - 1)
        return "%.2f %s".format(v, units[i])
    }

    private fun duration(ms: Long): String {
        val s = ms / 1000
        return "%dh %02dm %02ds".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    private fun offset(ms: Int): String {
        val sign = if (ms >= 0) "+" else "-"
        val m = Math.abs(ms) / 60000
        return "%s%02d:%02d".format(sign, m / 60, m % 60)
    }

    private fun date(ms: Long): String = DateFormat.getDateTimeInstance().format(Date(ms))

    private fun batteryStatus(v: Int) = when (v) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    private fun batteryHealth(v: Int) = when (v) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }

    private fun batteryPlugged(v: Int) = when (v) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        0 -> "On battery"
        else -> "Unknown"
    }
}
