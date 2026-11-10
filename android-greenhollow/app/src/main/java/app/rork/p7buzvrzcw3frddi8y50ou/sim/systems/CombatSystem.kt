package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Creature
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Terrain
import app.rork.p7buzvrzcw3frddi8y50ou.sim.UiEvent
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Swinging, being bitten, and the one spell the player knows. */
class CombatSystem(private val w: World) {
    private val p get() = w.p

    fun attack() {
        if (w.attackCooldown > 0f) return
        if (p.stamina < 8f) {
            w.toast("Too winded to swing.")
            return
        }
        val prog = w.progression
        val weapon = w.inventory.equippedItem("main_hand")
        val def = weapon?.let { w.content.items[it.defId] }
        val stats = weapon?.forged?.stats
        val damage = stats?.damage ?: def?.damage ?: 3f
        val reach = (stats?.reach ?: def?.reach ?: 1.2f) + 0.5f
        val speed = (stats?.speed ?: def?.speed ?: 1f).coerceAtLeast(0.2f)
        val type = weapon?.let { w.inventory.weaponType(it) }.orEmpty()
        p.stamina -= 8f + (stats?.mass ?: def?.weight ?: 0.5f) * 2f
        w.attackCooldown = 0.6f / (speed * Progression.attackSpeedFactor(prog.attr("agility")))
        w.swing = 1f

        val fx = sin(p.yaw)
        val fz = -cos(p.yaw)
        var best: Creature? = null
        var bestDist = reach
        for (c in w.creatures) {
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
        val leaning = prog.attr(if (type == "dagger") "agility" else "strength")
        val dealt = damage * (0.85f + w.rng.nextFloat() * 0.3f) * (1f + prog.skill("blades") / 100f) *
            (0.6f + 0.4f * condition) * Progression.meleeFactor(leaning)
        c.s.health -= dealt
        prog.gainXp("blades", 1f)
        w.events.tryEmit(UiEvent.Hit)
        if (weapon != null) {
            val wear = if (def != null && def.durability > 0) 1f / def.durability else 0.012f * (1f - (stats?.edgeRetention ?: 50f) / 140f)
            weapon.condition = max(0.05f, weapon.condition - wear)
            w.packDirty = true
        }
        if (c.s.health <= 0f) {
            c.s.respawnAt = w.save.minutes + 12 * 60.0
            val bonus = prog.skill("hunting") / 200f
            val loot = ArrayList<String>()
            for (l in c.def.loot) {
                if (w.rng.nextFloat() <= l.chance + bonus) {
                    w.inventory.addItem(l.item, l.count)
                    loot += if (l.count > 1) "${l.count} ${w.content.itemName(l.item)}" else w.content.itemName(l.item)
                }
            }
            w.toast(if (loot.isEmpty()) "The ${c.def.name} falls." else "The ${c.def.name} falls. You take ${loot.joinToString(" and ")}.")
            w.record("${p.name} brought down a ${c.def.name}.")
            w.discovery.killed(c.def.id)
            prog.gainXp("hunting", 4f)
        }
    }

    fun hurtPlayer(amount: Float, by: String) {
        val prog = w.progression
        var dmg = max(1f, amount - w.inventory.armor() * 0.5f)
        if (w.input.block && p.stamina > 5f) {
            dmg *= if (w.inventory.equippedItem("off_hand") != null) 0.15f else 0.35f
            p.stamina = max(0f, p.stamina - 9f * (1f - prog.skill("blocking") / 200f))
            prog.gainXp("blocking", 1.5f)
        }
        w.inventory.wearArmor()
        p.health -= dmg
        w.events.tryEmit(UiEvent.Hurt)
        if (p.health <= 0f) {
            p.health = prog.maxHealth * 0.5f
            val b = w.inn
            if (b != null) {
                val d = w.doorOf(b)
                p.x = d[0]; p.z = d[1]
            } else {
                p.x = 0f; p.z = 10f
            }
            p.y = Terrain.height(p.x, p.z)
            w.time.fastForward(6 * 60.0, sleeping = true)
            w.toast("A $by nearly ended you. Tomas dragged you back to the village.")
            w.record("${p.name} was carried home after a fight with a $by.")
        }
    }

    fun castHeal() {
        val prog = w.progression
        if (p.magic < 25f) {
            w.toast("Not enough magic.")
            return
        }
        if (p.health >= prog.maxHealth) return
        p.magic -= 25f
        p.health = min(prog.maxHealth, p.health + 22f + prog.skill("restoration") * 0.3f)
        prog.gainXp("restoration", 2f)
        w.toast("Warmth knits your wounds.")
    }
}
