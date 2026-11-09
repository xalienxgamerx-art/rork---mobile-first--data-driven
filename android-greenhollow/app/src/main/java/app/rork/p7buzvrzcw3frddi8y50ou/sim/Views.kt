package app.rork.p7buzvrzcw3frddi8y50ou.sim

import app.rork.p7buzvrzcw3frddi8y50ou.data.NpcDef

/** Immutable snapshots the simulation publishes for the UI. The UI never touches live state. */
data class Hud(
    val health: Float = 100f,
    val maxHealth: Float = 100f,
    val stamina: Float = 100f,
    val maxStamina: Float = 100f,
    val magic: Float = 100f,
    val maxMagic: Float = 100f,
    val timeLabel: String = "",
    val clock: String = "",
    val location: String = "Greenhollow",
    val prompt: String? = null,
    val promptKind: String = "",
    val thirdPerson: Boolean = false,
    val fps: Int = 0,
    val swimming: Boolean = false,
    val weather: String = "clear",
    val targetName: String? = null,
    val targetHealth: Float = 0f,
)

data class WareView(val itemId: String, val name: String, val icon: String, val count: Int, val price: Int)

data class NpcView(
    val id: String,
    val def: NpcDef,
    val profession: String,
    val race: String,
    val activity: String,
    val affinity: Int,
    val relation: String,
    val hunger: Float,
    val fatigue: Float,
    val social: Float,
    val gold: Int,
    val memories: List<String>,
    val bonds: List<Pair<String, Int>>,
    val home: String,
    val work: String,
    val trader: Boolean,
    val wares: List<WareView>,
    val skin: String,
    val cloth: String,
    val hat: Boolean,
)

data class ItemView(
    val uid: String,
    val defId: String,
    val name: String,
    val type: String,
    val slot: String,
    val icon: String,
    val count: Int,
    val condition: Float,
    val weight: Float,
    val value: Int,
    val description: String,
    val equipped: Boolean,
    val damage: Float,
    val armor: Float,
    val forged: ForgedWeapon?,
    val usable: Boolean,
)

data class PackView(
    val items: List<ItemView> = emptyList(),
    val gold: Int = 0,
    val burden: Float = 0f,
    val capacity: Float = 60f,
    val smithing: Float = 0f,
)

data class JournalView(
    val npcs: List<NpcView> = emptyList(),
    val hunger: Float = 70f,
    val fatigue: Float = 80f,
    val comfort: Float = 75f,
    val dateLine: String = "",
    val season: String = "",
    val history: List<String> = emptyList(),
    val skills: List<Pair<String, Float>> = emptyList(),
    val playerName: String = "",
)

sealed interface UiEvent {
    data class Toast(val text: String) : UiEvent
    data class Dialogue(val npcId: String, val line: String) : UiEvent
    data object OpenForge : UiEvent
    data object Hit : UiEvent
    data object Hurt : UiEvent
}

sealed interface Command {
    data object Interact : Command
    data object Attack : Command
    data object Jump : Command
    data object ToggleCamera : Command
    data object CastHeal : Command
    data class UseItem(val uid: String) : Command
    data class ToggleEquip(val uid: String) : Command
    data class Drop(val uid: String) : Command
    data class Chat(val npcId: String) : Command
    data class Gift(val npcId: String, val uid: String) : Command
    data class Buy(val npcId: String, val itemId: String) : Command
    data class Sell(val npcId: String, val uid: String) : Command
    data class Wait(val hours: Int) : Command
    data class AddForged(val weapon: ForgedWeapon, val materialItem: String, val ingots: Int) : Command
}

class InputState {
    @Volatile var moveX = 0f
    @Volatile var moveY = 0f
    @Volatile var block = false
    @Volatile var sensitivity = 1f
    private var lookX = 0f
    private var lookY = 0f

    @Synchronized
    fun look(dx: Float, dy: Float) {
        lookX += dx
        lookY += dy
    }

    @Synchronized
    fun consumeLook(out: FloatArray) {
        out[0] = lookX
        out[1] = lookY
        lookX = 0f
        lookY = 0f
    }

    fun reset() {
        moveX = 0f
        moveY = 0f
        block = false
    }
}

/** Pooled per-frame buffer the renderer reads. Filled under the engine lock, drawn outside it. */
class RenderFrame {
    companion object {
        const val MAX_ACTORS = 96
        const val MAX_NODES = 48
    }

    var camX = 0f
    var camY = 2f
    var camZ = 0f
    var yaw = 0f
    var pitch = 0f
    var hour = 16f
    var mist = 0f
    var swing = 0f
    var blocking = false
    var firstPersonWeapon = false
    var bladeLength = 0.8f
    val bladeColor = floatArrayOf(0.7f, 0.7f, 0.72f)

    var actorCount = 0
    val ax = FloatArray(MAX_ACTORS)
    val ay = FloatArray(MAX_ACTORS)
    val az = FloatArray(MAX_ACTORS)
    val ayaw = FloatArray(MAX_ACTORS)
    val aphase = FloatArray(MAX_ACTORS)
    val ascale = FloatArray(MAX_ACTORS)
    /** 0 humanoid, 1 quadruped */
    val akind = IntArray(MAX_ACTORS)
    /** bit0 hat, bit1 beard, bit2 long hair, bit3 armed */
    val aflags = IntArray(MAX_ACTORS)
    /** three rgb triples per actor: skin, cloth, hair */
    val acol = FloatArray(MAX_ACTORS * 9)

    var nodeCount = 0
    val nx = FloatArray(MAX_NODES)
    val ny = FloatArray(MAX_NODES)
    val nz = FloatArray(MAX_NODES)
    val nkind = IntArray(MAX_NODES)
}
