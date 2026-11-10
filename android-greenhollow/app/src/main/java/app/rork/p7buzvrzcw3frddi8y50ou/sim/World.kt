package app.rork.p7buzvrzcw3frddi8y50ou.sim

import app.rork.p7buzvrzcw3frddi8y50ou.data.BuildingDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ContentRegistry
import app.rork.p7buzvrzcw3frddi8y50ou.data.CreatureDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.NpcDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ProfessionDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.hexToRgb
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.CombatSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.CraftingSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.CreatureSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.DialogueSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.DiscoverySystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.InteractionSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.InventorySystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.NeedsSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.NpcSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.PlayerSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.ProgressionSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.TimeSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.TradeSystem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Game minutes that pass per real second: one in-game day lasts 48 real minutes. */
const val MIN_PER_SEC = 0.5f
const val TWO_PI = 6.2831855f

/** A villager in the running world: definition, saved state, and what is only needed while playing. */
class Npc(val def: NpcDef, val prof: ProfessionDef, val s: NpcState) {
    var yaw = 0f
    var phase = 0f
    var moving = false
    val path = ArrayDeque<FloatArray>()
    var decideAt = 0.0
    var nextSocialAt = 0.0
    var accum = 0f
    var pause = 0f
    var wageAcc = 0f
    var destName = ""
    var destLabel = ""
    var homeDoor = floatArrayOf(0f, 0f)
    var homeName = ""
    var workPos: FloatArray? = null
    var workGate = ""
    var workName = ""
    var skinHex = "#D9A77F"
    var clothHex = "#7A6548"
    var hat = false
    var skin = floatArrayOf(0.85f, 0.65f, 0.5f)
    var cloth = floatArrayOf(0.5f, 0.4f, 0.3f)
    var hair = floatArrayOf(0.3f, 0.2f, 0.1f)
    fun trait(name: String) = def.personality[name] ?: 0.5f
}

class Creature(val def: CreatureDef, val s: CreatureState) {
    var yaw = 0f
    var phase = 0f
    var tx = 0f
    var tz = 0f
    var cooldown = 0f
    var retarget = 0f
    val color = hexToRgb(def.color)
}

class Box(val cx: Float, val cz: Float, val hw: Float, val hd: Float, val cos: Float, val sin: Float)

/** What the Use button would act on. kind is npc, resource, building or object. */
class Target(val kind: String, val id: String, val label: String)

/**
 * Everything the systems share: the content, the save, the world's random sequence, the live
 * villagers and creatures, and the few values that exist only while the game runs. Systems hold
 * no world state of their own beyond timers, so the save plus this context is the whole world.
 */
class World(
    val content: ContentRegistry,
    val save: SaveGame,
    val input: InputState,
    val events: MutableSharedFlow<UiEvent>,
) {
    val rng = WorldRng(save)
    val p: PlayerState get() = save.player

    val npcs = ArrayList<Npc>()
    val npcById = HashMap<String, Npc>()
    val creatures = ArrayList<Creature>()
    val boxes = ArrayList<Box>()
    val inn: BuildingDef? = content.buildings.values.firstOrNull { it.type == "inn" }

    // Runtime only. None of this is saved.
    var vy = 0f
    var grounded = true
    var swimming = false
    var walkPhase = 0f
    var attackCooldown = 0f
    var swing = 0f
    var target: Target? = null
    var hostile: Creature? = null
    var packDirty = true
    var journalDirty = true
    @Volatile var fps = 0

    lateinit var progression: ProgressionSystem
    lateinit var inventory: InventorySystem
    lateinit var player: PlayerSystem
    lateinit var needs: NeedsSystem
    lateinit var combat: CombatSystem
    lateinit var dialogue: DialogueSystem
    lateinit var trade: TradeSystem
    lateinit var interaction: InteractionSystem
    lateinit var crafting: CraftingSystem
    lateinit var npcSim: NpcSystem
    lateinit var creatureSim: CreatureSystem
    lateinit var time: TimeSystem
    lateinit var discovery: DiscoverySystem

    init {
        for (b in content.buildings.values) {
            val r = atan2(b.x, b.z)
            boxes += Box(b.x, b.z, b.w / 2f, b.d / 2f, cos(r), sin(r))
        }
    }

    fun toast(text: String) {
        events.tryEmit(UiEvent.Toast(text))
    }

    /** Adds a line to the village chronicle. */
    fun record(text: String) {
        save.history += WorldEvent(save.minutes, text)
        while (save.history.size > 80) save.history.removeAt(0)
        journalDirty = true
    }

    /** The point in front of a building's door. Doors face the village square. */
    fun doorOf(b: BuildingDef, side: Float = 0f): FloatArray {
        val len = sqrt(b.x * b.x + b.z * b.z).coerceAtLeast(0.01f)
        val nx = b.x / len
        val nz = b.z / len
        val dist = b.d / 2f + 1.8f
        return floatArrayOf(b.x - nx * dist - nz * side, b.z - nz * dist + nx * side)
    }

    fun groundY(x: Float, z: Float) = max(Terrain.height(x, z), Terrain.WATER - 0.55f)

    /** Pushes a position out of any building it overlaps. */
    fun collide(pos: FloatArray, radius: Float) {
        for (b in boxes) {
            val dx = pos[0] - b.cx
            val dz = pos[1] - b.cz
            if (abs(dx) > 14f || abs(dz) > 14f) continue
            var lx = dx * b.cos - dz * b.sin
            var lz = dx * b.sin + dz * b.cos
            val px = b.hw + radius - abs(lx)
            val pz = b.hd + radius - abs(lz)
            if (px > 0f && pz > 0f) {
                if (px < pz) {
                    lx += if (lx >= 0f) px else -px
                } else {
                    lz += if (lz >= 0f) pz else -pz
                }
                pos[0] = b.cx + lx * b.cos + lz * b.sin
                pos[1] = b.cz - lx * b.sin + lz * b.cos
            }
        }
    }

    /** Whoever keeps the inn, and so is paid for beds and suppers. */
    fun innkeeper(): Npc? = npcs.firstOrNull { it.prof.workplace == "inn" && it.prof.trader }
}

/**
 * Brings a loaded save in line with the content that is loaded now: ids written before packs
 * were namespaced are rewritten to their full form, and anything a newer game expects but an
 * older save or a hand-edited file lacks is filled in. Ids that match nothing are left alone,
 * so state from a pack that is switched off survives until the pack comes back.
 */
object SaveBinding {
    private val LEGACY_SLOTS = mapOf("weapon" to "main_hand", "shield" to "off_hand")
    private val LEGACY_SKILLS = mapOf("blade" to "blades", "block" to "blocking")

    fun skillId(id: String) = LEGACY_SKILLS[id] ?: id

    fun bind(save: SaveGame, content: ContentRegistry) {
        val p = save.player
        for (i in p.inventory.indices) {
            val it = p.inventory[i]
            val defId = content.items.canonical(it.defId)
            val forged = it.forged?.let { f ->
                val m = content.materials.canonical(f.materialId)
                if (m != f.materialId) f.copy(materialId = m) else f
            }
            if (defId != it.defId || forged !== it.forged) p.inventory[i] = it.copy(defId = defId, forged = forged)
        }
        for ((old, new) in LEGACY_SLOTS) p.equipped.remove(old)?.let { uid -> if (new !in p.equipped) p.equipped[new] = uid }
        for ((old, new) in LEGACY_SKILLS) p.skills.remove(old)?.let { v -> p.skills[new] = maxOf(v, p.skills[new] ?: 0f) }
        p.race = content.races.canonical(p.race)
        p.background = content.backgrounds.canonical(p.background)
        if (p.appearance.skin.isBlank()) {
            p.appearance.skin = (content.races[p.race] ?: content.races.values.firstOrNull())?.skinTones?.firstOrNull() ?: "#E0B08A"
        }
        if (p.level < 1) p.level = 1

        for (i in save.npcs.indices) {
            val n = save.npcs[i]
            val id = content.npcs.canonical(n.id)
            if (id != n.id) save.npcs[i] = n.copy(id = id)
            val state = save.npcs[i]
            if (state.bonds.keys.any { content.npcs.canonical(it) != it }) {
                val bonds = state.bonds.entries.associate { content.npcs.canonical(it.key) to it.value }
                state.bonds.clear()
                state.bonds.putAll(bonds)
            }
            for (j in state.stock.indices) {
                val e = state.stock[j]
                val item = content.items.canonical(e.item)
                if (item != e.item) state.stock[j] = e.copy(item = item)
            }
        }
        for (i in save.creatures.indices) {
            val c = save.creatures[i]
            val def = content.creatures.canonical(c.defId)
            if (def != c.defId) save.creatures[i] = c.copy(uid = "$def#${c.uid.substringAfterLast('#')}", defId = def)
        }
        for (key in save.harvested.keys.toList()) {
            val id = content.resources.canonical(key)
            if (id != key) save.harvested.remove(key)?.let { save.harvested[id] = it }
        }
        if (save.rngState == 0L) save.rngState = if (save.seed != 0L) save.seed else 0x5EED5EEDL + save.nextUid
    }
}
