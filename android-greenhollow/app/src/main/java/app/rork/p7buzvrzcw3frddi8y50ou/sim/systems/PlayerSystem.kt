package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.sim.TWO_PI
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Terrain
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Looking, walking, sprinting, swimming, jumping, and the slow return of breath and magic. */
class PlayerSystem(private val w: World) {
    private val look = FloatArray(2)
    private val tmpPos = FloatArray(2)
    private var effort = 0f

    fun update(dt: Float) {
        val p = w.p
        val input = w.input
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
        w.swimming = Terrain.height(p.x, p.z) < Terrain.WATER - 1.0f
        val sprinting = mag > 0.9f && p.stamina > 4f && !w.swimming && !blocking
        var speed = when {
            w.swimming -> 2.2f
            sprinting -> 6.2f
            blocking -> 2.2f
            else -> 3.6f
        }
        if (w.inventory.burden() > w.progression.capacity) speed *= 0.55f
        if (mag > 0.05f) {
            val fx = sin(p.yaw)
            val fz = -cos(p.yaw)
            val rx = cos(p.yaw)
            val rz = sin(p.yaw)
            tmpPos[0] = p.x + (fx * my + rx * mx) * speed * dt
            tmpPos[1] = p.z + (fz * my + rz * mx) * speed * dt
            w.collide(tmpPos, 0.45f)
            val r = sqrt(tmpPos[0] * tmpPos[0] + tmpPos[1] * tmpPos[1])
            if (r > Terrain.LIMIT) {
                tmpPos[0] *= Terrain.LIMIT / r
                tmpPos[1] *= Terrain.LIMIT / r
            }
            p.x = tmpPos[0]
            p.z = tmpPos[1]
            w.walkPhase += speed * mag * dt * 2.4f
            if (sprinting) {
                val drain = 9f * (1f - w.progression.skill("athletics") / 200f)
                p.stamina = max(0f, p.stamina - drain * dt)
            }
            if (sprinting || w.swimming) {
                effort += dt
                if (effort >= 4f) {
                    effort -= 4f
                    w.progression.gainXp("athletics", 1f)
                }
            }
        }

        val ground = Terrain.height(p.x, p.z)
        if (w.swimming) {
            val float = Terrain.WATER - 1.25f
            p.y += (max(float, ground) - p.y) * min(1f, dt * 5f)
            w.vy = 0f
            w.grounded = true
        } else {
            if (w.grounded && w.vy <= 0f && p.y - ground < 0.6f) {
                p.y = ground
            } else {
                w.vy -= 16f * dt
                p.y += w.vy * dt
                if (p.y <= ground) {
                    p.y = ground
                    w.vy = 0f
                    w.grounded = true
                } else {
                    w.grounded = false
                }
            }
        }

        w.attackCooldown = max(0f, w.attackCooldown - dt)
        if (w.swing > 0f) w.swing = max(0f, w.swing - dt / 0.38f)

        val prog = w.progression
        val needs = if (p.hunger < 15f || p.fatigue < 15f) 0.45f else 1f
        if (!sprinting) {
            val regen = (if (blocking) 2f else 12f) * needs * w.inventory.staminaRegenFactor()
            p.stamina = min(prog.maxStamina, p.stamina + regen * dt)
        }
        p.magic = min(prog.maxMagic, p.magic + 1.3f * Progression.magicRegenFactor(prog.attr("willpower")) * dt)
        if (p.hunger > 30f && p.health > 0f) p.health = min(prog.maxHealth, p.health + 0.35f * dt)
    }

    fun jump() {
        val p = w.p
        if (w.grounded && !w.swimming && p.stamina >= 6f) {
            w.vy = 6f
            w.grounded = false
            p.stamina -= 6f
            w.progression.gainXp("athletics", 0.3f)
        }
    }
}
