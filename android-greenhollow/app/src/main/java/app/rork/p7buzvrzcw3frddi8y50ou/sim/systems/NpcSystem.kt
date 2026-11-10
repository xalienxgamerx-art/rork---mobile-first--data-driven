package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.data.hexToRgb
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Calendar
import app.rork.p7buzvrzcw3frddi8y50ou.sim.MIN_PER_SEC
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Npc
import app.rork.p7buzvrzcw3frddi8y50ou.sim.NpcState
import app.rork.p7buzvrzcw3frddi8y50ou.sim.StockEntry
import app.rork.p7buzvrzcw3frddi8y50ou.sim.TWO_PI
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The villagers' own lives: needs, choices, travel, wages and gossip. */
class NpcSystem(private val w: World) {
    private val save get() = w.save
    private val content get() = w.content
    private val rnd get() = w.rng

    fun build() {
        val states = save.npcs.associateBy { it.id }
        for (def in content.npcs.values) {
            val prof = content.professions[def.profession] ?: continue
            val home = content.buildings[def.home] ?: continue
            val hashSide = ((def.id.hashCode() and 0xFF) / 255f - 0.5f) * 4f
            val door = w.doorOf(home, hashSide)
            val state: NpcState = states[def.id] ?: run {
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
                n.workPos = w.doorOf(building, hashSide * 0.7f)
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
            w.npcs += n
            w.npcById[def.id] = n
        }
    }

    /** Villagers near the player move every frame; the rest are stepped twice a second. */
    fun update(dt: Float) {
        val p = w.p
        for (n in w.npcs) {
            val dx = n.s.x - p.x
            val dz = n.s.z - p.z
            if (dx * dx + dz * dz < 70f * 70f) {
                step(n, dt, detailed = true)
            } else {
                n.accum += dt
                if (n.accum >= 0.5f) {
                    step(n, n.accum, detailed = false)
                    n.accum = 0f
                }
            }
        }
    }

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
                val b = w.inn
                if (b != null && s.gold >= 4 && n.trait("sociability") > 0.5f && n.def.home != b.id) {
                    val d = w.doorOf(b, (rnd.nextFloat() - 0.5f) * 8f)
                    routeTo(n, d[0], d[1] + (rnd.nextFloat() - 0.5f) * 2f, "")
                    n.destName = b.name
                    n.destLabel = "Eating at ${b.name}"
                    s.gold -= 3
                    w.innkeeper()?.let { it.s.gold += 3 }
                } else {
                    routeTo(n, n.homeDoor[0], n.homeDoor[1], "")
                    n.destName = n.homeName
                    n.destLabel = "Eating at home"
                }
            }
            "work" -> {
                val at = n.workPos ?: n.homeDoor
                routeTo(n, at[0], at[1], n.workGate)
                n.destName = n.workName
                n.destLabel = n.prof.activity
            }
            "social" -> {
                val b = w.inn
                if (h >= 18f && b != null) {
                    val d = w.doorOf(b, (rnd.nextFloat() - 0.5f) * 9f)
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

    fun step(n: Npc, dt: Float, detailed: Boolean) {
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
        val other = w.npcs.firstOrNull {
            it !== n && it.s.action == "social" && it.path.isEmpty() &&
                abs(it.s.x - n.s.x) < 12f && abs(it.s.z - n.s.z) < 12f
        } ?: return
        val temper = (n.trait("aggression") + other.trait("aggression")) / 2f
        val bond = n.s.bonds[other.def.id] ?: 0
        val talk = w.dialogue
        if (rnd.nextFloat() < temper * 0.35f && bond < 6) {
            n.s.bonds[other.def.id] = bond - 3
            other.s.bonds[n.def.id] = (other.s.bonds[n.def.id] ?: 0) - 3
            talk.remember(n, "Quarrelled with ${other.def.name}")
            talk.remember(other, "Quarrelled with ${n.def.name}")
            w.record("${n.def.name} and ${other.def.name} quarrelled in ${n.destName}.")
        } else {
            n.s.bonds[other.def.id] = bond + 2
            other.s.bonds[n.def.id] = (other.s.bonds[n.def.id] ?: 0) + 2
            talk.remember(n, "Shared gossip with ${other.def.name}")
            talk.remember(other, "Shared gossip with ${n.def.name}")
            if (bond + 2 == 10) w.record("${n.def.name} and ${other.def.name} have become fast friends.")
        }
        n.yaw = atan2(other.s.x - n.s.x, -(other.s.z - n.s.z))
        other.yaw = atan2(n.s.x - other.s.x, -(n.s.z - other.s.z))
    }
}
