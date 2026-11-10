package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Calendar
import app.rork.p7buzvrzcw3frddi8y50ou.sim.MIN_PER_SEC
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.min

/** The clock, the weather, and everything that happens once in a while rather than every frame. */
class TimeSystem(private val w: World) {
    private val save get() = w.save
    private var lastWeatherSlot = -1

    fun advance(dt: Float) {
        save.minutes += (dt * MIN_PER_SEC).toDouble()
    }

    /** Skips time with the abstract simulation: needs, schedules and travel still resolve. */
    fun fastForward(minutes: Double, sleeping: Boolean) {
        var left = minutes
        while (left > 0.0) {
            val step = min(5.0, left)
            save.minutes += step
            w.needs.update((step / 60.0).toFloat(), sleeping)
            val seconds = (step / MIN_PER_SEC).toFloat()
            for (n in w.npcs) w.npcSim.step(n, seconds, detailed = false)
            left -= step
        }
        slowTick()
        w.packDirty = true
        w.journalDirty = true
    }

    fun passHours(hours: Int) {
        fastForward(hours * 60.0, sleeping = false)
        w.toast("$hours hour${if (hours == 1) "" else "s"} pass.")
    }

    /** Runs about once a second, and after any jump in time. */
    fun slowTick() {
        val day = Calendar.day(save.minutes)
        val h = Calendar.hour(save.minutes)
        w.trade.restock(day, h)
        val slot = day * 4 + (h / 6f).toInt()
        if (slot != lastWeatherSlot) {
            if (lastWeatherSlot != -1) {
                val roll = w.rng.nextFloat()
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
        w.discovery.tick()
    }
}
