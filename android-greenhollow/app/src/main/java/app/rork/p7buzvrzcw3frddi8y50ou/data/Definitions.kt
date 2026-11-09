package app.rork.p7buzvrzcw3frddi8y50ou.data

import kotlinx.serialization.Serializable

/**
 * Universal content definition format. Every pack (the core game and every mod) is one JSON
 * document shaped like [ContentPack]. Definitions are immutable templates; runtime instances
 * live in the save game and only reference definitions by stable string id.
 */
const val SCHEMA_VERSION = 1

@Serializable
data class ModManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val schemaVersion: Int = SCHEMA_VERSION,
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
)

@Serializable
data class BackgroundDef(
    val id: String,
    val name: String,
    val description: String = "",
    val items: List<StackDef> = emptyList(),
    val skills: Map<String, Float> = emptyMap(),
    val gold: Int = 20,
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
)
