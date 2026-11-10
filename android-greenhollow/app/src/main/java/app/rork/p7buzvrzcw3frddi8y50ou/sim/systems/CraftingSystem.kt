package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World

/** Turning ingots into the blade designed at the forge. */
class CraftingSystem(private val w: World) {
    fun addForged(c: Command.AddForged) {
        val p = w.p
        if (w.inventory.count(c.materialItem) < c.ingots) {
            w.toast("You no longer have enough ${w.content.itemName(c.materialItem)}.")
            return
        }
        w.inventory.removeByDef(c.materialItem, c.ingots)
        w.inventory.addItem("forged_blade", 1, c.weapon)
        w.progression.gainXp("smithing", 12f)
        w.save.minutes += 90.0
        w.toast("${c.weapon.name} added to your pack.")
        w.record("${p.name} forged a ${c.weapon.quality.lowercase()} ${c.weapon.name}.")
    }
}
