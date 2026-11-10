package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Calendar
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.max
import kotlin.math.min

/** Hunger, tiredness and comfort wearing away with the hours. */
class NeedsSystem(private val w: World) {
    fun update(hours: Float, sleeping: Boolean) {
        val p = w.p
        p.hunger = max(0f, p.hunger - 4f * hours)
        p.fatigue = if (sleeping) min(100f, p.fatigue + 14f * hours) else max(0f, p.fatigue - 3.5f * hours)
        val h = Calendar.hour(w.save.minutes)
        val night = h >= 21f || h < 5f
        p.comfort = if (sleeping) min(100f, p.comfort + 6f * hours) else max(0f, p.comfort - (if (night) 4f else 2.2f) * hours)
        if (p.hunger <= 0f) p.health = max(1f, p.health - 6f * hours)
    }
}
