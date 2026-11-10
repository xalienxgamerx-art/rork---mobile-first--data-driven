package app.rork.p7buzvrzcw3frddi8y50ou.sim

import kotlinx.serialization.Serializable

/** Everything in this file is persistent instance state. It references definitions only by id. */
const val SAVE_VERSION = 2

@Serializable
data class BladeGeometry(
    /** cm */
    val length: Float = 82f,
    /** cm */
    val width: Float = 4.5f,
    /** mm */
    val thickness: Float = 6f,
    /** degrees of arc along the blade */
    val curvature: Float = 2f,
    /** 0 = no distal taper, 1 = needle point */
    val taper: Float = 0.5f,
    /** cm */
    val handle: Float = 18f,
    /** grams */
    val pommel: Float = 180f,
    /** degrees, inclusive edge angle */
    val edgeAngle: Float = 30f,
    val fuller: Boolean = false,
)

@Serializable
data class WeaponStats(
    val mass: Float = 1f,
    val balance: Float = 10f,
    val inertia: Float = 0.1f,
    val reach: Float = 1.6f,
    val handling: Float = 50f,
    val cutting: Float = 50f,
    val thrust: Float = 50f,
    val integrity: Float = 50f,
    val edgeRetention: Float = 50f,
    val damage: Float = 10f,
    val speed: Float = 1f,
)

@Serializable
data class ForgedWeapon(
    val name: String,
    val materialId: String,
    val geometry: BladeGeometry,
    val stats: WeaponStats,
    val quality: String,
)

@Serializable
data class ItemInstance(
    val uid: String,
    val defId: String,
    var count: Int = 1,
    var condition: Float = 1f,
    val forged: ForgedWeapon? = null,
)

/** How the player's character looks. Colours are hex strings; an empty skin means "the race's first tone". */
@Serializable
data class Appearance(
    var skin: String = "",
    var hair: String = "#3B291F",
    /** short, long or bald */
    var hairStyle: String = "short",
    var beard: Boolean = false,
    /** 0.9 slight .. 1.1 broad */
    var build: Float = 1f,
    var cloth: String = "#5E6B45",
)

@Serializable
data class PlayerState(
    var name: String = "Wanderer",
    var race: String = "human",
    var background: String = "",
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 122f,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var health: Float = 100f,
    var stamina: Float = 100f,
    var magic: Float = 100f,
    var hunger: Float = 70f,
    var fatigue: Float = 80f,
    var comfort: Float = 75f,
    var gold: Int = 20,
    var thirdPerson: Boolean = false,
    val inventory: MutableList<ItemInstance> = mutableListOf(),
    val equipped: MutableMap<String, String> = mutableMapOf(),
    /** Skill id -> rank with its fractional progress to the next rank, e.g. 12.4. */
    val skills: MutableMap<String, Float> = mutableMapOf(),
    /** Attribute id -> base value before equipment. Missing means 5. */
    val attributes: MutableMap<String, Int> = mutableMapOf(),
    var level: Int = 1,
    /** Progress towards the next level, measured in skill ranks gained. */
    var levelProgress: Float = 0f,
    var attributePoints: Int = 0,
    val appearance: Appearance = Appearance(),
)

@Serializable
data class StockEntry(val item: String, var count: Int)

@Serializable
data class NpcState(
    val id: String,
    var x: Float = 0f,
    var z: Float = 0f,
    var hunger: Float = 70f,
    var fatigue: Float = 70f,
    var social: Float = 60f,
    var gold: Int = 30,
    var affinity: Int = 35,
    var action: String = "",
    var activity: String = "Idle",
    var indoors: Boolean = false,
    var lastChat: Double = -1000.0,
    var lastRestockDay: Int = -1,
    val memories: MutableList<String> = mutableListOf(),
    val bonds: MutableMap<String, Int> = mutableMapOf(),
    val stock: MutableList<StockEntry> = mutableListOf(),
)

@Serializable
data class CreatureState(
    val uid: String,
    val defId: String,
    var x: Float = 0f,
    var z: Float = 0f,
    var health: Float = 30f,
    var respawnAt: Double = 0.0,
)

@Serializable
data class BestiaryEntry(var firstSeen: Double = 0.0, var kills: Int = 0)

@Serializable
data class RumourEntry(val id: String, val minute: Double = 0.0, val source: String = "")

@Serializable
data class WorldEvent(val minute: Double, val text: String)

@Serializable
data class SaveGame(
    val version: Int = SAVE_VERSION,
    var minutes: Double = 0.0,
    var weather: String = "clear",
    var nextUid: Long = 1,
    val player: PlayerState = PlayerState(),
    val npcs: MutableList<NpcState> = mutableListOf(),
    val creatures: MutableList<CreatureState> = mutableListOf(),
    /** resource node id -> game minute at which it grows back */
    val harvested: MutableMap<String, Double> = mutableMapOf(),
    val history: MutableList<WorldEvent> = mutableListOf(),
    val packs: List<String> = emptyList(),
    /** Facts the world remembers: dialogue choices made, things read, one-off rewards taken. */
    val flags: MutableSet<String> = mutableSetOf(),
    /** Object id -> game minute it was last opened or used. */
    val opened: MutableMap<String, Double> = mutableMapOf(),
    /** Place id -> game minute it was first found. */
    val discovered: MutableMap<String, Double> = mutableMapOf(),
    /** Creature id -> what the player knows of it. */
    val bestiary: MutableMap<String, BestiaryEntry> = mutableMapOf(),
    val rumours: MutableList<RumourEntry> = mutableListOf(),
    /** Seed this world was started with. Kept for reports. */
    val seed: Long = 0,
    /** Current state of the world's random sequence, so a reloaded world continues it. */
    var rngState: Long = 0,
)

/**
 * The world's only source of chance. Its state lives in the save, so loading a world and
 * replaying the same inputs gives the same rolls instead of fresh ones. (SplitMix64.)
 */
class WorldRng(private val save: SaveGame) {
    fun nextLong(): Long {
        save.rngState += -0x61c8864680b583ebL
        var z = save.rngState
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()
    fun nextInt(bound: Int): Int = if (bound <= 1) 0 else ((nextLong() ushr 33) % bound).toInt()
}

object Calendar {
    private val months = listOf(
        "Dawnmoon", "Thawing", "Seedfall", "Rainhand", "Greentide", "Midsummer",
        "Sunheight", "Goldleaf", "Harvestmoon", "Frostfall", "Duskwane", "Deepwinter",
    )
    private val seasons = listOf(
        "Winter", "Winter", "Spring", "Spring", "Spring", "Summer",
        "Summer", "Summer", "Autumn", "Autumn", "Autumn", "Winter",
    )

    /** A new life begins on the 4th of Frostfall at four in the afternoon. */
    const val START_MINUTES = (9 * 30 + 3) * 1440.0 + 16 * 60.0

    fun hour(minutes: Double): Float = ((minutes % 1440.0) / 60.0).toFloat()
    fun day(minutes: Double): Int = (minutes / 1440.0).toInt()

    fun partOfDay(h: Float): String = when {
        h < 5f -> "Night"
        h < 7f -> "Dawn"
        h < 12f -> "Morning"
        h < 14f -> "Midday"
        h < 17f -> "Afternoon"
        h < 20f -> "Dusk"
        else -> "Night"
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
        }
        return "$n$suffix"
    }

    fun dateLabel(minutes: Double): String {
        val d = day(minutes)
        val dayOfYear = ((d % 360) + 360) % 360
        return "${ordinal(dayOfYear % 30 + 1)} of ${months[dayOfYear / 30]}"
    }

    fun season(minutes: Double): String = seasons[(((day(minutes) % 360) + 360) % 360) / 30]

    fun label(minutes: Double): String = "${partOfDay(hour(minutes))}, ${dateLabel(minutes)}"

    fun clock(minutes: Double): String {
        val h = hour(minutes)
        val hh = h.toInt()
        val mm = ((h - hh) * 60).toInt()
        return "%02d:%02d".format(hh, mm)
    }
}
