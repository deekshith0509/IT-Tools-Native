package tech.ittools.app.web

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer of everything the embedded page logs to the JS console
 * (plus our own notes). Surfaced verbatim in the System Info screen so nothing
 * the app does is hidden from the user. Capped to avoid unbounded growth.
 */
object ConsoleLog {

    private const val MAX = 500
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Observable by Compose; mutated on the main thread from the WebView clients. */
    val entries: SnapshotStateList<Entry> = mutableStateListOf()

    data class Entry(val ts: String, val level: String, val message: String, val source: String)

    fun add(level: String, message: String, source: String = "") {
        entries.add(Entry(time.format(Date()), level, message, source))
        while (entries.size > MAX) entries.removeAt(0)
    }

    fun clear() = entries.clear()

    fun dump(): String = entries.joinToString("\n") { e ->
        "[${e.ts}] ${e.level}: ${e.message}${if (e.source.isNotBlank()) "  (${e.source})" else ""}"
    }
}
