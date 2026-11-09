package app.rork.p7buzvrzcw3frddi8y50ou.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

data class PackInfo(
    val manifest: ModManifest,
    val builtin: Boolean,
    val enabled: Boolean,
    val loaded: Boolean,
    val definitionCount: Int,
)

data class ContentIssue(val severity: String, val packId: String, val message: String)

/** The merged, validated view of every enabled content pack. Engine code only ever reads this. */
class ContentRegistry(
    val materials: Map<String, MaterialDef>,
    val items: Map<String, ItemDef>,
    val races: Map<String, RaceDef>,
    val backgrounds: Map<String, BackgroundDef>,
    val professions: Map<String, ProfessionDef>,
    val npcs: Map<String, NpcDef>,
    val creatures: Map<String, CreatureDef>,
    val buildings: Map<String, BuildingDef>,
    val locations: Map<String, LocationDef>,
    val spots: Map<String, SpotDef>,
    val resources: Map<String, ResourceDef>,
    val packs: List<PackInfo>,
    val issues: List<ContentIssue>,
) {
    fun itemName(id: String): String = items[id]?.name ?: "Missing: $id"

    /** Resolves a workplace reference (building id, building type or spot id) to a building or spot. */
    fun buildingFor(ref: String): BuildingDef? =
        buildings[ref] ?: buildings.values.firstOrNull { it.type == ref }
}

fun parseHex(hex: String, fallback: Int = 0xFF888888.toInt()): Int = try {
    android.graphics.Color.parseColor(hex)
} catch (_: Exception) {
    fallback
}

fun hexToRgb(hex: String): FloatArray {
    val c = parseHex(hex)
    return floatArrayOf(
        ((c shr 16) and 0xFF) / 255f,
        ((c shr 8) and 0xFF) / 255f,
        (c and 0xFF) / 255f,
    )
}

object ContentLoader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val ASSET_DIR = "content"

    private fun modDir(context: Context) = File(context.filesDir, "mods").apply { mkdirs() }

    private class Loaded(val pack: ContentPack, val builtin: Boolean)

    fun load(context: Context, disabled: Set<String>): ContentRegistry {
        val issues = ArrayList<ContentIssue>()
        val found = ArrayList<Loaded>()

        context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".json") }.forEach { name ->
            try {
                val text = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
                found += Loaded(json.decodeFromString<ContentPack>(text), true)
            } catch (e: Exception) {
                issues += ContentIssue("error", name, "Could not read pack: ${e.message?.take(140)}")
            }
        }
        modDir(context).listFiles().orEmpty().filter { it.name.endsWith(".json") }.forEach { file ->
            try {
                val pack = json.decodeFromString<ContentPack>(file.readText())
                if (found.any { it.pack.manifest.id == pack.manifest.id }) {
                    issues += ContentIssue("warning", pack.manifest.id, "Duplicate pack id, the imported copy was ignored.")
                } else {
                    found += Loaded(pack, false)
                }
            } catch (e: Exception) {
                issues += ContentIssue("error", file.name, "Could not read pack: ${e.message?.take(140)}")
            }
        }

        found.sortWith(compareBy({ it.pack.manifest.loadOrder }, { it.pack.manifest.id }))

        val materials = LinkedHashMap<String, MaterialDef>()
        val items = LinkedHashMap<String, ItemDef>()
        val races = LinkedHashMap<String, RaceDef>()
        val backgrounds = LinkedHashMap<String, BackgroundDef>()
        val professions = LinkedHashMap<String, ProfessionDef>()
        val npcs = LinkedHashMap<String, NpcDef>()
        val creatures = LinkedHashMap<String, CreatureDef>()
        val buildings = LinkedHashMap<String, BuildingDef>()
        val locations = LinkedHashMap<String, LocationDef>()
        val spots = LinkedHashMap<String, SpotDef>()
        val resources = LinkedHashMap<String, ResourceDef>()
        val population = ArrayList<PopulationDef>()
        val origin = HashMap<String, String>()
        val loadedIds = HashSet<String>()
        val packs = ArrayList<PackInfo>()

        for (l in found) {
            val m = l.pack.manifest
            val p = l.pack
            val count = p.materials.size + p.items.size + p.races.size + p.backgrounds.size + p.professions.size +
                p.npcs.size + p.creatures.size + p.buildings.size + p.locations.size + p.spots.size + p.resources.size
            val enabled = m.id == "core" || m.id !in disabled
            var ok = enabled
            if (enabled && m.schemaVersion > SCHEMA_VERSION) {
                issues += ContentIssue("error", m.id, "Needs schema ${m.schemaVersion}, this game supports $SCHEMA_VERSION.")
                ok = false
            }
            if (ok) {
                val missing = m.dependencies.filter { it !in loadedIds }
                if (missing.isNotEmpty()) {
                    issues += ContentIssue("error", m.id, "Missing dependency: ${missing.joinToString()}. Pack not loaded.")
                    ok = false
                }
            }
            packs += PackInfo(m, l.builtin, enabled, ok, count)
            if (!ok) continue
            loadedIds += m.id

            fun <T> register(kind: String, target: MutableMap<String, T>, defs: List<T>, idOf: (T) -> String) {
                for (d in defs) {
                    val id = idOf(d)
                    if (id.isBlank()) {
                        issues += ContentIssue("warning", m.id, "A $kind definition has no id and was skipped.")
                        continue
                    }
                    val key = "$kind:$id"
                    val prev = origin[key]
                    if (prev != null) {
                        val sev = if (prev == "core") "info" else "warning"
                        issues += ContentIssue(sev, m.id, "Overrides $kind '$id' from pack '$prev'.")
                    }
                    origin[key] = m.id
                    target[id] = d
                }
            }
            register("material", materials, p.materials) { it.id }
            register("item", items, p.items) { it.id }
            register("race", races, p.races) { it.id }
            register("background", backgrounds, p.backgrounds) { it.id }
            register("profession", professions, p.professions) { it.id }
            register("npc", npcs, p.npcs) { it.id }
            register("creature", creatures, p.creatures) { it.id }
            register("building", buildings, p.buildings) { it.id }
            register("location", locations, p.locations) { it.id }
            register("spot", spots, p.spots) { it.id }
            register("resource", resources, p.resources) { it.id }
            population += p.population
        }

        // Cross-reference resolution and missing-reference detection.
        fun pk(kind: String, id: String) = origin["$kind:$id"] ?: "core"
        fun workplaceOk(ref: String) =
            ref.isEmpty() || ref in buildings || ref in spots || buildings.values.any { it.type == ref }

        materials.values.filter { it.itemId !in items }.forEach {
            issues += ContentIssue("warning", pk("material", it.id), "Material '${it.id}' needs missing item '${it.itemId}' and was removed.")
            materials.remove(it.id)
        }
        professions.values.toList().forEach { prof ->
            val bad = prof.goods.filter { it.item !in items }
            if (bad.isNotEmpty() || !workplaceOk(prof.workplace)) {
                if (bad.isNotEmpty()) issues += ContentIssue("warning", pk("profession", prof.id), "Profession '${prof.id}' sells missing items: ${bad.joinToString { it.item }}.")
                if (!workplaceOk(prof.workplace)) issues += ContentIssue("warning", pk("profession", prof.id), "Profession '${prof.id}' has unknown workplace '${prof.workplace}'.")
                professions[prof.id] = prof.copy(
                    goods = prof.goods.filter { it.item in items },
                    workplace = if (workplaceOk(prof.workplace)) prof.workplace else "",
                )
            }
        }
        backgrounds.values.toList().forEach { bg ->
            val bad = bg.items.filter { it.item !in items }
            if (bad.isNotEmpty()) {
                issues += ContentIssue("warning", pk("background", bg.id), "Background '${bg.id}' grants missing items: ${bad.joinToString { it.item }}.")
                backgrounds[bg.id] = bg.copy(items = bg.items.filter { it.item in items })
            }
        }
        npcs.values.toList().forEach { n ->
            val problems = buildList {
                if (n.race !in races) add("race '${n.race}'")
                if (n.profession !in professions) add("profession '${n.profession}'")
                if (n.home !in buildings) add("home '${n.home}'")
                if (!workplaceOk(n.workplace)) add("workplace '${n.workplace}'")
            }
            if (problems.isNotEmpty()) {
                issues += ContentIssue("warning", pk("npc", n.id), "NPC '${n.id}' references missing ${problems.joinToString()} and was removed.")
                npcs.remove(n.id)
            }
        }
        creatures.values.toList().forEach { c ->
            if (c.territory !in locations) {
                issues += ContentIssue("warning", pk("creature", c.id), "Creature '${c.id}' has unknown territory '${c.territory}' and was removed.")
                creatures.remove(c.id)
            } else {
                val bad = c.loot.filter { it.item !in items }
                if (bad.isNotEmpty()) {
                    issues += ContentIssue("warning", pk("creature", c.id), "Creature '${c.id}' drops missing items: ${bad.joinToString { it.item }}.")
                    creatures[c.id] = c.copy(loot = c.loot.filter { it.item in items })
                }
            }
        }
        resources.values.toList().forEach { r ->
            if (r.item !in items) {
                issues += ContentIssue("warning", pk("resource", r.id), "Resource '${r.id}' yields missing item '${r.item}' and was removed.")
                resources.remove(r.id)
            }
        }

        generatePopulation(population, professions, races, buildings, npcs)

        return ContentRegistry(
            materials, items, races, backgrounds, professions, npcs, creatures,
            buildings, locations, spots, resources, packs, issues,
        )
    }

    private val givenNames = listOf(
        "Aldric", "Berta", "Corin", "Dagny", "Edda", "Fenn", "Greta", "Halvar", "Ingrid", "Joss",
        "Kara", "Leof", "Maren", "Nils", "Odette", "Perrin", "Runa", "Soren", "Tilda", "Ulf", "Wren", "Yorick",
    )
    private val hairTones = listOf("#3A2A1E", "#6B3B24", "#A8703A", "#1F1A16", "#8A8378", "#C9A45A")

    /** Fills the settlement with ordinary townsfolk from profession templates, deterministically. */
    private fun generatePopulation(
        population: List<PopulationDef>,
        professions: Map<String, ProfessionDef>,
        races: Map<String, RaceDef>,
        buildings: Map<String, BuildingDef>,
        npcs: MutableMap<String, NpcDef>,
    ) {
        val houses = buildings.values.filter { it.type == "house" }
        if (houses.isEmpty()) return
        var index = 0
        for (pop in population) {
            if (pop.profession !in professions) continue
            val race = races[pop.race] ?: races.values.firstOrNull() ?: continue
            repeat(pop.count) { i ->
                val rnd = Random(index * 7919 + 17)
                val id = "pop_${pop.profession}_${i + 1}"
                if (id !in npcs) {
                    fun trait() = 0.2f + rnd.nextFloat() * 0.7f
                    npcs[id] = NpcDef(
                        id = id,
                        name = givenNames[index % givenNames.size],
                        race = race.id,
                        profession = pop.profession,
                        age = 18 + rnd.nextInt(45),
                        home = houses[index % houses.size].id,
                        personality = mapOf(
                            "ambition" to trait(), "courage" to trait(), "sociability" to trait(),
                            "generosity" to trait(), "greed" to trait(), "aggression" to trait() * 0.6f,
                            "curiosity" to trait(), "loyalty" to trait(),
                        ),
                        skin = race.skinTones[rnd.nextInt(race.skinTones.size)],
                        hair = hairTones[rnd.nextInt(hairTones.size)],
                        beard = rnd.nextFloat() < 0.35f,
                        longHair = rnd.nextFloat() < 0.45f,
                        affinity = 28 + rnd.nextInt(18),
                        gold = 10 + rnd.nextInt(30),
                    )
                }
                index++
            }
        }
    }

    /** Copies a pack file picked by the player into app storage after checking it parses. */
    fun importPack(context: Context, uri: Uri): Result<ModManifest> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("The file could not be opened.")
        val pack = json.decodeFromString<ContentPack>(text)
        val id = pack.manifest.id
        require(id.matches(Regex("[a-z0-9_\\-]{2,40}"))) { "Pack id must be lowercase letters, digits, - or _." }
        require(id != "core") { "A pack cannot replace the core game." }
        File(modDir(context), "$id.json").writeText(text)
        pack.manifest
    }

    fun removePack(context: Context, id: String) {
        File(modDir(context), "$id.json").delete()
    }
}
