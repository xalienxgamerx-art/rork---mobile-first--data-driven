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

/** One problem found while loading content, worded for the player. [entry] names the definition. */
data class ContentIssue(val severity: String, val packId: String, val message: String, val entry: String = "")

/**
 * Definitions of one kind, keyed by canonical id.
 *
 * Core definitions keep their short id ("iron"). Every other pack's definitions are namespaced
 * ("emberfall:emberglass"). Lookups also accept "core:iron" and, when it is unambiguous, the
 * short form of a namespaced id, so saves and packs written before namespacing keep working.
 */
class Defs<T>(private val map: Map<String, T>, private val aliases: Map<String, String>) : Map<String, T> by map {
    override fun get(key: String): T? = map[key] ?: map[canonical(key)]
    override fun containsKey(key: String): Boolean = map.containsKey(canonical(key))

    fun canonical(id: String): String = when {
        map.containsKey(id) -> id
        id.startsWith("core:") -> id.substring(5)
        else -> aliases[id] ?: id
    }

    companion object {
        fun <T> empty() = Defs<T>(emptyMap(), emptyMap())
    }
}

/** The part of an id after its pack prefix. */
fun shortId(id: String): String = id.substringAfter(':')

/** The merged, validated view of every enabled content pack. Engine code only ever reads this. */
class ContentRegistry(
    val materials: Defs<MaterialDef>,
    val items: Defs<ItemDef>,
    val races: Defs<RaceDef>,
    val backgrounds: Defs<BackgroundDef>,
    val professions: Defs<ProfessionDef>,
    val npcs: Defs<NpcDef>,
    val creatures: Defs<CreatureDef>,
    val buildings: Defs<BuildingDef>,
    val locations: Defs<LocationDef>,
    val spots: Defs<SpotDef>,
    val resources: Defs<ResourceDef>,
    val interactables: Defs<InteractableDef>,
    val dialogues: Defs<DialogueDef>,
    val rumours: Defs<RumourDef>,
    val packs: List<PackInfo>,
    val issues: List<ContentIssue>,
) {
    fun itemName(id: String): String = items[id]?.name ?: "Missing: $id"

    /** Resolves a workplace reference (building id, building type or spot id) to a building. */
    fun buildingFor(ref: String): BuildingDef? =
        buildings[ref] ?: buildings.values.firstOrNull { it.type == ref }

    /** The conversation written for this person, or failing that for their trade. */
    fun dialogueFor(npcId: String, professionId: String): DialogueDef? =
        dialogues.values.lastOrNull { it.npc == npcId && it.nodes.isNotEmpty() }
            ?: dialogues.values.lastOrNull { it.npc.isEmpty() && it.profession == professionId && it.nodes.isNotEmpty() }
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

/** A pack as read from storage, before it is merged. */
class PackSource(val pack: ContentPack, val builtin: Boolean)

/**
 * Merges packs into a [ContentRegistry]. Pure: no files, no Android, so it can be unit tested.
 *
 * Rules, in order:
 *  - Packs load by loadOrder, then id. "core" cannot be disabled.
 *  - A pack is skipped (error) when its schema is outside MIN_SCHEMA_VERSION..SCHEMA_VERSION, or
 *    when a dependency is missing, disabled, failed to load, or older than the version it asks for.
 *  - A definition with a bare id belongs to its own pack ("emberfall:vesna").
 *  - To replace another pack's definition, a pack names it in full ("core:iron"). The last pack
 *    in load order wins. Replacing something from a pack that is not a declared dependency warns.
 *  - Schema 1 packs keep the old behaviour, where a bare id that matches an existing definition
 *    replaces it. This is reported as a warning so the pack can be updated.
 *  - References are resolved own pack first, then core, then any pack if the short id is unique.
 *  - A definition whose required references are missing is removed with a warning; optional
 *    parts (one item in a loot list) are dropped on their own.
 */
object ContentBuilder {
    val ITEM_SLOTS = setOf("head", "chest", "hands", "legs", "feet", "main_hand", "off_hand", "trinket")
    private val LEGACY_SLOTS = mapOf("weapon" to "main_hand", "shield" to "off_hand")
    val CONDITION_TYPES = setOf(
        "affinity", "skill", "attribute", "level", "hour", "gold", "item", "flag", "memory", "rumour", "place", "relation",
    )
    val OUTCOME_TYPES = setOf(
        "affinity", "rumour", "give_item", "take_item", "gold", "trade", "flag", "memory", "skill_xp", "small_talk", "end",
    )

    private class Table<T>(val kind: String, val idOf: (T) -> String, val withId: (T, String) -> T) {
        val defs = LinkedHashMap<String, T>()
        val origin = HashMap<String, String>()

        fun has(id: String) = defs.containsKey(id)

        /** The only definition whose short id is [short], if there is exactly one. */
        fun uniqueShort(short: String): String? {
            var found: String? = null
            for (k in defs.keys) {
                if (k.length > short.length && k.endsWith(short) && k[k.length - short.length - 1] == ':') {
                    if (found != null) return null
                    found = k
                }
            }
            return found
        }

        fun resolve(pack: String, raw: String): String {
            if (raw.isEmpty()) return raw
            if (raw.contains(':')) return if (raw.startsWith("core:")) raw.substring(5) else raw
            if (pack != "core" && has("$pack:$raw")) return "$pack:$raw"
            if (has(raw)) return raw
            return uniqueShort(raw) ?: raw
        }

        fun pack(id: String) = origin[id] ?: "core"

        fun freeze(): Defs<T> {
            val aliases = HashMap<String, String>()
            val clash = HashSet<String>()
            for (k in defs.keys) {
                if (!k.contains(':')) continue
                val s = shortId(k)
                if (defs.containsKey(s) || s in clash) continue
                if (aliases.containsKey(s)) {
                    aliases.remove(s)
                    clash += s
                } else {
                    aliases[s] = k
                }
            }
            return Defs(defs, aliases)
        }
    }

    private class Dependency(val id: String, val min: String)

    private fun parseDependency(text: String): Dependency {
        val parts = text.split(">=")
        return Dependency(parts[0].trim(), parts.getOrNull(1)?.trim().orEmpty())
    }

    /** Compares dotted versions number by number. Missing parts count as zero. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrNull(i) ?: 0).compareTo(y.getOrNull(i) ?: 0)
            if (c != 0) return c
        }
        return 0
    }

    private fun count(p: ContentPack) = p.materials.size + p.items.size + p.races.size + p.backgrounds.size +
        p.professions.size + p.npcs.size + p.creatures.size + p.buildings.size + p.locations.size + p.spots.size +
        p.resources.size + p.interactables.size + p.dialogues.size + p.rumours.size

    /** Why this pack cannot be loaded next to [loaded] (pack id -> version), or null when it can. */
    fun blocker(m: ModManifest, loaded: Map<String, String>): String? {
        if (m.schemaVersion > SCHEMA_VERSION) {
            return "This pack was made for a newer version of the game (format ${m.schemaVersion}, this game reads up to $SCHEMA_VERSION). Update the game to use it."
        }
        if (m.schemaVersion < MIN_SCHEMA_VERSION) {
            return "This pack uses format ${m.schemaVersion}, which is too old for this game (oldest supported is $MIN_SCHEMA_VERSION)."
        }
        for (text in m.dependencies) {
            val dep = parseDependency(text)
            val have = loaded[dep.id]
                ?: return "Needs the pack '${dep.id}', which is missing, switched off or failed to load."
            if (dep.min.isNotEmpty() && compareVersions(have, dep.min) < 0) {
                return "Needs '${dep.id}' version ${dep.min} or newer, but version $have is installed."
            }
        }
        return null
    }

    fun build(sources: List<PackSource>, disabled: Set<String>, readIssues: List<ContentIssue> = emptyList()): ContentRegistry {
        val issues = ArrayList(readIssues)
        val found = sources.sortedWith(compareBy({ it.pack.manifest.loadOrder }, { it.pack.manifest.id }))

        val materials = Table<MaterialDef>("material", { it.id }, { d, id -> d.copy(id = id) })
        val items = Table<ItemDef>("item", { it.id }, { d, id -> d.copy(id = id) })
        val races = Table<RaceDef>("race", { it.id }, { d, id -> d.copy(id = id) })
        val backgrounds = Table<BackgroundDef>("background", { it.id }, { d, id -> d.copy(id = id) })
        val professions = Table<ProfessionDef>("profession", { it.id }, { d, id -> d.copy(id = id) })
        val npcs = Table<NpcDef>("person", { it.id }, { d, id -> d.copy(id = id) })
        val creatures = Table<CreatureDef>("creature", { it.id }, { d, id -> d.copy(id = id) })
        val buildings = Table<BuildingDef>("building", { it.id }, { d, id -> d.copy(id = id) })
        val locations = Table<LocationDef>("place", { it.id }, { d, id -> d.copy(id = id) })
        val spots = Table<SpotDef>("work spot", { it.id }, { d, id -> d.copy(id = id) })
        val resources = Table<ResourceDef>("resource", { it.id }, { d, id -> d.copy(id = id) })
        val interactables = Table<InteractableDef>("object", { it.id }, { d, id -> d.copy(id = id) })
        val dialogues = Table<DialogueDef>("conversation", { it.id }, { d, id -> d.copy(id = id) })
        val rumours = Table<RumourDef>("rumour", { it.id }, { d, id -> d.copy(id = id) })
        val population = ArrayList<Pair<String, PopulationDef>>()
        val loaded = LinkedHashMap<String, String>()
        val packs = ArrayList<PackInfo>()
        val seenPacks = HashSet<String>()

        for (l in found) {
            val m = l.pack.manifest
            val p = l.pack
            if (!seenPacks.add(m.id)) {
                issues += ContentIssue("warning", m.id, "Two packs share this id. The ${if (l.builtin) "built-in" else "imported"} copy was ignored.")
                continue
            }
            val enabled = m.id == "core" || m.id !in disabled
            val reason = if (enabled) blocker(m, loaded) else null
            if (reason != null) issues += ContentIssue("error", m.id, "$reason The pack was not loaded.")
            val ok = enabled && reason == null
            packs += PackInfo(m, l.builtin, enabled, ok, count(p))
            if (!ok) continue
            loaded[m.id] = m.version
            val deps = m.dependencies.map { parseDependency(it).id }.toSet()

            fun <T> register(t: Table<T>, defs: List<T>) {
                val mine = HashSet<String>()
                for (d in defs) {
                    val raw = t.idOf(d).trim()
                    if (raw.isBlank() || raw.any { it.isWhitespace() }) {
                        issues += ContentIssue("warning", m.id, "A ${t.kind} has ${if (raw.isBlank()) "no id" else "spaces in its id"} and was skipped.", raw)
                        continue
                    }
                    var replaces = false
                    val id: String = if (raw.contains(':')) {
                        val ns = raw.substringBefore(':')
                        val short = raw.substringAfter(':')
                        when (ns) {
                            m.id -> if (m.id == "core") short else raw
                            else -> {
                                val target = if (ns == "core") short else raw
                                if (!t.has(target)) {
                                    issues += ContentIssue("warning", m.id, "Tries to replace the ${t.kind} '$raw', but nothing with that id is loaded. It was skipped.", raw)
                                    continue
                                }
                                if (ns != "core" && ns !in deps) {
                                    issues += ContentIssue("warning", m.id, "Replaces the ${t.kind} '$raw' without listing '$ns' as a dependency. Load order may not be what you expect.", raw)
                                }
                                replaces = true
                                target
                            }
                        }
                    } else if (m.id == "core") {
                        raw
                    } else {
                        val legacy = if (m.schemaVersion < 2) (if (t.has(raw)) raw else t.uniqueShort(raw)) else null
                        if (legacy != null && t.origin[legacy] != m.id) {
                            issues += ContentIssue("warning", m.id, "Replaces the ${t.kind} '$legacy' by reusing its short id. This old style still works; newer packs write the full id instead.", raw)
                            replaces = true
                            legacy
                        } else {
                            "${m.id}:$raw"
                        }
                    }
                    if (!mine.add(id)) {
                        issues += ContentIssue("warning", m.id, "The ${t.kind} '$raw' is defined twice in this pack. The later one is used.", raw)
                    } else if (replaces) {
                        issues += ContentIssue("info", m.id, "Replaces the ${t.kind} '$id' from pack '${t.pack(id)}'.", raw)
                    }
                    t.origin[id] = m.id
                    t.defs[id] = t.withId(d, id)
                }
            }
            register(materials, p.materials)
            register(items, p.items)
            register(races, p.races)
            register(backgrounds, p.backgrounds)
            register(professions, p.professions)
            register(npcs, p.npcs)
            register(creatures, p.creatures)
            register(buildings, p.buildings)
            register(locations, p.locations)
            register(spots, p.spots)
            register(resources, p.resources)
            register(interactables, p.interactables)
            register(dialogues, p.dialogues)
            register(rumours, p.rumours)
            p.population.forEach { population += m.id to it }
        }

        // ---- second pass: resolve references, normalise, and report what is missing.
        fun warn(t: Table<*>, id: String, message: String) {
            issues += ContentIssue("warning", t.pack(id), message, id)
        }
        fun workplace(pack: String, ref: String): String? {
            if (ref.isEmpty()) return ""
            val b = buildings.resolve(pack, ref)
            if (buildings.has(b)) return b
            val s = spots.resolve(pack, ref)
            if (spots.has(s)) return s
            return if (buildings.defs.values.any { it.type == ref }) ref else null
        }
        fun stacks(t: Table<*>, id: String, what: String, list: List<StackDef>): List<StackDef> {
            val pack = t.pack(id)
            val out = list.map { it.copy(item = items.resolve(pack, it.item)) }
            val bad = out.filter { !items.has(it.item) }
            if (bad.isNotEmpty()) warn(t, id, "The ${t.kind} '$id' $what items that do not exist: ${bad.joinToString { it.item }}. They were left out.")
            return out.filter { items.has(it.item) }
        }
        fun loot(t: Table<*>, id: String, what: String, list: List<LootDef>): List<LootDef> {
            val pack = t.pack(id)
            val out = list.map { it.copy(item = items.resolve(pack, it.item)) }
            val bad = out.filter { !items.has(it.item) }
            if (bad.isNotEmpty()) warn(t, id, "The ${t.kind} '$id' $what items that do not exist: ${bad.joinToString { it.item }}. They were left out.")
            return out.filter { items.has(it.item) }
        }

        for ((id, d) in items.defs.toList()) {
            val pack = items.pack(id)
            var slot = LEGACY_SLOTS[d.slot] ?: d.slot
            if (slot.isNotEmpty() && slot !in ITEM_SLOTS) {
                warn(items, id, "The item '$id' uses the unknown slot '${d.slot}', so it cannot be worn. Slots are: ${ITEM_SLOTS.joinToString()}.")
                slot = ""
            }
            var rumour = rumours.resolve(pack, d.rumour)
            if (rumour.isNotEmpty() && !rumours.has(rumour)) {
                warn(items, id, "The item '$id' teaches the rumour '${d.rumour}', which does not exist.")
                rumour = ""
            }
            val weaponType = when {
                slot != "main_hand" -> ""
                d.weaponType in setOf("one_handed", "two_handed", "dagger") -> d.weaponType
                else -> {
                    if (d.weaponType.isNotEmpty()) warn(items, id, "The item '$id' has the unknown weapon type '${d.weaponType}'. It is treated as one-handed.")
                    "one_handed"
                }
            }
            items.defs[id] = d.copy(slot = slot, rumour = rumour, weaponType = weaponType)
        }
        for ((id, d) in materials.defs.toList()) {
            val item = items.resolve(materials.pack(id), d.itemId)
            if (!items.has(item)) {
                warn(materials, id, "The material '$id' is forged from the item '${d.itemId}', which does not exist. The material was removed.")
                materials.defs.remove(id)
            } else {
                materials.defs[id] = d.copy(itemId = item)
            }
        }
        for ((id, d) in professions.defs.toList()) {
            val w = workplace(professions.pack(id), d.workplace)
            if (w == null) warn(professions, id, "The profession '$id' works at '${d.workplace}', which is not a building, building type or work spot. They will have no workplace.")
            professions.defs[id] = d.copy(goods = stacks(professions, id, "sells", d.goods), workplace = w ?: "")
        }
        for ((id, d) in backgrounds.defs.toList()) {
            val pack = backgrounds.pack(id)
            val kept = stacks(backgrounds, id, "starts with", d.items)
            val equip = d.equip.map { items.resolve(pack, it) }.filter { e -> kept.any { it.item == e } }
            backgrounds.defs[id] = d.copy(items = kept, equip = equip)
        }
        for ((id, d) in npcs.defs.toList()) {
            val pack = npcs.pack(id)
            val race = races.resolve(pack, d.race)
            val prof = professions.resolve(pack, d.profession)
            val home = buildings.resolve(pack, d.home)
            val work = workplace(pack, d.workplace)
            val problems = buildList {
                if (!races.has(race)) add("race '${d.race}'")
                if (!professions.has(prof)) add("profession '${d.profession}'")
                if (!buildings.has(home)) add("home '${d.home}'")
                if (work == null) add("workplace '${d.workplace}'")
            }
            if (problems.isNotEmpty()) {
                warn(npcs, id, "The person '$id' needs a missing ${problems.joinToString(" and ")} and was removed.")
                npcs.defs.remove(id)
            } else {
                npcs.defs[id] = d.copy(race = race, profession = prof, home = home, workplace = work.orEmpty())
            }
        }
        for ((id, d) in locations.defs.toList()) {
            if (d.radius <= 0f) {
                warn(locations, id, "The place '$id' has no size and was removed.")
                locations.defs.remove(id)
            }
        }
        for ((id, d) in creatures.defs.toList()) {
            val territory = locations.resolve(creatures.pack(id), d.territory)
            if (!locations.has(territory)) {
                warn(creatures, id, "The creature '$id' lives in '${d.territory}', which is not a known place. It was removed.")
                creatures.defs.remove(id)
            } else {
                creatures.defs[id] = d.copy(territory = territory, loot = loot(creatures, id, "drops", d.loot))
            }
        }
        for ((id, d) in resources.defs.toList()) {
            val item = items.resolve(resources.pack(id), d.item)
            if (!items.has(item)) {
                warn(resources, id, "The resource '$id' yields the item '${d.item}', which does not exist. It was removed.")
                resources.defs.remove(id)
            } else {
                resources.defs[id] = d.copy(item = item)
            }
        }
        for ((id, d) in rumours.defs.toList()) {
            var loc = locations.resolve(rumours.pack(id), d.location)
            if (loc.isNotEmpty() && !locations.has(loc)) {
                warn(rumours, id, "The rumour '$id' points at '${d.location}', which is not a known place.")
                loc = ""
            }
            rumours.defs[id] = d.copy(location = loc)
        }
        for ((id, d) in interactables.defs.toList()) {
            val pack = interactables.pack(id)
            val owner = npcs.resolve(pack, d.owner)
            if (owner.isNotEmpty() && !npcs.has(owner)) {
                warn(interactables, id, "The object '$id' belongs to '${d.owner}', who does not exist. It was removed so it cannot be used freely by mistake.")
                interactables.defs.remove(id)
                continue
            }
            var rumour = rumours.resolve(pack, d.rumour)
            if (rumour.isNotEmpty() && !rumours.has(rumour)) {
                warn(interactables, id, "The object '$id' teaches the rumour '${d.rumour}', which does not exist.")
                rumour = ""
            }
            interactables.defs[id] = d.copy(owner = owner, rumour = rumour, loot = loot(interactables, id, "holds", d.loot))
        }
        for ((id, d) in dialogues.defs.toList()) {
            val pack = dialogues.pack(id)
            val npc = npcs.resolve(pack, d.npc)
            val prof = professions.resolve(pack, d.profession)
            if ((d.npc.isEmpty() && d.profession.isEmpty()) || d.nodes.isEmpty() ||
                (d.npc.isNotEmpty() && !npcs.has(npc)) || (d.npc.isEmpty() && !professions.has(prof))
            ) {
                val why = when {
                    d.nodes.isEmpty() -> "has nothing in it"
                    d.npc.isEmpty() && d.profession.isEmpty() -> "is not attached to a person or a profession"
                    d.npc.isNotEmpty() -> "is for '${d.npc}', who does not exist"
                    else -> "is for the profession '${d.profession}', which does not exist"
                }
                warn(dialogues, id, "The conversation '$id' $why and was removed.")
                dialogues.defs.remove(id)
                continue
            }
            val nodeIds = d.nodes.map { it.id }.toSet()
            fun conditions(list: List<ConditionDef>, where: String): List<ConditionDef> = list.map { c ->
                if (c.type !in CONDITION_TYPES) {
                    warn(dialogues, id, "The conversation '$id' ($where) uses the unknown condition '${c.type}'. It will never pass.")
                }
                when (c.type) {
                    "item" -> c.copy(key = items.resolve(pack, c.key))
                    "rumour" -> c.copy(key = rumours.resolve(pack, c.key))
                    "place" -> c.copy(key = locations.resolve(pack, c.key))
                    else -> c
                }
            }
            val nodes = d.nodes.map { node ->
                node.copy(
                    lines = node.lines.map { it.copy(conditions = conditions(it.conditions, "line in '${node.id}'")) },
                    replies = node.replies.map { r ->
                        if (r.goto.isNotEmpty() && r.goto !in nodeIds) {
                            warn(dialogues, id, "The conversation '$id' has a reply in '${node.id}' that leads to '${r.goto}', which is not one of its parts. It returns to the start instead.")
                        }
                        val outcomes = r.outcomes.mapNotNull { o ->
                            when (o.type) {
                                !in OUTCOME_TYPES -> {
                                    warn(dialogues, id, "The conversation '$id' (reply in '${node.id}') uses the unknown outcome '${o.type}'. It was ignored.")
                                    null
                                }
                                "give_item", "take_item" -> {
                                    val item = items.resolve(pack, o.key)
                                    if (!items.has(item)) {
                                        warn(dialogues, id, "The conversation '$id' (reply in '${node.id}') hands over the item '${o.key}', which does not exist. That outcome was ignored.")
                                        null
                                    } else {
                                        o.copy(key = item)
                                    }
                                }
                                "rumour" -> {
                                    val rumour = rumours.resolve(pack, o.key)
                                    if (!rumours.has(rumour)) {
                                        warn(dialogues, id, "The conversation '$id' (reply in '${node.id}') teaches the rumour '${o.key}', which does not exist. That outcome was ignored.")
                                        null
                                    } else {
                                        o.copy(key = rumour)
                                    }
                                }
                                else -> o
                            }
                        }
                        r.copy(
                            conditions = conditions(r.conditions, "reply in '${node.id}'"),
                            outcomes = outcomes,
                            goto = if (r.goto in nodeIds) r.goto else "",
                        )
                    },
                )
            }
            dialogues.defs[id] = d.copy(npc = if (d.npc.isEmpty()) "" else npc, profession = if (d.npc.isEmpty()) prof else "", nodes = nodes)
        }

        generatePopulation(population, professions, races, buildings, npcs)

        return ContentRegistry(
            materials.freeze(), items.freeze(), races.freeze(), backgrounds.freeze(), professions.freeze(),
            npcs.freeze(), creatures.freeze(), buildings.freeze(), locations.freeze(), spots.freeze(),
            resources.freeze(), interactables.freeze(), dialogues.freeze(), rumours.freeze(), packs, issues,
        )
    }

    private val givenNames = listOf(
        "Aldric", "Berta", "Corin", "Dagny", "Edda", "Fenn", "Greta", "Halvar", "Ingrid", "Joss",
        "Kara", "Leof", "Maren", "Nils", "Odette", "Perrin", "Runa", "Soren", "Tilda", "Ulf", "Wren", "Yorick",
    )
    private val hairTones = listOf("#3A2A1E", "#6B3B24", "#A8703A", "#1F1A16", "#8A8378", "#C9A45A")

    /** Fills the settlement with ordinary townsfolk from profession templates, deterministically. */
    private fun generatePopulation(
        population: List<Pair<String, PopulationDef>>,
        professions: Table<ProfessionDef>,
        races: Table<RaceDef>,
        buildings: Table<BuildingDef>,
        npcs: Table<NpcDef>,
    ) {
        val houses = buildings.defs.values.filter { it.type == "house" }
        if (houses.isEmpty()) return
        var index = 0
        for ((pack, pop) in population) {
            val profId = professions.resolve(pack, pop.profession)
            if (!professions.has(profId)) continue
            val race = races.defs[races.resolve(pack, pop.race)] ?: races.defs.values.firstOrNull() ?: continue
            repeat(pop.count) { i ->
                val rnd = Random(index * 7919 + 17)
                // Generated townsfolk keep the id they have always had, so old saves still find them.
                val id = "pop_${shortId(profId)}_${i + 1}"
                if (!npcs.has(id)) {
                    fun trait() = 0.2f + rnd.nextFloat() * 0.7f
                    npcs.origin[id] = pack
                    npcs.defs[id] = NpcDef(
                        id = id,
                        name = givenNames[index % givenNames.size],
                        race = race.id,
                        profession = profId,
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
}

object ContentLoader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val ASSET_DIR = "content"
    private val PACK_ID = Regex("[a-z0-9_\\-]{2,40}")

    private fun modDir(context: Context) = File(context.filesDir, "mods").apply { mkdirs() }

    private fun readError(e: Exception): String {
        val detail = e.message?.lineSequence()?.firstOrNull()?.take(160).orEmpty()
        return "This file could not be read as a content pack. $detail".trim()
    }

    /** Reads every built-in and imported pack. Files that cannot be parsed become issues. */
    fun readPacks(context: Context, issues: MutableList<ContentIssue>): List<PackSource> {
        val found = ArrayList<PackSource>()
        context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".json") }.sorted().forEach { name ->
            try {
                val text = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
                found += PackSource(json.decodeFromString<ContentPack>(text), true)
            } catch (e: Exception) {
                issues += ContentIssue("error", name, readError(e))
            }
        }
        modDir(context).listFiles().orEmpty().filter { it.name.endsWith(".json") }.sortedBy { it.name }.forEach { file ->
            try {
                found += PackSource(json.decodeFromString<ContentPack>(file.readText()), false)
            } catch (e: Exception) {
                issues += ContentIssue("error", file.name, readError(e))
            }
        }
        return found
    }

    fun load(context: Context, disabled: Set<String>): ContentRegistry {
        val issues = ArrayList<ContentIssue>()
        val sources = readPacks(context, issues)
        return ContentBuilder.build(sources, disabled, issues)
    }

    /**
     * Checks a candidate pack against what is installed, without touching storage.
     * Returns the reasons it cannot be installed; an empty list means it is safe.
     */
    fun validateImport(text: String, installed: List<PackSource>, disabled: Set<String>): Pair<ContentPack?, List<String>> {
        val pack = try {
            json.decodeFromString<ContentPack>(text)
        } catch (e: Exception) {
            return null to listOf(readError(e))
        }
        val m = pack.manifest
        val problems = ArrayList<String>()
        if (!m.id.matches(PACK_ID)) problems += "The pack id '${m.id}' must be 2 to 40 lowercase letters, digits, - or _."
        if (m.id == "core") problems += "A pack cannot replace the core game."
        if (installed.any { it.builtin && it.pack.manifest.id == m.id }) problems += "The id '${m.id}' belongs to a pack that ships with the game."
        if (m.name.isBlank()) problems += "The pack has no name."
        if (problems.isNotEmpty()) return pack to problems

        val others = installed.filter { it.pack.manifest.id != m.id }
        val available = others.filter { it.pack.manifest.id == "core" || it.pack.manifest.id !in disabled }
            .associate { it.pack.manifest.id to it.pack.manifest.version }
        ContentBuilder.blocker(m, available)?.let { problems += it }

        fun ids(kind: String, list: List<String>) {
            if (list.any { it.isBlank() }) problems += "A $kind in this pack has no id."
            val twice = list.filter { it.isNotBlank() }.groupBy { it }.filter { it.value.size > 1 }.keys
            if (twice.isNotEmpty()) problems += "These $kind ids are used more than once: ${twice.joinToString()}."
        }
        ids("material", pack.materials.map { it.id })
        ids("item", pack.items.map { it.id })
        ids("race", pack.races.map { it.id })
        ids("background", pack.backgrounds.map { it.id })
        ids("profession", pack.professions.map { it.id })
        ids("person", pack.npcs.map { it.id })
        ids("creature", pack.creatures.map { it.id })
        ids("building", pack.buildings.map { it.id })
        ids("place", pack.locations.map { it.id })
        ids("work spot", pack.spots.map { it.id })
        ids("resource", pack.resources.map { it.id })
        ids("object", pack.interactables.map { it.id })
        ids("conversation", pack.dialogues.map { it.id })
        ids("rumour", pack.rumours.map { it.id })
        if (problems.isNotEmpty()) return pack to problems

        // Dry run: the whole world must still build with this pack in it.
        try {
            val registry = ContentBuilder.build(others + PackSource(pack, false), disabled - m.id)
            registry.issues.filter { it.severity == "error" && it.packId == m.id }.forEach { problems += it.message }
            if (registry.races.isEmpty() || registry.buildings.isEmpty()) problems += "With this pack installed the world would have no people or buildings."
        } catch (e: Exception) {
            problems += "The game could not build the world with this pack: ${e.message?.take(120)}"
        }
        return pack to problems
    }

    /**
     * Installs a pack file picked by the player. The file is fully checked in memory first and
     * only then written, through a temporary file, so a broken pack can never replace a working one.
     */
    fun importPack(context: Context, uri: Uri, disabled: Set<String>): Result<ModManifest> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("The file could not be opened.")
        val installed = readPacks(context, ArrayList())
        val (pack, problems) = validateImport(text, installed, disabled)
        if (pack == null || problems.isNotEmpty()) {
            val keeps = pack != null && installed.any { !it.builtin && it.pack.manifest.id == pack.manifest.id }
            error(problems.take(3).joinToString(" ") + if (keeps) " Your installed copy of this pack was kept." else "")
        }
        val dir = modDir(context)
        val target = File(dir, "${pack.manifest.id}.json")
        val tmp = File(dir, "${pack.manifest.id}.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("The pack could not be saved to storage.")
        }
        pack.manifest
    }

    fun removePack(context: Context, id: String) {
        File(modDir(context), "$id.json").delete()
    }
}
