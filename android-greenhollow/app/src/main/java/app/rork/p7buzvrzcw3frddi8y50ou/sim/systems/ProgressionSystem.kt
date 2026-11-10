package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World

/** Attributes, skills and levels: who the character is becoming. */
class ProgressionSystem(private val w: World) {
    var maxHealth = 100f
        private set
    var maxStamina = 100f
        private set
    var maxMagic = 100f
        private set
    var capacity = 60f
        private set

    private val p get() = w.p

    fun base(id: String): Int = p.attributes[id] ?: Progression.BASE_ATTRIBUTE

    /** What worn equipment adds to an attribute. */
    fun bonus(id: String): Int {
        var total = 0
        for (uid in p.equipped.values) {
            val item = p.inventory.firstOrNull { it.uid == uid } ?: continue
            total += w.content.items[item.defId]?.bonuses?.get(id) ?: 0
        }
        return total
    }

    fun attr(id: String): Int = base(id) + bonus(id)

    /** Recomputes everything derived from attributes. Call after attributes or equipment change. */
    fun recalc() {
        val race = w.content.races[p.race] ?: w.content.races.values.firstOrNull()
        val end = attr("endurance")
        maxHealth = Progression.maxHealth(race?.healthBonus ?: 0f, end)
        maxStamina = Progression.maxStamina(race?.staminaBonus ?: 0f, end, attr("agility"))
        maxMagic = Progression.maxMagic(race?.magicBonus ?: 0f, attr("intellect"), attr("willpower"))
        capacity = Progression.capacity(attr("strength"))
        p.health = p.health.coerceAtMost(maxHealth)
        p.stamina = p.stamina.coerceAtMost(maxStamina)
        p.magic = p.magic.coerceAtMost(maxMagic)
        w.packDirty = true
        w.journalDirty = true
    }

    /** Rank with fractional progress, e.g. 12.4. */
    fun skill(id: String): Float = p.skills[id] ?: 0f

    /** Practice makes a skill grow. Each whole rank gained also counts towards the next level. */
    fun gainXp(id: String, xp: Float) {
        if (xp <= 0f) return
        val before = skill(id)
        val after = Progression.addXp(before, xp)
        if (after == before) return
        p.skills[id] = after
        val ranks = after.toInt() - before.toInt()
        if (ranks > 0) {
            w.toast("${Progression.name(id)} rises to ${after.toInt()}.")
            addLevelProgress(ranks.toFloat())
            w.packDirty = true
        }
        w.journalDirty = true
    }

    /** Skill ranks, discoveries and rumours all feed the same level bar. */
    fun addLevelProgress(amount: Float) {
        p.levelProgress += amount
        while (p.levelProgress >= Progression.levelNeed(p.level)) {
            p.levelProgress -= Progression.levelNeed(p.level)
            p.level++
            p.attributePoints++
            w.toast("You reach level ${p.level}. An attribute point is yours to spend in the Journal.")
            w.record("${p.name} reached level ${p.level}.")
        }
        w.journalDirty = true
    }

    fun spend(attribute: String) {
        if (attribute !in Progression.ATTRIBUTES || p.attributePoints <= 0) return
        if (base(attribute) >= Progression.MAX_ATTRIBUTE) {
            w.toast("${Progression.name(attribute)} cannot go any higher.")
            return
        }
        val healthBefore = maxHealth
        p.attributes[attribute] = base(attribute) + 1
        p.attributePoints--
        recalc()
        // New health arrives filled, so spending a point never leaves a gap in the bar.
        if (maxHealth > healthBefore) p.health = (p.health + (maxHealth - healthBefore)).coerceAtMost(maxHealth)
    }
}
