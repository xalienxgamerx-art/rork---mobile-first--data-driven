package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.data.BuildingDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.InteractableDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ItemDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ResourceDef
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Calendar
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Creature
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Target
import app.rork.p7buzvrzcw3frddi8y50ou.sim.UiEvent
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One way to use anything in the world. Every kind of thing (people, resource nodes, buildings,
 * objects placed by content packs) offers a target; the nearest one in front of the player wins
 * and the Use button acts on it.
 */
class InteractionSystem(private val w: World) {
    private val p get() = w.p
    private val save get() = w.save

    val resources: List<ResourceDef> = w.content.resources.values.toList()
    val objects: List<InteractableDef> = w.content.interactables.values.toList()
    private val interactBuildings: List<BuildingDef> = w.content.buildings.values.filter { it.interact.isNotEmpty() }

    private fun promptFor(o: InteractableDef): String {
        val base = o.prompt.ifBlank {
            when {
                o.sleep -> "Sleep in ${o.name}"
                o.kind == "container" -> "Open ${o.name}"
                o.kind == "sign" || o.kind == "book" -> "Read ${o.name}"
                else -> "Examine ${o.name}"
            }
        }
        return if (o.cost > 0) "$base (${o.cost} gold)" else base
    }

    fun findTarget() {
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
        for (n in w.npcs) {
            if (n.s.indoors) continue
            consider(n.s.x, n.s.z, 3.4f) { Target("npc", n.def.id, "Talk to ${n.def.name}") }
        }
        for (r in resources) {
            if ((save.harvested[r.id] ?: 0.0) > save.minutes) continue
            consider(r.x, r.z, 3f) { Target("resource", r.id, if (r.kind == "ore") "Mine ${r.name}" else "Gather ${r.name}") }
        }
        for (o in objects) {
            consider(o.x, o.z, 3f) { Target("object", o.id, promptFor(o)) }
        }
        for (b in interactBuildings) {
            val door = if (b.type == "well") floatArrayOf(b.x, b.z) else w.doorOf(b)
            val label = when (b.interact) {
                "rest" -> "Rest at ${b.name} (5 gold)"
                "forge" -> "Use the forge"
                "wash" -> "Wash at the well"
                else -> b.name
            }
            consider(door[0], door[1], if (b.type == "well") 3.6f else 4.2f) { Target("building", b.id, label) }
        }
        w.target = best

        var h: Creature? = null
        var hd = 14f
        for (c in w.creatures) {
            if (c.s.health <= 0f || !c.def.aggressive) continue
            val dx = c.s.x - p.x
            val dz = c.s.z - p.z
            val d = sqrt(dx * dx + dz * dz)
            if (d < hd) {
                hd = d
                h = c
            }
        }
        w.hostile = h
    }

    fun interact() {
        findTarget()
        val t = w.target ?: return
        when (t.kind) {
            "npc" -> w.npcById[t.id]?.let { w.dialogue.start(it) }
            "resource" -> w.content.resources[t.id]?.let { harvest(it) }
            "building" -> w.content.buildings[t.id]?.let { useBuilding(it) }
            "object" -> w.content.interactables[t.id]?.let { useObject(it) }
        }
    }

    private fun harvest(r: ResourceDef) {
        save.harvested[r.id] = save.minutes + r.respawnHours * 60.0
        var count = r.count
        if (w.rng.nextFloat() < w.progression.skill("foraging") / 100f) count++
        w.inventory.addItem(r.item, count)
        w.toast("${if (r.kind == "ore") "Mined" else "Gathered"} $count ${w.content.itemName(r.item)}.")
        w.progression.gainXp("foraging", 2f)
        w.target = null
    }

    private fun sleepUntilDawn() {
        val h = Calendar.hour(save.minutes)
        val until = if (h < 6f) 6f - h else 30f - h
        w.time.fastForward(until * 60.0, sleeping = true)
        p.health = w.progression.maxHealth
        w.toast("You wake rested at dawn.")
        w.packDirty = true
    }

    private fun useBuilding(b: BuildingDef) {
        when (b.interact) {
            "rest" -> {
                if (p.gold < 5) {
                    w.toast("A bed costs 5 gold.")
                } else {
                    p.gold -= 5
                    w.innkeeper()?.let { it.s.gold += 5 }
                    sleepUntilDawn()
                }
            }
            "forge" -> w.events.tryEmit(UiEvent.OpenForge)
            "wash" -> {
                p.comfort = min(100f, p.comfort + 35f)
                save.minutes += 10.0
                w.toast("Cold well water. You feel cleaner.")
            }
        }
    }

    private fun useObject(o: InteractableDef) {
        val owner = if (o.owner.isNotEmpty()) w.npcById[o.owner] else null
        if (owner != null && owner.def.relation != "family" && owner.s.affinity < o.accessAffinity) {
            w.toast("${o.name} belongs to ${owner.def.name}. You are not on good enough terms to use it.")
            return
        }
        if (o.cost > 0) {
            if (p.gold < o.cost) {
                w.toast("That costs ${o.cost} gold.")
                return
            }
            p.gold -= o.cost
            owner?.let { it.s.gold += o.cost }
            w.packDirty = true
        }

        if (o.loot.isNotEmpty()) {
            val last = save.opened[o.id]
            val ready = last == null || (o.respawnHours > 0 && save.minutes >= last + o.respawnHours * 60.0)
            if (!ready) {
                w.toast("${o.name} is empty.")
            } else {
                save.opened[o.id] = save.minutes
                val found = ArrayList<String>()
                for (l in o.loot) {
                    if (w.rng.nextFloat() <= l.chance) {
                        w.inventory.addItem(l.item, l.count)
                        found += if (l.count > 1) "${l.count} ${w.content.itemName(l.item)}" else w.content.itemName(l.item)
                    }
                }
                w.toast(if (found.isEmpty()) "${o.name} holds nothing of use." else "You take ${found.joinToString(", ")}.")
            }
        }

        if (o.restore.isNotEmpty()) {
            val key = "cd:${o.id}"
            val last = save.opened[key]
            if (last != null && save.minutes < last + o.cooldownHours * 60.0) {
                w.toast("Nothing more comes of it for now.")
            } else {
                if (o.cooldownHours > 0) save.opened[key] = save.minutes
                val prog = w.progression
                for ((what, amount) in o.restore) {
                    when (what) {
                        "health" -> p.health = (p.health + amount).coerceIn(1f, prog.maxHealth)
                        "magic" -> p.magic = (p.magic + amount).coerceIn(0f, prog.maxMagic)
                        "stamina" -> p.stamina = (p.stamina + amount).coerceIn(0f, prog.maxStamina)
                        "hunger" -> p.hunger = (p.hunger + amount).coerceIn(0f, 100f)
                        "comfort" -> p.comfort = (p.comfort + amount).coerceIn(0f, 100f)
                        "fatigue" -> p.fatigue = (p.fatigue + amount).coerceIn(0f, 100f)
                    }
                }
                if (o.text.isBlank() && !o.sleep) w.toast("You feel restored.")
            }
        }

        if (o.sleep) sleepUntilDawn()
        if (o.text.isNotBlank()) w.events.tryEmit(UiEvent.Read(o.name, o.text.replace("{player}", p.name)))
        if (o.rumour.isNotEmpty()) w.discovery.learnRumour(o.rumour, o.name)

        if (o.skillXp.isNotEmpty() && save.flags.add("used:${o.id}")) {
            for ((skill, xp) in o.skillXp) w.progression.gainXp(skill, xp)
        }
        w.journalDirty = true
    }

    /** Reading a book carried in the pack. */
    fun readBook(def: ItemDef) {
        w.events.tryEmit(UiEvent.Read(def.name, def.text.ifBlank { def.description }.replace("{player}", p.name)))
        if (save.flags.add("read:${def.id}")) w.progression.gainXp("speech", 2f)
        if (def.rumour.isNotEmpty()) w.discovery.learnRumour(def.rumour, def.name)
    }
}
