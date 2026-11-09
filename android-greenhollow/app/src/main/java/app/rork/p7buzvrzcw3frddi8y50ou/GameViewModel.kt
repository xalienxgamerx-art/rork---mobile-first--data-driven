package app.rork.p7buzvrzcw3frddi8y50ou

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rork.p7buzvrzcw3frddi8y50ou.data.ContentLoader
import app.rork.p7buzvrzcw3frddi8y50ou.data.ContentRegistry
import app.rork.p7buzvrzcw3frddi8y50ou.data.MaterialDef
import app.rork.p7buzvrzcw3frddi8y50ou.sim.BladeGeometry
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ForgePhysics
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ForgedWeapon
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ProcessQuality
import app.rork.p7buzvrzcw3frddi8y50ou.sim.SaveStore
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Settings
import app.rork.p7buzvrzcw3frddi8y50ou.sim.WeaponStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

sealed interface Phase {
    data object Loading : Phase
    data class Creation(val content: ContentRegistry) : Phase
    data class Playing(val engine: GameEngine) : Phase
}

/** The smith's bench: design, heat, hammer, quench, temper. Lives outside the world simulation. */
class ForgeSession {
    var materialId by mutableStateOf("")
    var geometry by mutableStateOf(BladeGeometry())
    var temperature by mutableFloatStateOf(20f)
    var progress by mutableFloatStateOf(0f)
    var blows by mutableIntStateOf(0)
    var blowQuality by mutableFloatStateOf(0f)
    var defects by mutableFloatStateOf(0f)
    var hardening by mutableFloatStateOf(0f)
    var temper by mutableFloatStateOf(0.5f)
    var quenched by mutableStateOf(false)
    var name by mutableStateOf("")
    var note by mutableStateOf("Set the blade's shape, then bring the steel to heat.")
    var strike by mutableIntStateOf(0)

    val locked get() = blows > 0 || quenched

    fun reset() {
        temperature = 20f; progress = 0f; blows = 0; blowQuality = 0f; defects = 0f
        hardening = 0f; temper = 0.5f; quenched = false; name = ""
        note = "Set the blade's shape, then bring the steel to heat."
    }

    fun quality(): ProcessQuality = ProcessQuality(
        forging = if (blows > 0) (blowQuality / blows).coerceIn(0f, 1f) else 0.7f,
        hardening = if (quenched) hardening else 0.8f,
        temper = temper,
        defects = defects.coerceIn(0f, 1f),
    )

    fun stats(m: MaterialDef): WeaponStats = ForgePhysics.compute(geometry, m, quality())

    fun cool(seconds: Float) {
        if (temperature > 20f) temperature = (temperature - (temperature - 20f) * 0.11f * seconds).coerceAtLeast(20f)
    }

    fun heat(m: MaterialDef) {
        if (quenched) {
            note = "The blade is hardened. Reheating it now would undo the quench."
            return
        }
        temperature = (temperature + 150f).coerceAtMost(1500f)
        note = when {
            temperature > m.forgeTemp + 200f -> {
                defects += 0.07f
                "Too hot. The metal sparks and burns at the edges."
            }
            temperature > m.forgeTemp + 90f -> "Bright yellow. Any hotter and it will burn."
            temperature > m.forgeTemp - 90f -> "A good working heat for ${m.name.lowercase()}."
            temperature > 500f -> "Dull red. It needs more fire."
            else -> "The bellows roar. The bar is warming."
        }
    }

    /** Returns false when the blow could not be struck. */
    fun hammer(m: MaterialDef, smithing: Float): Boolean {
        if (quenched) {
            note = "Hammering a hardened blade will only crack it."
            return false
        }
        if (progress >= 1f) {
            note = "The shape is true. Bring it to heat and quench it."
            return false
        }
        if (temperature < 450f) {
            note = "The metal is too cold to move. Heat it first."
            return false
        }
        val off = abs(temperature - m.forgeTemp)
        val q = (1f - off / 260f).coerceIn(0f, 1f)
        if (temperature < m.forgeTemp - 300f) defects += 0.05f
        blows++
        strike++
        blowQuality += (q * (0.82f + smithing / 100f * 0.18f)).coerceIn(0f, 1f)
        progress = (progress + 0.06f + 0.06f * m.workability / 100f + smithing * 0.0006f).coerceAtMost(1f)
        temperature = (temperature - 28f).coerceAtLeast(20f)
        note = when {
            progress >= 1f -> "The blade has its shape. Heat it once more, then quench."
            q > 0.75f -> "A clean blow. The steel moves like clay."
            q > 0.4f -> "The blow lands, but the heat is off."
            else -> "A poor blow. The metal resists and the grain suffers."
        }
        return true
    }

    fun quench(m: MaterialDef): Boolean {
        if (quenched) return false
        if (progress < 1f) {
            note = "The blade is not finished. Keep hammering."
            return false
        }
        val target = m.forgeTemp - 180f
        hardening = (1f - abs(temperature - target) / 240f).coerceIn(0.12f, 1f)
        if (temperature > m.forgeTemp + 110f) defects += 0.15f
        quenched = true
        note = when {
            temperature < 420f -> "It hissed weakly. The blade was too cold to harden."
            hardening > 0.8f -> "A sharp hiss and a clear ring. The blade is glass-hard."
            hardening > 0.5f -> "Steam rolls up. The blade has hardened, though unevenly."
            else -> "The quench went poorly. The edge will be soft."
        }
        temperature = 60f
        return true
    }

    fun finish(m: MaterialDef): ForgedWeapon {
        val q = quality()
        val label = name.trim().ifEmpty { "${m.name} ${ForgePhysics.className(geometry)}" }
        return ForgedWeapon(label, m.id, geometry, ForgePhysics.compute(geometry, m, q), ForgePhysics.qualityName(q))
    }
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    val phase = MutableStateFlow<Phase>(Phase.Loading)
    val settings = MutableStateFlow(SaveStore.loadSettings(app))
    val content = MutableStateFlow<ContentRegistry?>(null)
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val forge = ForgeSession()

    @Volatile private var paused = false
    @Volatile private var worldVisible = false

    private val engine: GameEngine? get() = (phase.value as? Phase.Playing)?.engine

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val registry = ContentLoader.load(getApplication(), settings.value.disabledPacks)
            content.value = registry
            val save = SaveStore.load(getApplication())
            phase.value = if (save != null) Phase.Playing(makeEngine(registry, save)) else Phase.Creation(registry)
        }
        // Keeps the vale alive while the player is in the journal, the pack or at the forge.
        viewModelScope.launch(Dispatchers.Default) {
            var sinceSave = 0f
            while (isActive) {
                delay(200)
                val e = engine ?: continue
                if (paused) continue
                if (System.nanoTime() - e.lastUpdateNanos > 350_000_000L) e.update(0.2f)
                sinceSave += 0.2f
                if (sinceSave >= 60f) {
                    sinceSave = 0f
                    persist(e)
                }
            }
        }
    }

    private fun makeEngine(registry: ContentRegistry, save: app.rork.p7buzvrzcw3frddi8y50ou.sim.SaveGame): GameEngine {
        val e = GameEngine(registry, save)
        e.input.sensitivity = settings.value.sensitivity
        if (forge.materialId !in registry.materials) forge.materialId = registry.materials.keys.firstOrNull().orEmpty()
        return e
    }

    private fun persist(e: GameEngine) {
        try {
            SaveStore.write(getApplication(), e.withState { SaveStore.encode(it) })
        } catch (_: Exception) {
        }
    }

    fun send(c: Command) {
        engine?.submit(c)
    }

    fun setPaused(value: Boolean) {
        paused = value
        val e = engine ?: return
        if (value) {
            e.input.reset()
            viewModelScope.launch(Dispatchers.IO) { persist(e) }
        }
    }

    fun saveNow() {
        val e = engine ?: return
        viewModelScope.launch(Dispatchers.IO) {
            persist(e)
            notices.tryEmit("The world is written down.")
        }
    }

    fun startNewGame(name: String, raceId: String, backgroundId: String) {
        val registry = content.value ?: return
        phase.value = Phase.Loading
        viewModelScope.launch(Dispatchers.Default) {
            val save = GameEngine.newGame(registry, name, raceId, backgroundId)
            val e = makeEngine(registry, save)
            forge.reset()
            persist(e)
            phase.value = Phase.Playing(e)
        }
    }

    fun newLife() {
        val registry = content.value ?: return
        SaveStore.delete(getApplication())
        forge.reset()
        phase.value = Phase.Creation(registry)
    }

    fun updateSettings(s: Settings) {
        settings.value = s
        engine?.input?.sensitivity = s.sensitivity
        SaveStore.saveSettings(getApplication(), s)
    }

    fun setPackEnabled(id: String, enabled: Boolean) {
        val s = settings.value
        val disabled = if (enabled) s.disabledPacks - id else s.disabledPacks + id
        updateSettings(s.copy(disabledPacks = disabled))
        reloadContent()
    }

    fun importPack(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ContentLoader.importPack(getApplication(), uri)
            result.onSuccess {
                notices.tryEmit("Installed ${it.name} ${it.version}.")
                withContext(Dispatchers.Main) { reloadContent() }
            }.onFailure {
                notices.tryEmit("That file is not a valid content pack: ${it.message ?: "unreadable"}")
            }
        }
    }

    fun removePack(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ContentLoader.removePack(getApplication(), id)
            withContext(Dispatchers.Main) { reloadContent() }
        }
    }

    /** Rebuilds the registry and re-binds the running world to it. Orphaned state stays in the save. */
    private fun reloadContent() {
        val current = phase.value
        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = (current as? Phase.Playing)?.engine?.withState { SaveStore.encode(it) }
            if (current is Phase.Playing) phase.value = Phase.Loading
            val registry = ContentLoader.load(getApplication(), settings.value.disabledPacks)
            content.value = registry
            phase.value = when {
                snapshot != null -> Phase.Playing(makeEngine(registry, SaveStore.decode(snapshot)))
                else -> Phase.Creation(registry)
            }
        }
    }

    override fun onCleared() {
        engine?.let { persist(it) }
    }
}
