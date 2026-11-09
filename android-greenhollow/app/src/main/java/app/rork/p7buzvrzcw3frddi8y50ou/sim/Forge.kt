package app.rork.p7buzvrzcw3frddi8y50ou.sim

import app.rork.p7buzvrzcw3frddi8y50ou.data.MaterialDef
import kotlin.math.abs
import kotlin.math.ceil

/** How well the smith treated the metal. All values 0..1. */
data class ProcessQuality(
    val forging: Float = 0.7f,
    val hardening: Float = 0.8f,
    val temper: Float = 0.5f,
    val defects: Float = 0f,
)

/**
 * Geometry-driven weapon physics. A blade's stats are never authored: they are derived from
 * its dimensions, the material's properties and how it was forged.
 */
object ForgePhysics {
    private const val GUARD_GRAMS = 110f
    private const val GRIP_GRAMS_PER_CM = 7f
    private const val GRAMS_PER_INGOT = 400f

    fun bladeVolume(g: BladeGeometry): Float =
        g.length * g.width * (g.thickness / 10f) * 0.62f * (1f - 0.35f * g.taper) * (if (g.fuller) 0.88f else 1f)

    fun bladeGrams(g: BladeGeometry, m: MaterialDef): Float = bladeVolume(g) * m.density

    fun ingotsNeeded(g: BladeGeometry, m: MaterialDef): Int =
        ceil(bladeGrams(g, m) / GRAMS_PER_INGOT).toInt().coerceAtLeast(1)

    fun compute(g: BladeGeometry, m: MaterialDef, q: ProcessQuality): WeaponStats {
        val blade = bladeGrams(g, m)
        val grip = g.handle * GRIP_GRAMS_PER_CM
        val total = blade + grip + GUARD_GRAMS + g.pommel
        val massKg = total / 1000f

        // Positions in cm measured from the guard, positive toward the tip.
        val bladeCom = g.length * (0.5f - 0.14f * g.taper)
        val gripCom = -g.handle / 2f
        val pommelCom = -(g.handle + 2f)
        val balance = (blade * bladeCom + grip * gripCom + g.pommel * pommelCom) / total

        // Moment of inertia about the leading hand, kg m^2.
        val hand = -g.handle * 0.3f
        fun arm(x: Float) = ((x - hand) / 100f).let { it * it }
        val lenM = g.length / 100f
        val inertia = (blade / 1000f) * (arm(bladeCom) + lenM * lenM / 12f * (1f - 0.3f * g.taper)) +
            (grip / 1000f) * arm(gripCom) + (g.pommel / 1000f) * arm(pommelCom) + (GUARD_GRAMS / 1000f) * arm(0f)

        val twoHanded = g.handle > 24f
        val handling = (100f - inertia * 230f - maxOf(0f, massKg - 1f) * 12f + if (twoHanded) 10f else 0f)
            .coerceIn(5f, 100f)

        val hardness = m.hardness * (0.55f + 0.45f * q.hardening) * (1f - 0.25f * q.temper)
        var toughness = m.toughness * (0.75f + 0.25f * q.forging) * (1f + 0.35f * q.temper) * (1f - 0.6f * q.defects)
        toughness *= 1f - 0.3f * q.hardening * (1f - q.temper)

        val sharp = ((50f - g.edgeAngle) / 30f).coerceIn(0.15f, 1.15f)
        val curve = 1f + g.curvature / 25f * 0.22f
        val percussion = (massKg * balance / 20f).coerceIn(0.5f, 1.6f)
        val cutting = ((hardness * 0.6f + 25f) * sharp * curve * (0.6f + 0.4f * percussion) * 1.3f).coerceIn(1f, 100f)

        val stiffness = (g.thickness / (g.length / 14f)).coerceIn(0.4f, 1.4f)
        val tip = 0.5f + 0.6f * g.taper
        val thrust = (55f * stiffness * tip * (1f - g.curvature / 40f) * (0.7f + hardness / 200f)).coerceIn(1f, 100f)

        val slender = g.length / (g.width * g.thickness)
        val integrity = (toughness * (1.25f - slender * 0.09f) * (if (g.fuller) 0.96f else 1f)).coerceIn(1f, 100f)
        val edge = (m.edgeRetention * (0.5f + 0.5f * q.hardening) * (0.7f + g.edgeAngle / 100f)).coerceIn(1f, 100f)

        val reach = 0.85f + g.length / 100f + if (twoHanded) 0.1f else 0f
        val speed = 0.6f + handling / 100f * 0.8f
        val damage = (cutting * 0.12f + thrust * 0.06f + massKg * 3f) * (0.8f + 0.2f * integrity / 100f)

        return WeaponStats(massKg, balance, inertia, reach, handling, cutting, thrust, integrity, edge, damage, speed)
    }

    fun className(g: BladeGeometry): String = when {
        g.length < 38f -> "Dagger"
        g.length < 62f -> if (g.curvature > 12f) "Hanger" else "Shortsword"
        g.length < 98f -> when {
            g.curvature > 16f -> "Scimitar"
            g.curvature > 8f -> "Sabre"
            g.handle > 24f -> "Bastard Sword"
            else -> "Longsword"
        }
        else -> "Greatsword"
    }

    fun qualityName(q: ProcessQuality): String {
        val score = q.forging * 0.4f + q.hardening * 0.3f + (1f - abs(q.temper - 0.6f)) * 0.15f + (1f - q.defects) * 0.15f
        return when {
            q.defects > 0.5f -> "Flawed"
            score < 0.45f -> "Crude"
            score < 0.68f -> "Serviceable"
            score < 0.86f -> "Fine"
            else -> "Masterwork"
        }
    }
}
