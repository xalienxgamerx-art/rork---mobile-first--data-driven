package app.rork.p7buzvrzcw3frddi8y50ou.sim

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

data class Settings(
    /** 0 low, 1 medium, 2 high */
    val graphics: Int = 1,
    val adaptive: Boolean = true,
    val showFps: Boolean = false,
    val leftHanded: Boolean = false,
    val sensitivity: Float = 1f,
    val disabledPacks: Set<String> = emptySet(),
)

/** Versioned persistence for the living world and for player preferences. */
object SaveStore {
    private const val FILE = "world.json"
    private const val PREFS = "greenhollow"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(save: SaveGame): String = json.encodeToString(SaveGame.serializer(), save)

    fun decode(text: String): SaveGame = migrate(json.decodeFromString(SaveGame.serializer(), text))

    /** Hook for future schema changes: older versions are upgraded step by step. */
    private fun migrate(save: SaveGame): SaveGame {
        if (save.version == SAVE_VERSION) return save
        return save.copy(version = SAVE_VERSION)
    }

    fun load(context: Context): SaveGame? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return try {
            decode(file.readText())
        } catch (_: Exception) {
            // Keep the unreadable file around rather than silently destroying a world.
            file.renameTo(File(context.filesDir, "world.corrupt.json"))
            null
        }
    }

    @Synchronized
    fun write(context: Context, text: String) {
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(text)
        val target = File(context.filesDir, FILE)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    fun delete(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    fun loadSettings(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            graphics = p.getInt("graphics", 1).coerceIn(0, 2),
            adaptive = p.getBoolean("adaptive", true),
            showFps = p.getBoolean("showFps", false),
            leftHanded = p.getBoolean("leftHanded", false),
            sensitivity = p.getFloat("sensitivity", 1f).coerceIn(0.4f, 2f),
            disabledPacks = p.getStringSet("disabledPacks", emptySet())?.toSet() ?: emptySet(),
        )
    }

    fun saveSettings(context: Context, s: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("graphics", s.graphics)
            .putBoolean("adaptive", s.adaptive)
            .putBoolean("showFps", s.showFps)
            .putBoolean("leftHanded", s.leftHanded)
            .putFloat("sensitivity", s.sensitivity)
            .putStringSet("disabledPacks", s.disabledPacks)
            .apply()
    }
}
