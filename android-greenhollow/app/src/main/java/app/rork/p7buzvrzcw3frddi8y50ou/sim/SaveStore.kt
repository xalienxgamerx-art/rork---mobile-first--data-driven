package app.rork.p7buzvrzcw3frddi8y50ou.sim

import android.content.Context
import app.rork.p7buzvrzcw3frddi8y50ou.Diagnostics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class Settings(
    /** 0 low, 1 medium, 2 high */
    val graphics: Int = 1,
    val adaptive: Boolean = true,
    val showFps: Boolean = false,
    val leftHanded: Boolean = false,
    val sensitivity: Float = 1f,
    val disabledPacks: Set<String> = emptySet(),
)

/** A decoded save plus what had to be done to read it. */
class Decoded(val save: SaveGame, val fromVersion: Int)

/** What loading found. [notice] is a sentence for the player when something unusual happened. */
class LoadResult(val save: SaveGame?, val notice: String = "")

class NewerSaveException(val version: Int) : Exception("Saved by a newer version of the game (save format $version).")

/**
 * Step-by-step upgrades of old saves. They work on raw JSON, before the save is decoded, so a
 * field can be renamed or reshaped without the current data classes having to understand the
 * old layout. Pure: no files, so each step can be unit tested with a JSON string.
 */
object SaveMigrations {
    private val steps: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to ::v1ToV2)

    fun versionOf(root: JsonObject): Int = root["version"]?.jsonPrimitive?.intOrNull ?: 1

    fun migrate(input: JsonObject): JsonObject {
        var root = input
        var version = versionOf(root)
        if (version > SAVE_VERSION) throw NewerSaveException(version)
        while (version < SAVE_VERSION) {
            val step = steps[version] ?: throw IllegalStateException("No upgrade path from save format $version.")
            root = step(root)
            version++
            root = JsonObject(root + ("version" to JsonPrimitive(version)))
        }
        return root
    }

    private fun renameKeys(obj: JsonObject?, names: Map<String, String>): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        obj?.forEach { (k, v) -> out[names[k] ?: k] = v }
        return JsonObject(out)
    }

    /**
     * Version 2 adds attributes, levels, nine equipment slots, appearance, discoveries and a
     * stored random seed. An old character gets average attributes and the same four free
     * points a new one starts with; their sword and shield move to the new hand slots.
     */
    private fun v1ToV2(root: JsonObject): JsonObject {
        val player = root["player"] as? JsonObject ?: JsonObject(emptyMap())
        val newPlayer = LinkedHashMap<String, JsonElement>(player)
        newPlayer["equipped"] = renameKeys(player["equipped"] as? JsonObject, mapOf("weapon" to "main_hand", "shield" to "off_hand"))
        newPlayer["skills"] = renameKeys(player["skills"] as? JsonObject, mapOf("blade" to "blades", "block" to "blocking"))
        newPlayer["attributes"] = JsonObject(Progression.ATTRIBUTES.associateWith { JsonPrimitive(Progression.BASE_ATTRIBUTE) })
        newPlayer["attributePoints"] = JsonPrimitive(Progression.STARTING_POINTS)
        newPlayer["level"] = JsonPrimitive(1)
        val name = player["name"]?.jsonPrimitive?.content.orEmpty()
        val uid = root["nextUid"]?.jsonPrimitive?.longOrNull ?: 1L
        val seed = name.hashCode().toLong() * 2654435761L + uid * 40503L + 0x5EEDL
        val out = LinkedHashMap<String, JsonElement>(root)
        out["player"] = JsonObject(newPlayer)
        out["flags"] = JsonArray(listOf(JsonPrimitive(FLAG_MIGRATED_V1)))
        out["seed"] = JsonPrimitive(seed)
        out["rngState"] = JsonPrimitive(seed)
        return JsonObject(out)
    }

    /** Tells the engine to fill in discoveries quietly for a world that predates them. */
    const val FLAG_MIGRATED_V1 = "sys:migrated_v1"
}

/** Versioned, crash-safe persistence for the living world and for player preferences. */
object SaveStore {
    private const val FILE = "world.json"
    private const val BACKUPS = 3
    private const val PREFS = "greenhollow"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(save: SaveGame): String = json.encodeToString(SaveGame.serializer(), save)

    fun decodeDetailed(text: String): Decoded {
        val root = json.parseToJsonElement(text).jsonObject
        val from = SaveMigrations.versionOf(root)
        val save = json.decodeFromJsonElement(SaveGame.serializer(), SaveMigrations.migrate(root))
        return Decoded(save, from)
    }

    fun decode(text: String): SaveGame = decodeDetailed(text).save

    private fun backup(context: Context, i: Int) = File(context.filesDir, "world.bak$i.json")

    private fun countBackups(context: Context) = (1..BACKUPS).count { backup(context, it).exists() }

    /**
     * Loads the newest save that can be read. An unreadable file is set aside as "*.corrupt"
     * (never deleted) and the next older copy is tried.
     */
    fun load(context: Context): LoadResult {
        val candidates = listOf(File(context.filesDir, FILE)) + (1..BACKUPS).map { backup(context, it) }
        var failed = 0
        var newer = false
        for ((index, file) in candidates.withIndex()) {
            if (!file.exists()) continue
            try {
                val decoded = decodeDetailed(file.readText())
                val recovery = if (failed == 0) "" else
                    "The newest save could not be read, so an earlier copy from ${Diagnostics.time(file.lastModified())} was loaded. The damaged file was kept."
                val migration = if (decoded.fromVersion == SAVE_VERSION) "" else
                    "This world was upgraded from save format ${decoded.fromVersion} to $SAVE_VERSION."
                if (recovery.isNotEmpty()) Diagnostics.record("save", "Recovered from backup ${if (index == 0) "" else index}. $recovery")
                if (migration.isNotEmpty()) Diagnostics.record("save", migration)
                Diagnostics.updateSave { it.copy(backups = countBackups(context), recovery = recovery, migration = migration) }
                return LoadResult(decoded.save, recovery)
            } catch (e: NewerSaveException) {
                newer = true
                Diagnostics.record("save", "${file.name} was written by a newer version of the game and was left untouched.", e)
            } catch (e: Exception) {
                failed++
                Diagnostics.record("save", "${file.name} could not be read and was set aside.", e)
                file.renameTo(File(context.filesDir, file.name.removeSuffix(".json") + ".corrupt.json"))
            }
        }
        val notice = when {
            newer -> "Your world was saved by a newer version of the game and cannot be opened by this one."
            failed > 0 -> "Your saved world and its backups could not be read. The files were kept, and the details are in Diagnostics."
            else -> ""
        }
        Diagnostics.updateSave { it.copy(backups = countBackups(context), recovery = notice) }
        return LoadResult(null, notice)
    }

    /**
     * Writes the world so that a crash at any moment leaves a complete save behind: the text
     * goes to a temporary file and is flushed to storage, the previous saves shift down one
     * backup slot, and only then does the new file take the live name in a single rename.
     */
    @Synchronized
    fun write(context: Context, text: String) {
        val dir = context.filesDir
        val tmp = File(dir, "$FILE.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        val target = File(dir, FILE)
        if (target.exists()) {
            for (i in BACKUPS - 1 downTo 1) {
                val from = backup(context, i)
                if (from.exists()) from.renameTo(backup(context, i + 1))
            }
            target.renameTo(backup(context, 1))
        }
        if (!tmp.renameTo(target)) {
            // Put the previous save back rather than leave the world without a live file.
            val previous = backup(context, 1)
            if (!target.exists() && previous.exists()) previous.renameTo(target)
            tmp.delete()
            throw IOException("The new save could not be moved into place.")
        }
        Diagnostics.updateSave { it.copy(lastSaved = System.currentTimeMillis(), backups = countBackups(context), lastError = "") }
    }

    /** Starting a new life clears the old world and its backups. Corrupt files set aside are left alone. */
    fun delete(context: Context) {
        File(context.filesDir, FILE).delete()
        (1..BACKUPS).forEach { backup(context, it).delete() }
        Diagnostics.updateSave { it.copy(backups = 0, recovery = "", migration = "") }
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
