package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.data.LocationDef
import app.rork.p7buzvrzcw3frddi8y50ou.sim.BestiaryEntry
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Marker
import app.rork.p7buzvrzcw3frddi8y50ou.sim.RumourEntry
import app.rork.p7buzvrzcw3frddi8y50ou.sim.SaveMigrations
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.atan2

/** What the player has found: places, creatures and rumours. */
class DiscoverySystem(private val w: World) {
    private val p get() = w.p
    private val save get() = w.save
    private val locations: List<LocationDef> = w.content.locations.values.toList()

    /** The smallest named place the player is standing in. */
    fun currentLocation(): LocationDef? {
        var best: LocationDef? = null
        var bestR = Float.MAX_VALUE
        for (l in locations) {
            val dx = p.x - l.x
            val dz = p.z - l.z
            if (dx * dx + dz * dz < l.radius * l.radius && l.radius < bestR) {
                bestR = l.radius
                best = l
            }
        }
        return best
    }

    /**
     * Called once when a world starts running. A world carried over from before discoveries
     * existed quietly knows where it is and the village it has been living in.
     */
    fun bind() {
        if (save.flags.remove(SaveMigrations.FLAG_MIGRATED_V1) && save.discovered.isEmpty()) {
            currentLocation()?.let { save.discovered[it.id] = save.minutes }
            w.inn?.let { inn ->
                locations.filter { l ->
                    val dx = inn.x - l.x
                    val dz = inn.z - l.z
                    dx * dx + dz * dz < l.radius * l.radius
                }.minByOrNull { it.radius }?.let { if (it.id !in save.discovered) save.discovered[it.id] = save.minutes }
            }
        }
    }

    fun tick() {
        val here = currentLocation()
        if (here != null && here.id !in save.discovered) {
            save.discovered[here.id] = save.minutes
            w.toast("Discovered: ${here.name}")
            w.record("${p.name} found ${here.name}.")
            w.progression.addLevelProgress(0.25f)
        }
        for (c in w.creatures) {
            if (c.s.health <= 0f || c.def.id in save.bestiary) continue
            val dx = c.s.x - p.x
            val dz = c.s.z - p.z
            if (dx * dx + dz * dz < 18f * 18f) {
                save.bestiary[c.def.id] = BestiaryEntry(firstSeen = save.minutes)
                w.toast("Bestiary: ${c.def.name}")
                w.journalDirty = true
            }
        }
    }

    fun killed(creatureId: String) {
        val e = save.bestiary.getOrPut(creatureId) { BestiaryEntry(firstSeen = save.minutes) }
        e.kills++
        w.journalDirty = true
    }

    fun knowsRumour(id: String): Boolean {
        val canonical = w.content.rumours.canonical(id)
        return save.rumours.any { it.id == canonical }
    }

    fun learnRumour(id: String, source: String) {
        val def = w.content.rumours[id] ?: return
        if (knowsRumour(def.id)) return
        save.rumours += RumourEntry(def.id, save.minutes, source)
        w.toast("Rumour: ${def.title}")
        w.progression.addLevelProgress(0.2f)
        w.journalDirty = true
    }

    /** Compass markers for every place except the one the player is standing in. */
    fun markers(): List<Marker> {
        val here = currentLocation()
        val out = ArrayList<Marker>(locations.size)
        for (l in locations) {
            if (l === here) continue
            val dx = l.x - p.x
            val dz = l.z - p.z
            if (dx * dx + dz * dz < 4f) continue
            var deg = Math.toDegrees(atan2(dx, -dz).toDouble()).toFloat()
            if (deg < 0f) deg += 360f
            out += Marker(deg, if (l.id in save.discovered) l.name else null)
        }
        return out
    }
}
