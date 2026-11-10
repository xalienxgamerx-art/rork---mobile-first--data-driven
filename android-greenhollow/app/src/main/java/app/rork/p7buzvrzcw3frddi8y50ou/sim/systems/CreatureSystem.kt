package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Creature
import app.rork.p7buzvrzcw3frddi8y50ou.sim.CreatureState
import app.rork.p7buzvrzcw3frddi8y50ou.sim.TWO_PI
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Terrain
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Wildlife: roaming a territory, hunting or fleeing the player, and returning after death. */
class CreatureSystem(private val w: World) {
    private val save get() = w.save
    private val rnd get() = w.rng

    fun build() {
        val states = save.creatures.associateBy { it.uid }
        for (def in w.content.creatures.values) {
            val loc = w.content.locations[def.territory] ?: continue
            for (i in 0 until def.count) {
                val uid = "${def.id}#$i"
                val state: CreatureState = states[uid] ?: run {
                    val created = CreatureState(uid, def.id, health = def.health)
                    placeInTerritory(created, loc.x, loc.z, loc.radius)
                    save.creatures.add(created)
                    created
                }
                val c = Creature(def, state)
                c.tx = state.x
                c.tz = state.z
                w.creatures += c
            }
        }
    }

    private fun placeInTerritory(s: CreatureState, cx: Float, cz: Float, radius: Float) {
        for (attempt in 0 until 12) {
            val a = rnd.nextFloat() * TWO_PI
            val r = sqrt(rnd.nextFloat()) * radius
            s.x = cx + cos(a) * r
            s.z = cz + sin(a) * r
            if (Terrain.height(s.x, s.z) > Terrain.WATER + 0.3f) return
        }
    }

    fun update(dt: Float) {
        for (c in w.creatures) step(c, dt)
    }

    private fun step(c: Creature, dt: Float) {
        val p = w.p
        val s = c.s
        val loc = w.content.locations[c.def.territory] ?: return
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
                    w.combat.hurtPlayer(c.def.damage, c.def.name)
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
}
