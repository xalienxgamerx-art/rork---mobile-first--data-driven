package app.rork.p7buzvrzcw3frddi8y50ou.sim

import app.rork.p7buzvrzcw3frddi8y50ou.data.BuildingDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ContentRegistry
import app.rork.p7buzvrzcw3frddi8y50ou.data.CreatureDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.NpcDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ProfessionDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ResourceDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.hexToRgb
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Game minutes that pass per real second: one in-game day lasts 48 real minutes. */
const val MIN_PER_SEC = 0.5f

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

private class Box(val cx: Float, val cz: Float, val hw: Float, val hd: Float, val cos: Float, val sin: Float)

private class Target(val kind: String, val id: String, val label: String)

/**
 * The simulation. Owns all mutable world state and is the only thing allowed to change it.
 * The UI and renderer send [Command]s and read published snapshots; everything else happens
 * inside [update], which runs under [lock] on whichever thread is currently driving the game.
 */
class GameEngine(val content: ContentRegistry, val save: SaveGame) {
    val lock = Any()
    val input = InputState()
    val hud = MutableStateFlow(Hud())
    val heading = MutableStateFlow(0f)
    val pack = MutableStateFlow(PackView())
    val journal = MutableStateFlow(JournalView())
    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)

    @Volatile var lastUpdateNanos = 0L
    @Volatile var fps = 0

    private val commands = ConcurrentLinkedQueue<Command>()
    private val rnd = Random(System.nanoTime())
    private val p get() = save.player
    private val look = FloatArray(2)

    private val npcs = ArrayList<Npc>()
    private val npcById = HashMap<String, Npc>()
    private val creatures = ArrayList<Creature>()
    private val boxes = ArrayList<Box>()
    private val resources: List<ResourceDef> = content.resources.values.toList()
    private val interactBuildings: List<BuildingDef> = content.buildings.values.filter { it.interact.isNotEmpty() }
    private val inn: BuildingDef? = content.buildings.values.firstOrNull { it.type == "inn" }

    private var vy = 0f
    private var grounded = true
    private var swimming = false
    private var walkPhase = 0f
    private var attackCooldown = 0f
    private var swing = 0f
    private var target: Target? = null
    private var hostile: Creature? = null
    private var targetTimer = 0f
    private var hudTimer = 0f
    private var slowTimer = 0f
    private var packDirty = true
    private var lastWeatherSlot = -1
    private var playerSkin = floatArrayOf(0.85f, 0.65f, 0.5f)

    val maxHealth: Float
    val maxStamina: Float
    val maxMagic: Float

    init {
        val race = content.races[p.race] ?: content.races.values.firstOrNull()
        maxHealth = 100f + (race?.healthBonus ?: 0f)
        maxStamina = 100f + (race?.staminaBonus ?: 0f)
        maxMagic = 100f + (race?.magicBonus ?: 0f)
        race?.skinTones?.firstOrNull()?.let { playerSkin = hexToRgb(it) }
        p.health = p.health.coerceAtMost(maxHealth)
        p.stamina = p.stamina.coerceAtMost(maxStamina)
        p.magic = p.magic.coerceAtMost(maxMagic)

        for (b in content.buildings.values) {
            val r = atan2(b.x, b.z)
            boxes += Box(b.x, b.z, b.w / 2f, b.d / 2f, cos(r), sin(r))
        }
        buildNpcs()
        buildCreatures()
        p.y = max(Terrain.height(p.x, p.z), p.y)
        publish(force = true)
    }

    // ---------------------------------------------------------------- setup

    private fun doorOf(b: BuildingDef, side: Float = 0f): FloatArray {
        val len = sqrt(b.x * b.x + b.z * b.z).coerceAtLeast(0.01f)
        val nx = b.x / len
        val nz = b.z / len
        val dist = b.d / 2f + 1.8f
        return floatArrayOf(b.x - nx * dist - nz * side, b.z - nz * dist + nx * side)
    }

    private fun buildNpcs() {
        for (def in content.npcs.values) {
            val prof = content.professions[def.profession] ?: continue
            val home = content.buildings[def.home] ?: continue
            val hashSide = ((def.id.hashCode() and 0xFF) / 255f - 0.5f) * 4f
            val door = doorOf(home, hashSide)
            val state: NpcState = save.npcs.firstOrNull { it.id == def.id } ?: run {
                val created = NpcState(
                    id = def.id, x = door[0], z = door[1],
                    hunger = 55f + rnd.nextFloat() * 35f,
                    fatigue = 55f + rnd.nextFloat() * 35f,
                    social = 40f + rnd.nextFloat() * 40f,
                    gold = def.gold, affinity = def.affinity,
                )
                for (g in prof.goods) created.stock.add(StockEntry(g.item, g.count))
                save.npcs.add(created)
                created
            }
            val n = Npc(def, prof, state)
            n.homeDoor = door
            n.homeName = home.name
            val ref = def.workplace.ifEmpty { prof.workplace }
            val spot = content.spots[ref]
            val building = if (spot == null && ref.isNotEmpty()) content.buildingFor(ref) else null
            if (spot != null) {
                n.workPos = floatArrayOf(spot.x + hashSide * 1.5f, spot.z + ((def.id.hashCode() shr 8 and 0xFF) / 255f - 0.5f) * 8f)
                n.workGate = spot.gate
                n.workName = spot.name
            } else if (building != null) {
                n.workPos = doorOf(building, hashSide * 0.7f)
                n.workName = building.name
            }
            val race = content.races[def.race]
            n.skinHex = def.skin.ifEmpty { race?.skinTones?.firstOrNull() ?: "#D9A77F" }
            n.clothHex = def.cloth.ifEmpty { prof.cloth }
            n.hat = def.hat ?: prof.hat
            n.skin = hexToRgb(n.skinHex)
            n.cloth = hexToRgb(n.clothHex)
            n.hair = hexToRgb(def.hair)
            n.decideAt = save.minutes + rnd.nextDouble() * 4.0
            npcs += n
            npcById[def.id] = n
        }
    }

    private fun buildCreatures() {
        for (def in content.creatures.values) {
            val loc = content.locations[def.territory] ?: continue
            for (i in 0 until def.count) {
                val uid = "${def.id}#$i"
                val state: CreatureState = save.creatures.firstOrNull { it.uid == uid } ?: run {
                    val created = CreatureState(uid, def.id, health = def.health)
                    placeInTerritory(created, loc.x, loc.z, loc.radius)
                    save.creatures.add(created)
                    created
                }
                val c = Creature(def, state)
                c.tx = state.x
                c.tz = state.z
                creatures += c
            }
        }
    }

    private fun placeInTerritory(s: CreatureState, cx: Float, cz: Float, radius: Float) {
        for (attempt in 0 until 12) {
            val a = rnd.nextFloat() * 6.283f
            val r = sqrt(rnd.nextFloat()) * radius
            s.x = cx + cos(a) * r
            s.z = cz + sin(a) * r
            if (Terrain.height(s.x, s.z) > Terrain.WATER + 0.3f) return
        }
    }

    // ---------------------------------------------------------------- public api

    fun submit(c: Command) {
        commands += c
    }

    /** Serialisable copy of the world, taken under the lock. */
    fun <T> withState(block: (SaveGame) -> T): T = synchronized(lock) { block(save) }

    fun update(dtIn: Float) = synchronized(lock) {
        lastUpdateNanos = System.nanoTime()
        val dt = dtIn.coerceIn(0f, 0.25f)
        while (true) {
            val c = commands.poll() ?: break
            handle(c)
        }
        updatePlayer(dt)
        save.minutes += (dt * MIN_PER_SEC).toDouble()
        updateNeeds(dt * MIN_PER_SEC / 60f, sleeping = false)
        for (n in npcs) {
            val dx = n.s.x - p.x
            val dz = n.s.z - p.z
            if (dx * dx + dz * dz < 70f * 70f) {
                stepNpc(n, dt, detailed = true)
            } else {
                // Mid-distance townsfolk are simulated in coarse steps.
                n.accum += dt
                if (n.accum >= 0.5f) {
                    stepNpc(n, n.accum, detailed = false)
                    n.accum = 0f
                }
            }
        }
        for (c in creatures) stepCreature(c, dt)
        slowTimer += dt
        if (slowTimer > 1f) {
            slowTimer = 0f
            slowTick()
        }
        targetTimer += dt
        if (targetTimer > 0.15f) {
            targetTimer = 0f
            findTarget()
        }
        hudTimer += dt
        publish(force = false)
    }

    fun fillFrame(f: RenderFrame) = synchronized(lock) {
        val cp = cos(p.pitch)
        val dx = sin(p.yaw) * cp
        val dy = sin(p.pitch)
        val dz = -cos(p.yaw) * cp
        val eyeY = p.y + if (swimming) 1.35f else 1.68f
        if (p.thirdPerson) {
            var cx = p.x - dx * 4.2f
            var cy = eyeY - dy * 4.2f + 0.7f
            var cz = p.z - dz * 4.2f
            val floor = max(Terrain.height(cx, cz), Terrain.WATER) + 0.4f
            if (cy < floor) cy = floor
            cx = cx.coerceIn(-Terrain.HALF + 2f, Terrain.HALF - 2f)
            cz = cz.coerceIn(-Terrain.HALF + 2f, Terrain.HALF - 2f)
            f.camX = cx; f.camY = cy; f.camZ = cz
        } else {
            f.camX = p.x; f.camY = eyeY; f.camZ = p.z
        }
        f.yaw = p.yaw
        f.pitch = p.pitch
        f.hour = Calendar.hour(save.minutes)
        f.mist = when (save.weather) { "mist" -> 1f; "overcast" -> 0.5f; else -> 0f }
        f.swing = swing
        f.blocking = input.block
        val weapon = equippedItem("weapon")
        f.firstPersonWeapon = !p.thirdPerson && weapon != null
        if (weapon != null) {
            f.bladeLength = (weapon.forged?.geometry?.length ?: 80f) / 100f
            val mat = weapon.forged?.materialId?.let { content.materials[it] }
            val rgb = if (mat != null) hexToRgb(mat.color) else floatArrayOf(0.72f, 0.73f, 0.76f)
            f.bladeColor[0] = rgb[0]; f.bladeColor[1] = rgb[1]; f.bladeColor[2] = rgb[2]
        }

        var a = 0
        fun put(x: Float, y: Float, z: Float, yaw: Float, phase: Float, scale: Float, kind: Int, flags: Int, c1: FloatArray, c2: FloatArray, c3: FloatArray) {
            if (a >= RenderFrame.MAX_ACTORS) return
            f.ax[a] = x; f.ay[a] = y; f.az[a] = z; f.ayaw[a] = yaw; f.aphase[a] = phase
            f.ascale[a] = scale; f.akind[a] = kind; f.aflags[a] = flags
            val o = a * 9
            f.acol[o] = c1[0]; f.acol[o + 1] = c1[1]; f.acol[o + 2] = c1[2]
            f.acol[o + 3] = c2[0]; f.acol[o + 4] = c2[1]; f.acol[o + 5] = c2[2]
            f.acol[o + 6] = c3[0]; f.acol[o + 7] = c3[1]; f.acol[o + 8] = c3[2]
            a++
        }
        if (p.thirdPerson) {
            put(p.x, p.y, p.z, p.yaw, walkPhase, 1f, 0, if (weapon != null) 8 else 0, playerSkin, PLAYER_CLOTH, PLAYER_HAIR)
        }
        val far = 170f * 170f
        for (n in npcs) {
            if (n.s.indoors) continue
            val ddx = n.s.x - p.x
            val ddz = n.s.z - p.z
            if (ddx * ddx + ddz * ddz > far) continue
            val flags = (if (n.hat) 1 else 0) or (if (n.def.beard) 2 else 0) or (if (n.def.longHair) 4 else 0)
            put(n.s.x, groundY(n.s.x, n.s.z), n.s.z, n.yaw, if (n.moving) n.phase else 0f, 0.94f + (n.def.age % 7) * 0.012f, 0, flags, n.skin, n.cloth, n.hair)
        }
        for (c in creatures) {
            if (c.s.health <= 0f) continue
            val ddx = c.s.x - p.x
            val ddz = c.s.z - p.z
            if (ddx * ddx + ddz * ddz > far) continue
            put(c.s.x, groundY(c.s.x, c.s.z), c.s.z, c.yaw, c.phase, c.def.size, 1, 0, c.color, c.color, c.color)
        }
        f.actorCount = a

        var k = 0
        for (r in resources) {
            if (k >= RenderFrame.MAX_NODES) break
            if ((save.harvested[r.id] ?: 0.0) > save.minutes) continue
            f.nx[k] = r.x; f.nz[k] = r.z; f.ny[k] = Terrain.height(r.x, r.z)
            f.nkind[k] = if (r.kind == "ore") 1 else 0
            k++
        }
        f.nodeCount = k
    }

    // ---------------------------------------------------------------- player

    private fun groundY(x: Float, z: Float) = max(Terrain.height(x, z), Terrain.WATER - 0.55f)

    private fun collide(pos: FloatArray, radius: Float) {
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

    private val tmpPos = FloatArray(2)

    private fun updatePlayer(dt: Float) {
        input.consumeLook(look)
        val sens = 0.0045f * input.sensitivity
        p.yaw += look[0] * sens
        if (p.yaw < 0f) p.yaw += TWO_PI
        if (p.yaw >= TWO_PI) p.yaw -= TWO_PI
        p.pitch = (p.pitch - look[1] * sens).coerceIn(-1.3f, 1.3f)

        val mx = input.moveX
        val my = input.moveY
        val mag = min(1f, sqrt(mx * mx + my * my))
        val blocking = input.block
        swimming = Terrain.height(p.x, p.z) < Terrain.WATER - 1.0f
        val sprinting = mag > 0.9f && p.stamina > 4f && !swimming && !blocking
        var speed = when {
            swimming -> 2.2f
            sprinting -> 6.2f
            blocking -> 2.2f
            else -> 3.6f
        }
        if (burden() > CAPACITY) speed *= 0.55f
        if (mag > 0.05f) {
            val fx = sin(p.yaw)
            val fz = -cos(p.yaw)
            val rx = cos(p.yaw)
            val rz = sin(p.yaw)
            tmpPos[0] = p.x + (fx * my + rx * mx) * speed * dt
            tmpPos[1] = p.z + (fz * my + rz * mx) * speed * dt
            collide(tmpPos, 0.45f)
            val r = sqrt(tmpPos[0] * tmpPos[0] + tmpPos[1] * tmpPos[1])
            if (r > Terrain.LIMIT) {
                tmpPos[0] *= Terrain.LIMIT / r
                tmpPos[1] *= Terrain.LIMIT / r
            }
            p.x = tmpPos[0]
            p.z = tmpPos[1]
            walkPhase += speed * mag * dt * 2.4f
            if (sprinting) p.stamina = max(0f, p.stamina - 9f * dt)
        }

        val ground = Terrain.height(p.x, p.z)
        if (swimming) {
            val float = Terrain.WATER - 1.25f
            p.y += (max(float, ground) - p.y) * min(1f, dt * 5f)
            vy = 0f
            grounded = true
        } else {
            if (grounded && vy <= 0f && p.y - ground < 0.6f) {
                p.y = ground
            } else {
                vy -= 16f * dt
                p.y += vy * dt
                if (p.y <= ground) {
                    p.y = ground
                    vy = 0f
                    grounded = true
                } else {
                    grounded = false
                }
            }
        }

        attackCooldown = max(0f, attackCooldown - dt)
        if (swing > 0f) swing = max(0f, swing - dt / 0.38f)

        val needs = if (p.hunger < 15f || p.fatigue < 15f) 0.45f else 1f
        if (!sprinting) p.stamina = min(maxStamina, p.stamina + (if (blocking) 2f else 12f) * needs * dt)
        p.magic = min(maxMagic, p.magic + 1.3f * dt)
        if (p.hunger > 30f && p.health > 0f) p.health = min(maxHealth, p.health + 0.35f * dt)
    }

    private fun updateNeeds(hours: Float, sleeping: Boolean) {
        p.hunger = max(0f, p.hunger - 4f * hours)
        p.fatigue = if (sleeping) min(100f, p.fatigue + 14f * hours) else max(0f, p.fatigue - 3.5f * hours)
        val h = Calendar.hour(save.minutes)
        val night = h >= 21f || h < 5f
        p.comfort = if (sleeping) min(100f, p.comfort + 6f * hours) else max(0f, p.comfort - (if (night) 4f else 2.2f) * hours)
        if (p.hunger <= 0f) p.health = max(1f, p.health - 6f * hours)
    }

    private fun burden(): Float {
        var w = 0f
        for (it in p.inventory) w += weightOf(it) * it.count
        return w
    }

    private fun weightOf(it: ItemInstance): Float = it.forged?.stats?.mass ?: content.items[it.defId]?.weight ?: 0.5f

    private fun equippedItem(slot: String): ItemInstance? {
        val uid = p.equipped[slot] ?: return null
        return p.inventory.firstOrNull { it.uid == uid }
    }

    private fun armor(): Float {
        var a = 0f
        for (slot in ARMOR_SLOTS) equippedItem(slot)?.let { a += content.items[it.defId]?.armor ?: 0f }
        return a
    }

    private fun skill(id: String) = p.skills[id] ?: 0f

    private fun gainSkill(id: String, amount: Float) {
        p.skills[id] = min(100f, skill(id) + amount)
    }

    // ---------------------------------------------------------------- commands

    private fun handle(c: Command) {
        when (c) {
            Command.Interact -> interact()
            Command.Attack -> attack()
            Command.Jump -> if (grounded && !swimming && p.stamina >= 6f) {
                vy = 6f
                grounded = false
                p.stamina -= 6f
            }
            Command.ToggleCamera -> p.thirdPerson = !p.thirdPerson
            Command.CastHeal -> castHeal()
            is Command.UseItem -> useItem(c.uid)
            is Command.ToggleEquip -> toggleEquip(c.uid)
            is Command.Drop -> {
                val it = p.inventory.firstOrNull { i -> i.uid == c.uid }
                if (it != null) {
                    removeItem(it, it.count)
                    toast("Dropped ${nameOf(it)}.")
                }
            }
            is Command.Chat -> chat(c.npcId)
            is Command.Gift -> gift(c.npcId, c.uid)
            is Command.Buy -> buy(c.npcId, c.itemId)
            is Command.Sell -> sell(c.npcId, c.uid)
            is Command.Wait -> {
                fastForward(c.hours * 60.0, sleeping = false)
                toast("${c.hours} hour${if (c.hours == 1) "" else "s"} pass.")
            }
            is Command.AddForged -> addForged(c)
        }
    }

    private fun toast(text: String) {
        events.tryEmit(UiEvent.Toast(text))
    }

    private fun record(text: String) {
        save.history += WorldEvent(save.minutes, text)
        while (save.history.size > 80) save.history.removeAt(0)
    }

    private fun nameOf(it: ItemInstance): String = it.forged?.name ?: content.itemName(it.defId)

    private fun addItem(defId: String, count: Int, forged: ForgedWeapon? = null) {
        addItemTo(save, content, defId, count, forged)
        packDirty = true
    }

    private fun removeItem(it: ItemInstance, count: Int) {
        it.count -= count
        if (it.count <= 0) {
            p.inventory.remove(it)
            val slot = p.equipped.entries.firstOrNull { e -> e.value == it.uid }?.key
            if (slot != null) p.equipped.remove(slot)
        }
        packDirty = true
    }

    private fun useItem(uid: String) {
        val it = p.inventory.firstOrNull { i -> i.uid == uid } ?: return
        val def = content.items[it.defId] ?: return
        when (def.type) {
            "food", "potion" -> {
                p.health = min(maxHealth, p.health + def.restoreHealth)
                p.hunger = min(100f, p.hunger + def.restoreHunger)
                p.comfort = min(100f, p.comfort + def.restoreComfort)
                p.magic = min(maxMagic, p.magic + def.restoreMagic)
                removeItem(it, 1)
                toast(if (def.type == "food") "You eat the ${def.name}." else "You drink the ${def.name}.")
            }
            "book" -> {
                gainSkill("speech", 0.5f)
                toast(def.description)
            }
            else -> toggleEquip(uid)
        }
    }

    private fun toggleEquip(uid: String) {
        val it = p.inventory.firstOrNull { i -> i.uid == uid } ?: return
        val slot = content.items[it.defId]?.slot.orEmpty()
        if (slot.isEmpty()) return
        if (p.equipped[slot] == uid) {
            p.equipped.remove(slot)
            toast("${nameOf(it)} put away.")
        } else {
            p.equipped[slot] = uid
            toast("${nameOf(it)} equipped.")
        }
        packDirty = true
    }

    private fun castHeal() {
        if (p.magic < 25f) {
            toast("Not enough magic.")
            return
        }
        if (p.health >= maxHealth) return
        p.magic -= 25f
        p.health = min(maxHealth, p.health + 22f + skill("restoration") * 0.3f)
        gainSkill("restoration", 0.6f)
        toast("Warmth knits your wounds.")
    }

    private fun attack() {
        if (attackCooldown > 0f) return
        if (p.stamina < 8f) {
            toast("Too winded to swing.")
            return
        }
        val weapon = equippedItem("weapon")
        val def = weapon?.let { content.items[it.defId] }
        val stats = weapon?.forged?.stats
        val damage = stats?.damage ?: def?.damage ?: 3f
        val reach = (stats?.reach ?: def?.reach ?: 1.2f) + 0.5f
        val speed = stats?.speed ?: 1f
        p.stamina -= 8f + (stats?.mass ?: def?.weight ?: 0.5f) * 2f
        attackCooldown = 0.6f / speed
        swing = 1f

        val fx = sin(p.yaw)
        val fz = -cos(p.yaw)
        var best: Creature? = null
        var bestDist = reach
        for (c in creatures) {
            if (c.s.health <= 0f) continue
            val dx = c.s.x - p.x
            val dz = c.s.z - p.z
            val d = sqrt(dx * dx + dz * dz)
            if (d > bestDist) continue
            if (d > 0.6f && (dx * fx + dz * fz) / d < 0.45f) continue
            best = c
            bestDist = d
        }
        val c = best ?: return
        val condition = weapon?.condition ?: 1f
        val dealt = damage * (0.85f + rnd.nextFloat() * 0.3f) * (1f + skill("blade") / 100f) * (0.6f + 0.4f * condition)
        c.s.health -= dealt
        gainSkill("blade", 0.4f)
        events.tryEmit(UiEvent.Hit)
        if (weapon != null) {
            val wear = 0.012f * (1f - (stats?.edgeRetention ?: 50f) / 140f)
            weapon.condition = max(0.05f, weapon.condition - wear)
            packDirty = true
        }
        if (c.s.health <= 0f) {
            c.s.respawnAt = save.minutes + 12 * 60.0
            val loot = ArrayList<String>()
            for (l in c.def.loot) {
                if (rnd.nextFloat() <= l.chance) {
                    addItem(l.item, l.count)
                    loot += if (l.count > 1) "${l.count} ${content.itemName(l.item)}" else content.itemName(l.item)
                }
            }
            toast(if (loot.isEmpty()) "The ${c.def.name} falls." else "The ${c.def.name} falls. You take ${loot.joinToString(" and ")}.")
            record("${p.name} brought down a ${c.def.name}.")
        }
    }

    private fun hurtPlayer(amount: Float, by: String) {
        var dmg = max(1f, amount - armor() * 0.5f)
        if (input.block && p.stamina > 5f) {
            dmg *= if (equippedItem("shield") != null) 0.15f else 0.35f
            p.stamina = max(0f, p.stamina - 9f)
            gainSkill("block", 0.5f)
        }
        p.health -= dmg
        events.tryEmit(UiEvent.Hurt)
        if (p.health <= 0f) {
            p.health = maxHealth * 0.5f
            val b = inn
            if (b != null) {
                val d = doorOf(b)
                p.x = d[0]; p.z = d[1]
            } else {
                p.x = 0f; p.z = 10f
            }
            p.y = Terrain.height(p.x, p.z)
            fastForward(6 * 60.0, sleeping = true)
            toast("A $by nearly ended you. Tomas dragged you back to the village.")
            record("${p.name} was carried home after a fight with a $by.")
        }
    }

    // ---------------------------------------------------------------- interaction

    private fun findTarget() {
        val fx = sin(p.yaw)
        val fz = -cos(p.yaw)
        var best: Target? = null
        var bestD = 3.4f
        fun consider(x: Float, z: Float, range: Float, make: () -> Target) {
            val dx = x - p.x
            val dz = z - p.z
            val d = sqrt(dx * dx + dz * dz)
            if (d > range) return
            if (d > 1.6f && (dx * fx + dz * fz) / d < 0.2f) return
            val score = d * (3.4f / range)
            if (score < bestD) {
                bestD = score
                best = make()
            }
        }
        for (n in npcs) {
            if (n.s.indoors) continue
            consider(n.s.x, n.s.z, 3.4f) { Target("npc", n.def.id, "Talk to ${n.def.name}") }
        }
        for (r in resources) {
            if ((save.harvested[r.id] ?: 0.0) > save.minutes) continue
            consider(r.x, r.z, 3f) { Target("resource", r.id, if (r.kind == "ore") "Mine ${r.name}" else "Gather ${r.name}") }
        }
        for (b in interactBuildings) {
            val door = if (b.type == "well") floatArrayOf(b.x, b.z) else doorOf(b)
            val label = when (b.interact) {
                "rest" -> "Rest at ${b.name} (5 gold)"
                "forge" -> "Use the forge"
                "wash" -> "Wash at the well"
                else -> b.name
            }
            consider(door[0], door[1], if (b.type == "well") 3.6f else 4.2f) { Target("building", b.id, label) }
        }
        target = best

        var h: Creature? = null
        var hd = 14f
        for (c in creatures) {
            if (c.s.health <= 0f || !c.def.aggressive) continue
            val dx = c.s.x - p.x
            val dz = c.s.z - p.z
            val d = sqrt(dx * dx + dz * dz)
            if (d < hd) {
                hd = d
                h = c
            }
        }
        hostile = h
    }

    private fun interact() {
        findTarget()
        val t = target ?: return
        when (t.kind) {
            "npc" -> {
                val n = npcById[t.id] ?: return
                n.pause = 10f
                n.yaw = atan2(p.x - n.s.x, -(p.z - n.s.z))
                events.tryEmit(UiEvent.Dialogue(n.def.id, greeting(n)))
            }
            "resource" -> {
                val r = content.resources[t.id] ?: return
                save.harvested[r.id] = save.minutes + r.respawnHours * 60.0
                addItem(r.item, r.count)
                toast("${if (r.kind == "ore") "Mined" else "Gathered"} ${r.count} ${content.itemName(r.item)}.")
                target = null
            }
            "building" -> {
                val b = content.buildings[t.id] ?: return
                when (b.interact) {
                    "rest" -> {
                        if (p.gold < 5) {
                            toast("A bed costs 5 gold.")
                        } else {
                            p.gold -= 5
                            npcs.firstOrNull { it.prof.workplace == "inn" && it.prof.trader }?.let { it.s.gold += 5 }
                            val h = Calendar.hour(save.minutes)
                            val until = if (h < 6f) 6f - h else 30f - h
                            fastForward(until * 60.0, sleeping = true)
                            p.health = maxHealth
                            toast("You wake rested at dawn.")
                            packDirty = true
                        }
                    }
                    "forge" -> events.tryEmit(UiEvent.OpenForge)
                    "wash" -> {
                        p.comfort = min(100f, p.comfort + 35f)
                        save.minutes += 10.0
                        toast("Cold well water. You feel cleaner.")
                    }
                }
            }
        }
    }

    private fun relationOf(n: Npc): String = when {
        n.def.relation == "family" -> "family"
        n.s.affinity >= 50 -> "friend"
        n.s.affinity < 25 -> "rival"
        else -> "stranger"
    }

    private fun greeting(n: Npc): String {
        if (n.def.greeting.isNotBlank()) return n.def.greeting
        return when (relationOf(n)) {
            "friend" -> "Good to see you, ${p.name}."
            "rival" -> "Oh. It's you."
            "family" -> "There you are!"
            else -> "Can I help you, stranger?"
        }
    }

    private fun remember(n: Npc, text: String) {
        n.s.memories += "${Calendar.dateLabel(save.minutes)}: $text"
        while (n.s.memories.size > 10) n.s.memories.removeAt(0)
    }

    private fun chat(id: String) {
        val n = npcById[id] ?: return
        n.pause = 10f
        val options = ArrayList<String>()
        options += "I was ${n.s.activity.replaceFirstChar { it.lowercase() }} until you came by."
        if (n.s.hunger < 35f) options += "Forgive me, my stomach is louder than I am."
        if (n.s.fatigue < 30f) options += "I can barely keep my eyes open today."
        if (n.def.likes.isNotBlank()) options += "Nothing in the vale beats ${n.def.likes}."
        if (n.def.fears.isNotBlank()) options += "Between us? What keeps me up is ${n.def.fears}."
        n.s.memories.lastOrNull()?.let { options += "You heard? ${it.substringAfter(": ")}." }
        n.s.bonds.maxByOrNull { it.value }?.let { e -> npcById[e.key]?.let { o -> options += "${o.def.name} and I get on well these days." } }
        n.s.bonds.minByOrNull { it.value }?.takeIf { it.value < 0 }?.let { e -> npcById[e.key]?.let { o -> options += "Do not get me started on ${o.def.name}." } }
        if (save.weather == "mist") options += "This mist gets into the bones."
        var line = options[rnd.nextInt(options.size)]
        if (save.minutes - n.s.lastChat > 180.0) {
            val gain = 2 + (skill("speech") / 20f).toInt() + if (n.trait("sociability") > 0.6f) 1 else 0
            n.s.affinity = min(100, n.s.affinity + gain)
            n.s.social = min(100f, n.s.social + 15f)
            n.s.lastChat = save.minutes
            gainSkill("speech", 0.6f)
            remember(n, "Talked with ${p.name}")
        } else {
            line += " ...But we spoke not long ago."
        }
        events.tryEmit(UiEvent.Dialogue(id, line))
    }

    private fun gift(id: String, uid: String) {
        val n = npcById[id] ?: return
        val it = p.inventory.firstOrNull { i -> i.uid == uid } ?: return
        val value = it.forged?.let { f -> (f.stats.damage * 5f).toInt() } ?: content.items[it.defId]?.value ?: 1
        val gain = (value / 3 + 2).coerceIn(2, 12)
        val name = nameOf(it)
        removeItem(it, 1)
        n.s.affinity = min(100, n.s.affinity + gain)
        remember(n, "Received $name from ${p.name}")
        val line = if (n.trait("greed") > 0.6f) "For me? I will not say no." else "You did not have to. Thank you, truly."
        events.tryEmit(UiEvent.Dialogue(id, line))
        if (relationOf(n) == "friend" && n.s.affinity - gain < 50) record("${n.def.name} now counts ${p.name} as a friend.")
    }

    private fun buyPrice(n: Npc, value: Int) = max(1, ceil(value * 1.25f * (1f - (n.s.affinity - 40) / 400f)).toInt())
    private fun sellPrice(n: Npc, value: Int) = max(1, (value * 0.5f * (1f + (n.s.affinity - 40) / 400f)).toInt())

    private fun buy(id: String, itemId: String) {
        val n = npcById[id] ?: return
        val entry = n.s.stock.firstOrNull { it.item == itemId && it.count > 0 } ?: return
        val def = content.items[itemId] ?: return
        val price = buyPrice(n, def.value)
        if (p.gold < price) {
            events.tryEmit(UiEvent.Dialogue(id, "That is $price gold. Come back when your purse is heavier."))
            return
        }
        p.gold -= price
        n.s.gold += price
        entry.count -= 1
        addItem(itemId, 1)
        gainSkill("speech", 0.2f)
    }

    private fun sell(id: String, uid: String) {
        val n = npcById[id] ?: return
        val it = p.inventory.firstOrNull { i -> i.uid == uid } ?: return
        val value = it.forged?.let { f -> (f.stats.damage * 5f * it.condition).toInt() } ?: content.items[it.defId]?.value ?: 1
        val price = sellPrice(n, value)
        if (n.s.gold < price) {
            events.tryEmit(UiEvent.Dialogue(id, "I cannot afford that today."))
            return
        }
        n.s.gold -= price
        p.gold += price
        if (it.forged == null && content.items.containsKey(it.defId)) {
            val stock = n.s.stock.firstOrNull { s -> s.item == it.defId }
            if (stock != null) stock.count += 1 else n.s.stock += StockEntry(it.defId, 1)
        }
        removeItem(it, 1)
        gainSkill("speech", 0.2f)
    }

    private fun addForged(c: Command.AddForged) {
        val have = p.inventory.filter { it.defId == c.materialItem }.sumOf { it.count }
        if (have < c.ingots) {
            toast("You no longer have enough ${content.itemName(c.materialItem)}.")
            return
        }
        var left = c.ingots
        for (it in p.inventory.filter { i -> i.defId == c.materialItem }) {
            val take = min(left, it.count)
            removeItem(it, take)
            left -= take
            if (left <= 0) break
        }
        addItem("forged_blade", 1, c.weapon)
        gainSkill("smithing", 2.5f)
        save.minutes += 90.0
        toast("${c.weapon.name} added to your pack.")
        record("${p.name} forged a ${c.weapon.quality.lowercase()} ${c.weapon.name}.")
    }

    // ---------------------------------------------------------------- npc simulation

    private fun gate(name: String) = if (name == "north") floatArrayOf(0f, -50f) else floatArrayOf(0f, 50f)

    private fun routeTo(n: Npc, tx: Float, tz: Float, targetGate: String) {
        n.path.clear()
        val out = n.s.x * n.s.x + n.s.z * n.s.z > 47f * 47f
        val curGate = if (!out) "" else if (n.s.z > 0f) "south" else "north"
        if (curGate.isNotEmpty() && curGate != targetGate) n.path += gate(curGate)
        if (targetGate.isNotEmpty() && targetGate != curGate) n.path += gate(targetGate)
        n.path += floatArrayOf(tx, tz)
        n.s.indoors = false
    }

    private fun decide(n: Npc) {
        val s = n.s
        val h = Calendar.hour(save.minutes)
        val night = h >= 21.5f || h < 5.5f
        val working = n.workPos != null && h >= n.prof.workStart && h < n.prof.workEnd
        var best = "wander"
        var bestScore = 0.15f + n.trait("curiosity") * 0.1f + if (s.action == "wander") 0.12f else 0f
        fun offer(action: String, score: Float) {
            val v = score + if (s.action == action) 0.14f else 0f
            if (v > bestScore) {
                bestScore = v
                best = action
            }
        }
        offer("sleep", (1f - s.fatigue / 100f) * (if (night) 1.6f else 0.45f) + if (night) 0.35f else 0f)
        offer("eat", (1f - s.hunger / 100f) * 1.3f)
        if (working) offer("work", 0.5f + n.trait("ambition") * 0.25f)
        offer("social", (1f - s.social / 100f) * (0.4f + n.trait("sociability") * 0.6f) * (if (night) 0.3f else 1f))
        if (best != s.action || (n.path.isEmpty() && best == "wander" && rnd.nextFloat() < 0.5f)) begin(n, best, h)
    }

    private fun begin(n: Npc, action: String, h: Float) {
        val s = n.s
        s.action = action
        when (action) {
            "sleep" -> {
                routeTo(n, n.homeDoor[0], n.homeDoor[1], "")
                n.destName = n.homeName
                n.destLabel = "Sleeping"
            }
            "eat" -> {
                val b = inn
                if (b != null && s.gold >= 4 && n.trait("sociability") > 0.5f && n.def.home != b.id) {
                    val d = doorOf(b, (rnd.nextFloat() - 0.5f) * 8f)
                    routeTo(n, d[0], d[1] + (rnd.nextFloat() - 0.5f) * 2f, "")
                    n.destName = b.name
                    n.destLabel = "Eating at ${b.name}"
                    s.gold -= 3
                    npcs.firstOrNull { it.prof.workplace == "inn" && it.prof.trader }?.let { it.s.gold += 3 }
                } else {
                    routeTo(n, n.homeDoor[0], n.homeDoor[1], "")
                    n.destName = n.homeName
                    n.destLabel = "Eating at home"
                }
            }
            "work" -> {
                val w = n.workPos ?: n.homeDoor
                routeTo(n, w[0], w[1], n.workGate)
                n.destName = n.workName
                n.destLabel = n.prof.activity
            }
            "social" -> {
                val b = inn
                if (h >= 18f && b != null) {
                    val d = doorOf(b, (rnd.nextFloat() - 0.5f) * 9f)
                    routeTo(n, d[0] - 1.5f, d[1], "")
                    n.destName = b.name
                    n.destLabel = "Sharing a drink outside ${b.name}"
                } else {
                    val a = rnd.nextFloat() * TWO_PI
                    val r = 3f + rnd.nextFloat() * 6f
                    routeTo(n, cos(a) * r, 2f + sin(a) * r, "")
                    n.destName = "the square"
                    n.destLabel = "Chatting in the square"
                }
                n.nextSocialAt = save.minutes + 10.0
            }
            else -> {
                val a = rnd.nextFloat() * TWO_PI
                val r = 6f + rnd.nextFloat() * 20f
                routeTo(n, cos(a) * r, sin(a) * r, "")
                n.destName = "the village"
                n.destLabel = "Taking the air"
            }
        }
        val far = n.path.size > 1
        s.activity = if (far) "Heading to ${n.destName}" else "Walking to ${n.destName}"
    }

    private fun stepNpc(n: Npc, dt: Float, detailed: Boolean) {
        val s = n.s
        val hours = dt * MIN_PER_SEC / 60f
        s.hunger = max(0f, s.hunger - 5f * hours)
        s.fatigue = max(0f, s.fatigue - 4.2f * hours)
        s.social = max(0f, s.social - 3f * hours)
        if (save.minutes >= n.decideAt) {
            decide(n)
            n.decideAt = save.minutes + 10.0 + rnd.nextDouble() * 8.0
        }
        n.moving = false
        if (n.pause > 0f) {
            n.pause -= dt
            return
        }
        var remaining = (1.7f - (n.def.age - 30).coerceAtLeast(0) * 0.008f) * dt
        while (remaining > 0f && n.path.isNotEmpty()) {
            val wp = n.path.first()
            val dx = wp[0] - s.x
            val dz = wp[1] - s.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist <= remaining || dist < 0.05f) {
                s.x = wp[0]
                s.z = wp[1]
                remaining -= dist
                n.path.removeFirst()
            } else {
                s.x += dx / dist * remaining
                s.z += dz / dist * remaining
                n.yaw = atan2(dx, -dz)
                n.moving = true
                remaining = 0f
            }
        }
        if (n.moving) {
            if (detailed) n.phase += dt * 7f
            return
        }
        if (n.path.isNotEmpty()) return

        // Arrived: carry out the chosen activity.
        s.activity = n.destLabel.ifEmpty { "Idle" }
        when (s.action) {
            "sleep" -> {
                s.indoors = true
                s.fatigue = min(100f, s.fatigue + 17f * hours)
                if (s.fatigue >= 98f) n.decideAt = min(n.decideAt, save.minutes + 1.0)
            }
            "eat" -> {
                s.indoors = n.destName == n.homeName
                s.hunger = min(100f, s.hunger + 65f * hours)
                if (s.hunger >= 96f) n.decideAt = min(n.decideAt, save.minutes + 0.5)
            }
            "work" -> {
                n.wageAcc += n.prof.wage * hours
                if (n.wageAcc >= 1f) {
                    val coins = n.wageAcc.toInt()
                    s.gold += coins
                    n.wageAcc -= coins
                }
                if (detailed) n.yaw = atan2(-s.x, s.z)
            }
            "social" -> {
                s.social = min(100f, s.social + 30f * hours)
                if (save.minutes >= n.nextSocialAt) {
                    n.nextSocialAt = save.minutes + 50.0
                    mingle(n)
                }
            }
        }
    }

    /** Two townsfolk who are both socialising nearby gossip, or occasionally quarrel. */
    private fun mingle(n: Npc) {
        val other = npcs.firstOrNull {
            it !== n && it.s.action == "social" && it.path.isEmpty() &&
                abs(it.s.x - n.s.x) < 12f && abs(it.s.z - n.s.z) < 12f
        } ?: return
        val temper = (n.trait("aggression") + other.trait("aggression")) / 2f
        val bond = n.s.bonds[other.def.id] ?: 0
        if (rnd.nextFloat() < temper * 0.35f && bond < 6) {
            n.s.bonds[other.def.id] = bond - 3
            other.s.bonds[n.def.id] = (other.s.bonds[n.def.id] ?: 0) - 3
            remember(n, "Quarrelled with ${other.def.name}")
            remember(other, "Quarrelled with ${n.def.name}")
            record("${n.def.name} and ${other.def.name} quarrelled in ${n.destName}.")
        } else {
            n.s.bonds[other.def.id] = bond + 2
            other.s.bonds[n.def.id] = (other.s.bonds[n.def.id] ?: 0) + 2
            remember(n, "Shared gossip with ${other.def.name}")
            remember(other, "Shared gossip with ${n.def.name}")
            if (bond + 2 == 10) record("${n.def.name} and ${other.def.name} have become fast friends.")
        }
        n.yaw = atan2(other.s.x - n.s.x, -(other.s.z - n.s.z))
        other.yaw = atan2(n.s.x - other.s.x, -(n.s.z - other.s.z))
    }

    /** Skips time with the abstract simulation: needs, schedules and travel still resolve. */
    private fun fastForward(minutes: Double, sleeping: Boolean) {
        var left = minutes
        while (left > 0.0) {
            val step = min(5.0, left)
            save.minutes += step
            updateNeeds((step / 60.0).toFloat(), sleeping)
            val seconds = (step / MIN_PER_SEC).toFloat()
            for (n in npcs) stepNpc(n, seconds, detailed = false)
            left -= step
        }
        slowTick()
        packDirty = true
    }

    private fun slowTick() {
        val day = Calendar.day(save.minutes)
        val h = Calendar.hour(save.minutes)
        // Traders restock every morning.
        if (h >= 6f) {
            for (n in npcs) {
                if (n.prof.trader && n.s.lastRestockDay != day) {
                    n.s.lastRestockDay = day
                    for (g in n.prof.goods) {
                        val e = n.s.stock.firstOrNull { it.item == g.item }
                        if (e == null) n.s.stock += StockEntry(g.item, g.count) else if (e.count < g.count) e.count = g.count
                    }
                }
            }
        }
        val slot = day * 4 + (h / 6f).toInt()
        if (slot != lastWeatherSlot) {
            if (lastWeatherSlot != -1) {
                val roll = rnd.nextFloat()
                save.weather = when {
                    roll < 0.55f -> "clear"
                    roll < 0.8f -> "overcast"
                    else -> "mist"
                }
            }
            lastWeatherSlot = slot
        }
        val it = save.harvested.entries.iterator()
        while (it.hasNext()) if (it.next().value <= save.minutes) it.remove()
    }

    // ---------------------------------------------------------------- creatures

    private fun stepCreature(c: Creature, dt: Float) {
        val s = c.s
        val loc = content.locations[c.def.territory] ?: return
        val dx = p.x - s.x
        val dz = p.z - s.z
        val dist = sqrt(dx * dx + dz * dz)
        if (s.health <= 0f) {
            if (save.minutes >= s.respawnAt && dist > 45f) {
                s.health = c.def.health
                placeInTerritory(s, loc.x, loc.z, loc.radius)
                c.tx = s.x
                c.tz = s.z
            }
            return
        }
        if (dist > 110f) return
        val homeDx = loc.x - s.x
        val homeDz = loc.z - s.z
        val fromHome = sqrt(homeDx * homeDx + homeDz * homeDz)
        var speed = 1.3f
        c.cooldown = max(0f, c.cooldown - dt)
        if (c.def.aggressive && dist < 16f && fromHome < loc.radius * 1.7f) {
            c.tx = p.x
            c.tz = p.z
            speed = c.def.speed
            if (dist < 1.9f) {
                speed = 0f
                if (c.cooldown <= 0f) {
                    c.cooldown = 1.4f
                    hurtPlayer(c.def.damage, c.def.name)
                }
            }
        } else if (!c.def.aggressive && dist < 12f) {
            c.tx = s.x - dx / max(dist, 0.1f) * 14f
            c.tz = s.z - dz / max(dist, 0.1f) * 14f
            speed = c.def.speed
        } else {
            c.retarget -= dt
            val tdx = c.tx - s.x
            val tdz = c.tz - s.z
            if (c.retarget <= 0f || tdx * tdx + tdz * tdz < 1f) {
                c.retarget = 5f + rnd.nextFloat() * 9f
                val a = rnd.nextFloat() * TWO_PI
                val r = sqrt(rnd.nextFloat()) * loc.radius
                c.tx = loc.x + cos(a) * r
                c.tz = loc.z + sin(a) * r
            }
        }
        val tdx = c.tx - s.x
        val tdz = c.tz - s.z
        val td = sqrt(tdx * tdx + tdz * tdz)
        if (td > 0.3f && speed > 0f) {
            val step = min(td, speed * dt)
            val nx = s.x + tdx / td * step
            val nz = s.z + tdz / td * step
            if (Terrain.height(nx, nz) > Terrain.WATER - 0.3f && nx * nx + nz * nz < 190f * 190f) {
                s.x = nx
                s.z = nz
            } else {
                c.retarget = 0f
            }
            c.yaw = atan2(tdx, -tdz)
            c.phase += dt * (4f + speed * 2f)
        } else if (dist < 3f) {
            c.yaw = atan2(dx, -dz)
        }
    }

    // ---------------------------------------------------------------- snapshots

    private fun locationName(): String {
        var best: String? = null
        var bestR = Float.MAX_VALUE
        for (l in content.locations.values) {
            val dx = p.x - l.x
            val dz = p.z - l.z
            if (dx * dx + dz * dz < l.radius * l.radius && l.radius < bestR) {
                bestR = l.radius
                best = l.name
            }
        }
        return best ?: "The Wilds"
    }

    private fun publish(force: Boolean) {
        val deg = Math.toDegrees(p.yaw.toDouble()).toFloat()
        if (abs(deg - heading.value) > 0.4f) heading.value = deg
        if (!force && hudTimer < 0.2f) return
        hudTimer = 0f
        val h = hostile
        hud.value = Hud(
            health = p.health, maxHealth = maxHealth,
            stamina = p.stamina, maxStamina = maxStamina,
            magic = p.magic, maxMagic = maxMagic,
            timeLabel = Calendar.label(save.minutes),
            clock = Calendar.clock(save.minutes),
            location = locationName(),
            prompt = target?.label,
            promptKind = target?.kind.orEmpty(),
            thirdPerson = p.thirdPerson,
            fps = fps,
            swimming = swimming,
            weather = save.weather,
            targetName = h?.def?.name,
            targetHealth = if (h != null) (h.s.health / h.def.health).coerceIn(0f, 1f) else 0f,
        )
        publishJournal()
        if (packDirty || force) {
            packDirty = false
            publishPack()
        }
    }

    private var journalTick = 0

    private fun publishJournal() {
        journalTick++
        if (journalTick % 4 != 1) return
        val views = npcs.map { n ->
            NpcView(
                id = n.def.id, def = n.def,
                profession = n.prof.name,
                race = content.races[n.def.race]?.name ?: n.def.race,
                activity = n.s.activity,
                affinity = n.s.affinity,
                relation = relationOf(n),
                hunger = n.s.hunger, fatigue = n.s.fatigue, social = n.s.social,
                gold = n.s.gold,
                memories = n.s.memories.toList().asReversed(),
                bonds = n.s.bonds.entries.sortedByDescending { abs(it.value) }.take(4)
                    .mapNotNull { e -> npcById[e.key]?.let { it.def.name to e.value } },
                home = n.homeName,
                work = n.workName,
                trader = n.prof.trader,
                wares = if (!n.prof.trader) emptyList() else n.s.stock.filter { it.count > 0 }.mapNotNull { e ->
                    content.items[e.item]?.let { d -> WareView(d.id, d.name, d.icon, e.count, buyPrice(n, d.value)) }
                },
                skin = n.skinHex, cloth = n.clothHex, hat = n.hat,
            )
        }.sortedByDescending { it.affinity }
        journal.value = JournalView(
            npcs = views,
            hunger = p.hunger, fatigue = p.fatigue, comfort = p.comfort,
            dateLine = "${Calendar.clock(save.minutes)}, ${Calendar.dateLabel(save.minutes)}",
            season = Calendar.season(save.minutes),
            history = save.history.takeLast(14).asReversed().map { "${Calendar.dateLabel(it.minute)}: ${it.text}" },
            skills = p.skills.entries.sortedByDescending { it.value }.map { it.key to it.value },
            playerName = p.name,
        )
    }

    private fun publishPack() {
        val items = p.inventory.map { it ->
            val def = content.items[it.defId]
            ItemView(
                uid = it.uid, defId = it.defId,
                name = nameOf(it),
                type = def?.type ?: "missing",
                slot = def?.slot.orEmpty(),
                icon = def?.icon ?: "misc",
                count = it.count,
                condition = it.condition,
                weight = weightOf(it),
                value = it.forged?.let { f -> (f.stats.damage * 5f).toInt() } ?: def?.value ?: 0,
                description = it.forged?.let { f -> "${f.quality} work in ${content.materials[f.materialId]?.name ?: f.materialId}, shaped at your own anvil." }
                    ?: def?.description ?: "This item came from a content pack that is no longer enabled.",
                equipped = p.equipped.containsValue(it.uid),
                damage = it.forged?.stats?.damage ?: def?.damage ?: 0f,
                armor = def?.armor ?: 0f,
                forged = it.forged,
                usable = def != null && (def.type == "food" || def.type == "potion" || def.type == "book"),
            )
        }
        pack.value = PackView(items, p.gold, burden(), CAPACITY, skill("smithing"))
    }

    companion object {
        const val CAPACITY = 60f
        private const val TWO_PI = 6.2831855f
        private val ARMOR_SLOTS = listOf("head", "chest", "shield")
        private val PLAYER_CLOTH = floatArrayOf(0.37f, 0.42f, 0.27f)
        private val PLAYER_HAIR = floatArrayOf(0.23f, 0.16f, 0.12f)

        fun addItemTo(save: SaveGame, content: ContentRegistry, defId: String, count: Int, forged: ForgedWeapon? = null) {
            val def = content.items[defId]
            val inv = save.player.inventory
            if (def != null && def.stackable && forged == null) {
                val existing = inv.firstOrNull { it.defId == defId && it.forged == null }
                if (existing != null) {
                    existing.count += count
                    return
                }
                inv += ItemInstance("i${save.nextUid++}", defId, count)
            } else {
                repeat(count) { inv += ItemInstance("i${save.nextUid++}", defId, 1, 1f, forged) }
            }
        }

        fun newGame(content: ContentRegistry, name: String, raceId: String, backgroundId: String): SaveGame {
            val save = SaveGame(
                minutes = Calendar.START_MINUTES,
                packs = content.packs.filter { it.loaded }.map { it.manifest.id },
            )
            val p = save.player
            p.name = name.ifBlank { "Wanderer" }
            p.race = raceId
            p.background = backgroundId
            val bg = content.backgrounds[backgroundId]
            bg?.items?.forEach { addItemTo(save, content, it.item, it.count) }
            p.gold = bg?.gold ?: 20
            bg?.skills?.forEach { (k, v) -> p.skills[k] = v }
            p.health = 88f
            p.stamina = 60f
            p.magic = 75f
            p.y = Terrain.height(p.x, p.z)
            save.history += WorldEvent(save.minutes, "${p.name} came down the road to Greenhollow.")
            return save
        }
    }
}
