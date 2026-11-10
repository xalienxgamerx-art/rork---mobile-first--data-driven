package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.data.ContentRegistry
import app.rork.p7buzvrzcw3frddi8y50ou.data.ItemDef
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ForgedWeapon
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ItemInstance
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.sim.SaveGame
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlin.math.max
import kotlin.math.min

/** What the player carries and wears. */
class InventorySystem(private val w: World) {
    private val p get() = w.p
    private val content get() = w.content

    fun find(uid: String): ItemInstance? = p.inventory.firstOrNull { it.uid == uid }

    fun burden(): Float {
        var total = 0f
        for (it in p.inventory) total += weightOf(it) * it.count
        return total
    }

    fun weightOf(it: ItemInstance): Float = it.forged?.stats?.mass ?: content.items[it.defId]?.weight ?: 0.5f

    fun equippedItem(slot: String): ItemInstance? {
        val uid = p.equipped[slot] ?: return null
        return find(uid)
    }

    /** The slot an item is worn in right now, or null. */
    fun wornIn(uid: String): String? = p.equipped.entries.firstOrNull { it.value == uid }?.key

    fun nameOf(it: ItemInstance): String = it.forged?.name ?: content.itemName(it.defId)

    /** What the item is worth before a trader's opinion of the player. */
    fun valueOf(it: ItemInstance, worn: Boolean): Int {
        val f = it.forged
        if (f != null) return (f.stats.damage * 5f * (if (worn) it.condition else 1f)).toInt()
        return content.items[it.defId]?.value ?: 1
    }

    fun weaponType(it: ItemInstance): String {
        val f = it.forged
        if (f != null) return Progression.forgedWeaponType(f.geometry.length)
        val def = content.items[it.defId] ?: return ""
        return if (def.slot == "main_hand") def.weaponType.ifEmpty { "one_handed" } else ""
    }

    fun count(defId: String): Int {
        val id = content.items.canonical(defId)
        var n = 0
        for (it in p.inventory) if (it.defId == id) n += it.count
        return n
    }

    fun addItem(defId: String, count: Int, forged: ForgedWeapon? = null) {
        add(w.save, content, defId, count, forged)
        w.packDirty = true
    }

    fun removeItem(it: ItemInstance, count: Int) {
        it.count -= count
        if (it.count <= 0) {
            p.inventory.remove(it)
            val slot = wornIn(it.uid)
            if (slot != null) {
                p.equipped.remove(slot)
                w.progression.recalc()
            }
        }
        w.packDirty = true
    }

    /** Removes up to [count] of an item kind across stacks. Returns how many were taken. */
    fun removeByDef(defId: String, count: Int): Int {
        val id = content.items.canonical(defId)
        var left = count
        for (it in p.inventory.filter { i -> i.defId == id }) {
            if (left <= 0) break
            val take = min(left, it.count)
            removeItem(it, take)
            left -= take
        }
        return count - left
    }

    fun useItem(uid: String) {
        val it = find(uid) ?: return
        val def = content.items[it.defId] ?: return
        when (def.type) {
            "food", "potion" -> {
                p.health = min(w.progression.maxHealth, p.health + def.restoreHealth)
                p.hunger = min(100f, p.hunger + def.restoreHunger)
                p.comfort = min(100f, p.comfort + def.restoreComfort)
                p.magic = min(w.progression.maxMagic, p.magic + def.restoreMagic)
                removeItem(it, 1)
                w.toast(if (def.type == "food") "You eat the ${def.name}." else "You drink the ${def.name}.")
            }
            "book" -> w.interaction.readBook(def)
            else -> toggleEquip(uid)
        }
    }

    /** Why this cannot be equipped yet, or an empty string. */
    fun blockedReason(def: ItemDef?): String {
        if (def == null) return ""
        for ((attr, need) in def.requirements) {
            val have = w.progression.attr(attr)
            if (have < need) return "Needs ${Progression.name(attr)} $need (you have $have)"
        }
        return ""
    }

    /** The slot this would go into if equipped now. */
    fun targetSlot(def: ItemDef): String = when (def.slot) {
        "trinket" -> if ("trinket_1" !in p.equipped) "trinket_1" else if ("trinket_2" !in p.equipped) "trinket_2" else "trinket_1"
        else -> def.slot
    }

    fun toggleEquip(uid: String) {
        val it = find(uid) ?: return
        val def = content.items[it.defId] ?: return
        if (def.slot.isEmpty()) return
        val worn = wornIn(uid)
        if (worn != null) {
            p.equipped.remove(worn)
            w.toast("${nameOf(it)} put away.")
        } else {
            val blocked = blockedReason(def)
            if (blocked.isNotEmpty()) {
                w.toast("$blocked.")
                return
            }
            val slot = targetSlot(def)
            if (slot == "main_hand" && weaponType(it) == "two_handed" && p.equipped.remove("off_hand") != null) {
                w.toast("${nameOf(it)} needs both hands.")
            }
            if (slot == "off_hand") {
                val main = equippedItem("main_hand")
                if (main != null && weaponType(main) == "two_handed") p.equipped.remove("main_hand")
            }
            p.equipped[slot] = uid
            w.toast("${nameOf(it)} equipped.")
        }
        w.progression.recalc()
        // Losing a bonus can leave other gear without the attribute it needs.
        dropUnmet()
    }

    private fun dropUnmet() {
        var changed = true
        while (changed) {
            changed = false
            for ((slot, uid) in p.equipped.entries.toList()) {
                val item = find(uid)
                if (item == null) {
                    p.equipped.remove(slot)
                    changed = true
                    continue
                }
                if (blockedReason(content.items[item.defId]).isNotEmpty()) {
                    p.equipped.remove(slot)
                    w.toast("${nameOf(item)} put away: you no longer meet what it asks.")
                    w.progression.recalc()
                    changed = true
                }
            }
        }
    }

    /** Protection from everything worn except the weapon. Worn-out pieces protect less. */
    fun armor(): Float {
        var a = 0f
        for ((slot, uid) in p.equipped) {
            if (slot == "main_hand") continue
            val item = find(uid) ?: continue
            val def = content.items[item.defId] ?: continue
            a += def.armor * (0.5f + 0.5f * item.condition)
        }
        return a
    }

    /** A blow landed: armour that can wear, wears. */
    fun wearArmor() {
        for ((slot, uid) in p.equipped) {
            if (slot == "main_hand") continue
            val item = find(uid) ?: continue
            val def = content.items[item.defId] ?: continue
            if (def.armor <= 0f || def.durability <= 0) continue
            item.condition = max(0.05f, item.condition - 1f / def.durability)
            w.packDirty = true
        }
    }

    /** Heavy and medium armour slow how quickly breath returns. */
    fun staminaRegenFactor(): Float {
        var f = 1f
        for ((_, uid) in p.equipped) {
            val item = find(uid) ?: continue
            when (content.items[item.defId]?.armorClass) {
                "heavy" -> f -= 0.06f
                "medium" -> f -= 0.03f
            }
        }
        return f.coerceAtLeast(0.4f)
    }

    fun drop(uid: String) {
        val it = find(uid) ?: return
        val name = nameOf(it)
        removeItem(it, it.count)
        w.toast("Dropped $name.")
    }

    companion object {
        /** slot id -> label, in the order the Pack screen shows them. */
        val SLOTS = listOf(
            "head" to "Head", "chest" to "Chest", "hands" to "Hands", "legs" to "Legs", "feet" to "Feet",
            "main_hand" to "Main hand", "off_hand" to "Off hand", "trinket_1" to "Trinket", "trinket_2" to "Trinket",
        )

        fun slotLabel(slot: String): String = when (slot) {
            "trinket" -> "Trinket"
            else -> SLOTS.firstOrNull { it.first == slot }?.second ?: ""
        }

        fun add(save: SaveGame, content: ContentRegistry, defId: String, count: Int, forged: ForgedWeapon? = null) {
            val id = content.items.canonical(defId)
            val def = content.items[id]
            val inv = save.player.inventory
            if (def != null && def.stackable && forged == null) {
                val existing = inv.firstOrNull { it.defId == id && it.forged == null }
                if (existing != null) {
                    existing.count += count
                    return
                }
                inv += ItemInstance("i${save.nextUid++}", id, count)
            } else {
                repeat(count) { inv += ItemInstance("i${save.nextUid++}", id, 1, 1f, forged) }
            }
        }
    }
}
