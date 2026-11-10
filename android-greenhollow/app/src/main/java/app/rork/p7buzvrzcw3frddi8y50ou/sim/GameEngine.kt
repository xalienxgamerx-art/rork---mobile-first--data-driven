package app.rork.p7buzvrzcw3frddi8y50ou.sim

import app.rork.p7buzvrzcw3frddi8y50ou.data.ContentRegistry
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
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.SnapshotSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.TimeSystem
import app.rork.p7buzvrzcw3frddi8y50ou.sim.systems.TradeSystem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

/**
 * The simulation's coordinator. It owns the lock, the command queue and the published
 * snapshots, and runs the systems in one fixed order every step. The rules themselves live in
 * the systems under sim/systems; the UI and renderer send [Command]s and read snapshots.
 *
 * Order of one step: commands, player, clock, needs, villagers, creatures, the once-a-second
 * tick (restock, weather, regrowth, discovery), target finding, snapshots.
 */
class GameEngine(val content: ContentRegistry, val save: SaveGame) {
    val lock = Any()
    val input = InputState()
    val hud = MutableStateFlow(Hud())
    val heading = MutableStateFlow(0f)
    val pack = MutableStateFlow(PackView())
    val journal = MutableStateFlow(JournalView())
    val dialogue = MutableStateFlow<DialogueView?>(null)
    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)

    @Volatile var lastUpdateNanos = 0L

    private val commands = ConcurrentLinkedQueue<Command>()
    private val w: World
    private val snapshot: SnapshotSystem
    private var slowTimer = 0f
    private var targetTimer = 0f

    var fps: Int
        get() = w.fps
        set(value) {
            w.fps = value
        }

    val maxHealth: Float get() = w.progression.maxHealth
    val maxStamina: Float get() = w.progression.maxStamina
    val maxMagic: Float get() = w.progression.maxMagic

    init {
        SaveBinding.bind(save, content)
        w = World(content, save, input, events)
        w.progression = ProgressionSystem(w)
        w.inventory = InventorySystem(w)
        w.player = PlayerSystem(w)
        w.needs = NeedsSystem(w)
        w.combat = CombatSystem(w)
        w.dialogue = DialogueSystem(w, dialogue)
        w.trade = TradeSystem(w)
        w.interaction = InteractionSystem(w)
        w.crafting = CraftingSystem(w)
        w.npcSim = NpcSystem(w)
        w.creatureSim = CreatureSystem(w)
        w.time = TimeSystem(w)
        w.discovery = DiscoverySystem(w)
        snapshot = SnapshotSystem(w, hud, heading, pack, journal)

        w.progression.recalc()
        w.npcSim.build()
        w.creatureSim.build()
        w.discovery.bind()
        val p = save.player
        p.y = max(Terrain.height(p.x, p.z), p.y)
        snapshot.publish(0f, force = true)
    }

    fun submit(c: Command) {
        commands += c
    }

    /** Runs [block] on the world under the lock, e.g. to take a serialisable copy. */
    fun <T> withState(block: (SaveGame) -> T): T = synchronized(lock) { block(save) }

    fun update(dtIn: Float) = synchronized(lock) {
        lastUpdateNanos = System.nanoTime()
        val dt = dtIn.coerceIn(0f, 0.25f)
        while (true) {
            val c = commands.poll() ?: break
            handle(c)
        }
        w.player.update(dt)
        w.time.advance(dt)
        w.needs.update(dt * MIN_PER_SEC / 60f, sleeping = false)
        w.npcSim.update(dt)
        w.creatureSim.update(dt)
        slowTimer += dt
        if (slowTimer > 1f) {
            slowTimer = 0f
            w.time.slowTick()
        }
        targetTimer += dt
        if (targetTimer > 0.15f) {
            targetTimer = 0f
            w.interaction.findTarget()
        }
        snapshot.publish(dt, force = false)
    }

    fun fillFrame(f: RenderFrame) = synchronized(lock) { snapshot.fillFrame(f) }

    private fun handle(c: Command) {
        when (c) {
            Command.Interact -> w.interaction.interact()
            Command.Attack -> w.combat.attack()
            Command.Jump -> w.player.jump()
            Command.ToggleCamera -> save.player.thirdPerson = !save.player.thirdPerson
            Command.CastHeal -> w.combat.castHeal()
            is Command.UseItem -> w.inventory.useItem(c.uid)
            is Command.ToggleEquip -> w.inventory.toggleEquip(c.uid)
            is Command.Drop -> w.inventory.drop(c.uid)
            is Command.Chat -> w.dialogue.chat(c.npcId)
            is Command.Gift -> w.dialogue.gift(c.npcId, c.uid)
            is Command.Buy -> w.trade.buy(c.npcId, c.itemId)
            is Command.Sell -> w.trade.sell(c.npcId, c.uid)
            is Command.Wait -> w.time.passHours(c.hours)
            is Command.Reply -> w.dialogue.reply(c.index)
            Command.EndDialogue -> w.dialogue.end()
            is Command.SpendPoint -> w.progression.spend(c.attribute)
            is Command.AddForged -> w.crafting.addForged(c)
        }
    }

    companion object {
        fun addItemTo(save: SaveGame, content: ContentRegistry, defId: String, count: Int, forged: ForgedWeapon? = null) =
            InventorySystem.add(save, content, defId, count, forged)

        /**
         * Starts a new life. [points] is how the player spread their starting attribute points;
         * anything unspent (or beyond the allowance) is kept to spend later.
         */
        fun newGame(
            content: ContentRegistry,
            name: String,
            raceId: String,
            backgroundId: String,
            appearance: Appearance = Appearance(),
            points: Map<String, Int> = emptyMap(),
        ): SaveGame {
            val seed = System.nanoTime()
            val save = SaveGame(
                minutes = Calendar.START_MINUTES,
                packs = content.packs.filter { it.loaded }.map { it.manifest.id },
                seed = seed,
                rngState = seed,
            )
            val p = save.player
            p.name = name.ifBlank { "Wanderer" }
            p.race = content.races.canonical(raceId)
            p.background = content.backgrounds.canonical(backgroundId)
            val race = content.races[p.race]

            var left = Progression.STARTING_POINTS
            for (a in Progression.ATTRIBUTES) {
                val spent = (points[a] ?: 0).coerceIn(0, left)
                left -= spent
                p.attributes[a] = (Progression.BASE_ATTRIBUTE + (race?.attributes?.get(a) ?: 0) + spent).coerceIn(1, Progression.MAX_ATTRIBUTE)
            }
            p.attributePoints = left

            val look = p.appearance
            look.skin = appearance.skin.ifBlank { race?.skinTones?.firstOrNull() ?: "#E0B08A" }
            look.hair = appearance.hair
            look.hairStyle = appearance.hairStyle
            look.beard = appearance.beard
            look.build = appearance.build.coerceIn(0.9f, 1.1f)
            look.cloth = appearance.cloth

            val bg = content.backgrounds[p.background]
            bg?.items?.forEach { InventorySystem.add(save, content, it.item, it.count) }
            p.gold = bg?.gold ?: 20
            bg?.skills?.forEach { (k, v) -> p.skills[SaveBinding.skillId(k)] = v }
            bg?.equip?.forEach { raw ->
                val id = content.items.canonical(raw)
                val def = content.items[id] ?: return@forEach
                val item = p.inventory.firstOrNull { it.defId == id } ?: return@forEach
                val slot = when (def.slot) {
                    "" -> return@forEach
                    "trinket" -> if ("trinket_1" !in p.equipped) "trinket_1" else "trinket_2"
                    else -> def.slot
                }
                if (slot !in p.equipped) p.equipped[slot] = item.uid
            }
            p.health = 88f
            p.stamina = 60f
            p.magic = 75f
            p.y = Terrain.height(p.x, p.z)
            save.history += WorldEvent(save.minutes, "${p.name} came down the road to Greenhollow.")
            return save
        }
    }
}
