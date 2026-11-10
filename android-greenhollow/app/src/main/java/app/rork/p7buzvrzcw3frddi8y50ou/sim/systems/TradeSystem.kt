package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Npc
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.sim.StockEntry
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.ceil
import kotlin.math.max

/** Buying, selling and the morning restock. */
class TradeSystem(private val w: World) {
    private val p get() = w.p

    private fun shift(): Float = Progression.priceShift(w.progression.attr("charm"), w.progression.skill("trading"))

    fun buyPrice(n: Npc, value: Int): Int =
        max(1, ceil(value * 1.25f * (1f - (n.s.affinity - 40) / 400f) * (1f - shift())).toInt())

    fun sellPrice(n: Npc, value: Int): Int =
        max(1, (value * 0.5f * (1f + (n.s.affinity - 40) / 400f) * (1f + shift())).toInt())

    fun buy(id: String, itemId: String) {
        val n = w.npcById[id] ?: return
        val canonical = w.content.items.canonical(itemId)
        val entry = n.s.stock.firstOrNull { it.item == canonical && it.count > 0 } ?: return
        val def = w.content.items[canonical] ?: return
        val price = buyPrice(n, def.value)
        if (p.gold < price) {
            w.dialogue.say(n, "That is $price gold. Come back when your purse is heavier.")
            return
        }
        p.gold -= price
        n.s.gold += price
        entry.count -= 1
        w.inventory.addItem(canonical, 1)
        w.progression.gainXp("trading", 1f)
        w.journalDirty = true
    }

    fun sell(id: String, uid: String) {
        val n = w.npcById[id] ?: return
        val it = w.inventory.find(uid) ?: return
        val price = sellPrice(n, w.inventory.valueOf(it, worn = true))
        if (n.s.gold < price) {
            w.dialogue.say(n, "I cannot afford that today.")
            return
        }
        n.s.gold -= price
        p.gold += price
        if (it.forged == null && w.content.items.containsKey(it.defId)) {
            val stock = n.s.stock.firstOrNull { s -> s.item == it.defId }
            if (stock != null) stock.count += 1 else n.s.stock += StockEntry(it.defId, 1)
        }
        w.inventory.removeItem(it, 1)
        w.progression.gainXp("trading", 1f)
        w.journalDirty = true
    }

    /** Traders refill their shelves once a day, after six in the morning. */
    fun restock(day: Int, hour: Float) {
        if (hour < 6f) return
        for (n in w.npcs) {
            if (!n.prof.trader || n.s.lastRestockDay == day) continue
            n.s.lastRestockDay = day
            for (g in n.prof.goods) {
                val e = n.s.stock.firstOrNull { it.item == g.item }
                if (e == null) n.s.stock += StockEntry(g.item, g.count) else if (e.count < g.count) e.count = g.count
            }
        }
    }
}
