package app.rork.p7buzvrzcw3frddi8y50ou

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticEntry(val time: Long, val area: String, val message: String)

data class SaveHealth(
    /** Wall-clock time of the last successful save, or 0 when nothing was saved this session. */
    val lastSaved: Long = 0,
    val backups: Int = 0,
    /** Set when the newest save could not be read and an older copy was used. */
    val recovery: String = "",
    /** Set when the loaded save came from an older version of the game. */
    val migration: String = "",
    val lastError: String = "",
)

data class DiagnosticsState(val entries: List<DiagnosticEntry> = emptyList(), val save: SaveHealth = SaveHealth())

/**
 * One place where problems are written down instead of being swallowed. Safe to call from any
 * thread. The most recent entries are kept in memory and in a small log file, so a report still
 * has something to say after a crash and restart.
 */
object Diagnostics {
    private const val MAX = 60
    private const val FILE = "diagnostics.log"
    private val lock = Any()
    private var file: File? = null
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    val state = MutableStateFlow(DiagnosticsState())

    fun attach(dir: File) = synchronized(lock) {
        if (file != null) return@synchronized
        val f = File(dir, FILE)
        file = f
        try {
            if (f.exists()) {
                val old = f.readLines().takeLast(MAX).mapNotNull { line ->
                    val parts = line.split('\t', limit = 3)
                    val time = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                    DiagnosticEntry(time, parts.getOrNull(1).orEmpty(), parts.getOrNull(2).orEmpty())
                }
                state.value = state.value.copy(entries = old + state.value.entries)
            }
        } catch (_: Exception) {
            // The log is a convenience. Failing to read it must never stop the game.
        }
    }

    fun record(area: String, message: String, error: Throwable? = null) = synchronized(lock) {
        val text = if (error == null) message else "$message (${error.javaClass.simpleName}: ${error.message?.take(160) ?: "no detail"})"
        val entries = (state.value.entries + DiagnosticEntry(System.currentTimeMillis(), area, text.replace('\n', ' '))).takeLast(MAX)
        state.value = state.value.copy(entries = entries)
        try {
            file?.writeText(entries.joinToString("\n") { "${it.time}\t${it.area}\t${it.message}" })
        } catch (_: Exception) {
        }
    }

    fun updateSave(change: (SaveHealth) -> SaveHealth) = synchronized(lock) {
        state.value = state.value.copy(save = change(state.value.save))
    }

    fun time(millis: Long): String = if (millis <= 0) "never" else stamp.format(Date(millis))

    /** The plain-text report behind the Share report button. */
    fun report(header: List<String>, contentIssues: List<String>): String = buildString {
        val s = state.value
        appendLine("Greenhollow diagnostics report")
        appendLine("Written ${time(System.currentTimeMillis())}")
        header.forEach { appendLine(it) }
        appendLine()
        appendLine("SAVE")
        appendLine("Last saved: ${time(s.save.lastSaved)}")
        appendLine("Backups available: ${s.save.backups}")
        appendLine("Recovery: ${s.save.recovery.ifEmpty { "none needed" }}")
        appendLine("Upgrade: ${s.save.migration.ifEmpty { "none needed" }}")
        appendLine("Last save error: ${s.save.lastError.ifEmpty { "none" }}")
        appendLine()
        appendLine("CONTENT PACKS")
        if (contentIssues.isEmpty()) appendLine("No problems.") else contentIssues.forEach { appendLine(it) }
        appendLine()
        appendLine("RECENT ERRORS")
        if (s.entries.isEmpty()) appendLine("None recorded.") else s.entries.asReversed().forEach {
            appendLine("${time(it.time)} [${it.area}] ${it.message}")
        }
    }
}
