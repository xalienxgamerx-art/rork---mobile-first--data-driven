package app.rork.p7buzvrzcw3frddi8y50ou.sim

import kotlin.math.floor

/**
 * The rules of character growth, as plain functions of numbers so they can be tested on their
 * own. An attribute of [BASE_ATTRIBUTE] is the average person and changes nothing; every
 * formula is written as a shift away from that.
 */
object Progression {
    val ATTRIBUTES = listOf("strength", "agility", "endurance", "intellect", "willpower", "charm")
    const val BASE_ATTRIBUTE = 5
    const val STARTING_POINTS = 4
    const val MAX_ATTRIBUTE = 20
    const val MAX_SKILL = 100f

    val SKILLS = listOf("blades", "blocking", "smithing", "foraging", "hunting", "trading", "speech", "restoration", "athletics")

    fun name(id: String): String = id.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun effect(attribute: String): String = when (attribute) {
        "strength" -> "Harder blows and a heavier pack"
        "agility" -> "Faster swings, more stamina, deadlier daggers"
        "endurance" -> "More health and stamina"
        "intellect" -> "A deeper well of magic"
        "willpower" -> "Magic returns sooner"
        "charm" -> "Better prices, quicker friendships"
        else -> ""
    }

    /** Experience needed to go from [rank] to the next one. Later ranks take longer. */
    fun xpForRank(rank: Int): Float = 6f + rank * 0.8f

    /** Skill ranks (or their worth in discoveries) needed to go from [level] to the next. */
    fun levelNeed(level: Int): Float = 4f + level

    fun maxHealth(raceBonus: Float, endurance: Int): Float = (100f + raceBonus + (endurance - BASE_ATTRIBUTE) * 8f).coerceAtLeast(20f)

    fun maxStamina(raceBonus: Float, endurance: Int, agility: Int): Float =
        (100f + raceBonus + (endurance - BASE_ATTRIBUTE) * 4f + (agility - BASE_ATTRIBUTE) * 4f).coerceAtLeast(20f)

    fun maxMagic(raceBonus: Float, intellect: Int, willpower: Int): Float =
        (100f + raceBonus + (intellect - BASE_ATTRIBUTE) * 8f + (willpower - BASE_ATTRIBUTE) * 2f).coerceAtLeast(10f)

    fun capacity(strength: Int): Float = (60f + (strength - BASE_ATTRIBUTE) * 6f).coerceAtLeast(20f)

    /** Damage multiplier from the attribute a weapon leans on (Strength, or Agility for daggers). */
    fun meleeFactor(attribute: Int): Float = (1f + (attribute - BASE_ATTRIBUTE) * 0.04f).coerceAtLeast(0.5f)

    fun attackSpeedFactor(agility: Int): Float = (1f + (agility - BASE_ATTRIBUTE) * 0.02f).coerceAtLeast(0.6f)

    fun magicRegenFactor(willpower: Int): Float = (1f + (willpower - BASE_ATTRIBUTE) * 0.08f).coerceAtLeast(0.3f)

    /** Fraction by which prices move in the player's favour. */
    fun priceShift(charm: Int, trading: Float): Float = ((charm - BASE_ATTRIBUTE) * 0.015f + trading * 0.002f).coerceIn(-0.3f, 0.4f)

    /** Extra affinity each time someone warms to the player. */
    fun affinityBonus(charm: Int): Int = floor((charm - BASE_ATTRIBUTE) / 3f).toInt()

    /**
     * Adds [xp] to a skill stored as rank plus fraction. Returns the new value; the number of
     * whole ranks gained is the difference of the integer parts.
     */
    fun addXp(value: Float, xp: Float): Float {
        var v = value
        var left = xp
        while (left > 0f && v < MAX_SKILL) {
            val rank = v.toInt()
            val need = xpForRank(rank)
            val room = (rank + 1 - v) * need
            if (left >= room) {
                v = rank + 1f
                left -= room
            } else {
                v += left / need
                left = 0f
            }
        }
        return v.coerceAtMost(MAX_SKILL)
    }

    /** Sword class of a blade forged to [lengthCm]. */
    fun forgedWeaponType(lengthCm: Float): String = when {
        lengthCm > 105f -> "two_handed"
        lengthCm < 45f -> "dagger"
        else -> "one_handed"
    }
}
