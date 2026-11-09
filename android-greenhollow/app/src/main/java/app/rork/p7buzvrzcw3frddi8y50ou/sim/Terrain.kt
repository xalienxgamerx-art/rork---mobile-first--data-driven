package app.rork.p7buzvrzcw3frddi8y50ou.sim

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The shape of the vale. Pure functions of world coordinates, shared by the simulation
 * (walking, swimming, placement) and the renderer (terrain mesh). North is -z, east is +x.
 */
object Terrain {
    const val HALF = 200f
    const val WATER = -0.9f
    const val LIMIT = 186f

    fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun hash(ix: Int, iz: Int): Float {
        var h = ix * 374761393 + iz * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    fun noise(x: Float, z: Float): Float {
        val fx = floor(x)
        val fz = floor(z)
        val ix = fx.toInt()
        val iz = fz.toInt()
        val tx = x - fx
        val tz = z - fz
        val sx = tx * tx * (3f - 2f * tx)
        val sz = tz * tz * (3f - 2f * tz)
        val a = lerp(hash(ix, iz), hash(ix + 1, iz), sx)
        val b = lerp(hash(ix, iz + 1), hash(ix + 1, iz + 1), sx)
        return lerp(a, b, sz)
    }

    /** Fractal noise in roughly -1..1. */
    fun fbm(x: Float, z: Float): Float {
        var sum = 0f
        var amp = 0.5f
        var f = 1f
        for (i in 0 until 4) {
            sum += (noise(x * f + i * 17.3f, z * f - i * 9.1f) - 0.5f) * 2f * amp
            amp *= 0.5f
            f *= 2.03f
        }
        return sum
    }

    fun riverZ(x: Float) = -95f + 18f * sin(x * 0.018f)

    private fun rolling(x: Float, z: Float): Float {
        val r = sqrt(x * x + z * z)
        val hills = fbm(x * 0.011f, z * 0.011f) * 7f + 3.2f + fbm(x * 0.05f, z * 0.05f) * 0.8f
        return lerp(1.4f, hills, smooth(58f, 115f, r))
    }

    fun height(x: Float, z: Float): Float {
        val r = sqrt(x * x + z * z)
        var h = rolling(x, z)
        // The road south of the village follows a gentle, flattened profile.
        if (z > 30f) {
            val k = 1f - smooth(3f, 15f, abs(x))
            if (k > 0f) h = lerp(h, max(rolling(0f, z), 0.4f), k)
        }
        // The Greyback ring, with a pass where the road leaves the vale.
        val m = max(0f, r - 150f)
        val pass = if (z > 0f) smooth(12f, 46f, abs(x)) else 1f
        h += m * m * 0.017f * pass
        // The Silverrun.
        val d = abs(z - riverZ(x))
        val carve = 1f - smooth(5f, 17f, d)
        if (carve > 0f) h = lerp(h, -2.6f, carve)
        return h
    }

    fun onRoad(x: Float, z: Float): Float {
        // 1 on the packed dirt of the road or square, 0 on grass.
        val road = if (z > 20f) 1f - smooth(1.8f, 3.4f, abs(x + sin(z * 0.05f) * 0.6f)) else 0f
        val r = sqrt(x * x + z * z)
        val square = 1f - smooth(22f, 33f, r)
        return max(road, square)
    }

    class Tree(val x: Float, val z: Float, val scale: Float, val kind: Int, val tint: Float)

    /** Deterministic scatter of trees. The same list feeds every graphics preset (lower presets take a prefix). */
    fun trees(max: Int, clearings: List<FloatArray>): List<Tree> {
        val rnd = Random(4242)
        val out = ArrayList<Tree>(max)
        var attempts = 0
        while (out.size < max && attempts < max * 12) {
            attempts++
            val x = (rnd.nextFloat() * 2f - 1f) * (HALF - 4f)
            val z = (rnd.nextFloat() * 2f - 1f) * (HALF - 4f)
            val r = sqrt(x * x + z * z)
            if (r < 58f) continue
            if (z > 30f && abs(x) < 6f) continue
            val h = height(x, z)
            if (h < WATER + 0.5f || h > 34f) continue
            if (clearings.any { (x - it[0]) * (x - it[0]) + (z - it[1]) * (z - it[1]) < it[2] * it[2] }) continue
            val density = fbm(x * 0.02f + 40f, z * 0.02f - 12f)
            if (density < -0.12f && rnd.nextFloat() > 0.18f) continue
            val pine = h > 9f || fbm(x * 0.015f - 30f, z * 0.015f + 8f) > 0.12f
            out += Tree(x, z, 0.8f + rnd.nextFloat() * 0.7f, if (pine) 1 else 0, rnd.nextFloat())
        }
        return out
    }
}
