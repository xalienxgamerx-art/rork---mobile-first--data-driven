package app.rork.p7buzvrzcw3frddi8y50ou.data

import kotlinx.serialization.Serializable

/**
 * Universal content definition format. Every pack (the core game and every mod) is one JSON
 * document shaped like [ContentPack]. Definitions are immutable templates; runtime instances
 * live in the save game and only reference definitions by stable string id.
 */
const val SCHEMA_VERSION = 2

/** Oldest pack format this game still reads. Schema 1 packs use bare ids and the old slot names. */
const val MIN_SCHEMA_VERSION = 1

@Serializable
data class ModManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val schemaVersion: Int = SCHEMA_VERSION,
    /** Pack ids, optionally with a minimum version: "core" or "core>=0.2.0". */
    val dependencies: List<String> = emptyList(),
    val loadOrder: Int = 100,
)

@Serializable
data class MaterialDef(
    val id: String,
    val name: String,
    val category: String = "metal",
    /** g/cm3 */
    val density: Float = 7.8f,
    val hardness: Float = 50f,
    val toughness: Float = 50f,
    val flexibility: Float = 50f,
    val workability: Float = 50f,
    val heatResistance: Float = 50f,
    val edgeRetention: Float = 50f,
    /** Ideal working temperature in Celsius. */
    val forgeTemp: Float = 1000f,
    val color: String = "#9A9A9A",
    /** Inventory item consumed when forging with this material. */
    val itemId: String = "",
)

@Serializable
data class ItemDef(
    val id: String,
    val name: String,
    val type: String = "misc",
    val slot: String = "",
    val weight: Float = 0.5f,
    val value: Int = 1,
    val stackable: Boolean = true,
    val icon: String = "misc",
    val description: String = "",
    val damage: Float = 0f,
    val reach: Float = 1.6f,
    val armor: Float = 0f,
    val restoreHealth: Float = 0f,
    val restoreHunger: Float = 0f,
    val restoreComfort: Float = 0f,
    val restoreMagic: Float = 0f,
    /** one_handed, two_handed or dagger. Empty for anything that is not a weapon. */
    val weaponType: String = "",
    /** Swings per second relative to an ordinary sword. */
    val speed: Float = 1f,
    /** light, medium or heavy. Empty for anything that is not armour. */
    val armorClass: String = "",
    /** Attribute id -> minimum value needed to equip. */
    val requirements: Map<String, Int> = emptyMap(),
    /** Attribute id -> bonus while equipped. */
    val bonuses: Map<String, Int> = emptyMap(),
    /** Uses (swings or blows taken) before the item is worn out. 0 means it never wears. */
    val durability: Int = 0,
    /** Long text shown when a book is read. Falls back to the description. */
    val text: String = "",
    /** Rumour learned by reading this. */
    val rumour: String = "",
)

@Serializable
data class RaceDef(
    val id: String,
    val name: String,
    val description: String = "",
    val healthBonus: Float = 0f,
    val staminaBonus: Float = 0f,
    val magicBonus: Float = 0f,
    val skinTones: List<String> = listOf("#D9A77F"),
    /** Attribute id -> starting adjustment. */
    val attributes: Map<String, Int> = emptyMap(),
)

@Serializable
data class BackgroundDef(
    val id: String,
    val name: String,
    val description: String = "",
    val items: List<StackDef> = emptyList(),
    val skills: Map<String, Float> = emptyMap(),
    val gold: Int = 20,
    /** Item ids from [items] that start equipped. */
    val equip: List<String> = emptyList(),
)

@Serializable
data class StackDef(val item: String, val count: Int = 1)

@Serializable
data class ProfessionDef(
    val id: String,
    val name: String,
    /** A building type ("smithy"), a building id, or a spot id. */
    val workplace: String = "",
    val workStart: Int = 8,
    val workEnd: Int = 17,
    val wage: Int = 4,
    val activity: String = "Working",
    val trader: Boolean = false,
    val goods: List<StackDef> = emptyList(),
    val cloth: String = "#7A6548",
    val hat: Boolean = false,
)

@Serializable
data class NpcDef(
    val id: String,
    val name: String,
    val race: String = "human",
    val profession: String = "villager",
    val age: Int = 30,
    val home: String = "",
    val workplace: String = "",
    /** 0..1 traits: ambition, courage, sociability, generosity, greed, aggression, curiosity, loyalty. */
    val personality: Map<String, Float> = emptyMap(),
    val skin: String = "",
    val hair: String = "#4A3222",
    val cloth: String = "",
    val hat: Boolean? = null,
    val beard: Boolean = false,
    val longHair: Boolean = false,
    /** friend, rival, family or stranger: the starting bond with the player. */
    val relation: String = "stranger",
    val affinity: Int = 35,
    val gold: Int = 30,
    val greeting: String = "",
    val likes: String = "",
    val fears: String = "",
)

@Serializable
data class PopulationDef(val profession: String, val count: Int = 1, val race: String = "human")

@Serializable
data class LootDef(val item: String, val chance: Float = 1f, val count: Int = 1)

@Serializable
data class CreatureDef(
    val id: String,
    val name: String,
    val health: Float = 30f,
    val damage: Float = 5f,
    val speed: Float = 3f,
    val aggressive: Boolean = false,
    val color: String = "#6B6257",
    val size: Float = 1f,
    val loot: List<LootDef> = emptyList(),
    val territory: String = "",
    val count: Int = 2,
    val description: String = "",
)

@Serializable
data class BuildingDef(
    val id: String,
    val name: String,
    val type: String = "house",
    val x: Float,
    val z: Float,
    val w: Float = 9f,
    val d: Float = 8f,
    val h: Float = 4f,
    val wall: String = "#C9B48A",
    val roof: String = "#6B3F2A",
    val interact: String = "",
)

@Serializable
data class LocationDef(
    val id: String,
    val name: String,
    val x: Float,
    val z: Float,
    val radius: Float = 40f,
    val description: String = "",
)

/** A named outdoor work place. gate is "north", "south" or empty when it lies inside the village. */
@Serializable
data class SpotDef(
    val id: String,
    val name: String,
    val x: Float,
    val z: Float,
    val gate: String = "",
)

@Serializable
data class ResourceDef(
    val id: String,
    val name: String,
    val item: String,
    val kind: String = "herb",
    val x: Float,
    val z: Float,
    val count: Int = 1,
    val respawnHours: Int = 24,
)

/**
 * Anything in the world the player can walk up to and use. Behaviour comes from the data, not
 * from [kind]: a pack can invent a new kind ("shrine", "milestone") and give it any mix of text,
 * contents, rest, restoration, a rumour and skill experience. [kind] only picks the default
 * prompt and how the object is drawn (container, sign, book, bed; anything else is a marker stone).
 */
@Serializable
data class InteractableDef(
    val id: String,
    val name: String,
    val kind: String = "sign",
    val x: Float,
    val z: Float,
    /** Overrides the default prompt, e.g. "Pray at the shrine". */
    val prompt: String = "",
    /** Shown in a reading sheet when used. */
    val text: String = "",
    /** Taken once, or again after [respawnHours] when that is above zero. */
    val loot: List<LootDef> = emptyList(),
    val respawnHours: Int = 0,
    /** Sleep here until dawn. */
    val sleep: Boolean = false,
    /** Gold charged for each use. */
    val cost: Int = 0,
    /** NPC who owns this. Use needs [accessAffinity] with them. Empty means anyone may use it. */
    val owner: String = "",
    val accessAffinity: Int = 50,
    /** health, magic, stamina, hunger, comfort -> amount restored on use. */
    val restore: Map<String, Float> = emptyMap(),
    val rumour: String = "",
    /** Skill id -> experience granted the first time this is used. */
    val skillXp: Map<String, Float> = emptyMap(),
    /** Game hours before [restore] works again. */
    val cooldownHours: Int = 0,
)

@Serializable
data class RumourDef(
    val id: String,
    val title: String,
    val text: String = "",
    /** Location the rumour points at, if any. */
    val location: String = "",
)

/**
 * A check on the world. Types: affinity (with the speaker), skill, attribute, level, hour,
 * gold, item (count carried), flag, memory (the speaker remembers text containing [key]),
 * rumour (known), place (discovered), relation (friend, rival, family, stranger).
 * Numeric checks pass when min <= value <= max. Set [negate] to invert.
 */
@Serializable
data class ConditionDef(
    val type: String,
    val key: String = "",
    val min: Float? = null,
    val max: Float? = null,
    val negate: Boolean = false,
)

/**
 * A consequence of a reply. Types: affinity (amount), rumour (key), give_item / take_item
 * (key, amount), gold (amount, may be negative), trade, flag (key), memory (text),
 * skill_xp (key, amount), small_talk, end.
 */
@Serializable
data class OutcomeDef(
    val type: String,
    val key: String = "",
    val amount: Float = 0f,
    val text: String = "",
)

@Serializable
data class ReplyDef(
    val text: String,
    val conditions: List<ConditionDef> = emptyList(),
    val outcomes: List<OutcomeDef> = emptyList(),
    /** Node to move to. Empty returns to the dialogue's first node. */
    val goto: String = "",
    /** Offered only until it has been chosen. */
    val once: Boolean = false,
    /** Spoken in answer before moving on. Empty uses the target node's text. */
    val answer: String = "",
)

/** One alternative wording of a node, used when all its conditions hold. */
@Serializable
data class LineDef(val text: String, val conditions: List<ConditionDef> = emptyList())

@Serializable
data class DialogueNodeDef(
    val id: String,
    val text: String = "",
    /** Checked in order before [text]; the first match is spoken. */
    val lines: List<LineDef> = emptyList(),
    val replies: List<ReplyDef> = emptyList(),
)

/** A branching conversation. Attached to one NPC, or to everyone of a profession. */
@Serializable
data class DialogueDef(
    val id: String,
    val npc: String = "",
    val profession: String = "",
    /** The first node is where every conversation opens. */
    val nodes: List<DialogueNodeDef> = emptyList(),
)

@Serializable
data class ContentPack(
    val manifest: ModManifest,
    val materials: List<MaterialDef> = emptyList(),
    val items: List<ItemDef> = emptyList(),
    val races: List<RaceDef> = emptyList(),
    val backgrounds: List<BackgroundDef> = emptyList(),
    val professions: List<ProfessionDef> = emptyList(),
    val npcs: List<NpcDef> = emptyList(),
    val population: List<PopulationDef> = emptyList(),
    val creatures: List<CreatureDef> = emptyList(),
    val buildings: List<BuildingDef> = emptyList(),
    val locations: List<LocationDef> = emptyList(),
    val spots: List<SpotDef> = emptyList(),
    val resources: List<ResourceDef> = emptyList(),
    val interactables: List<InteractableDef> = emptyList(),
    val dialogues: List<DialogueDef> = emptyList(),
    val rumours: List<RumourDef> = emptyList(),
)
