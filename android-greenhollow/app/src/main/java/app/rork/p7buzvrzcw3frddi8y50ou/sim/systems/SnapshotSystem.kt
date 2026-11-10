package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.data.ItemDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.hexToRgb
import app.rork.p7buzvrzcw3frddi8y50ou.sim.AttributeView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.BeastView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Calendar
import app.rork.p7buzvrzcw3frddi8y50ou.sim.CharacterView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.CompareLine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Hud
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ItemInstance
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ItemView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.JournalView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.NpcView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.PackView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.PlaceView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.sim.RenderFrame
import app.rork.p7buzvrzcw3frddi8y50ou.sim.RumourView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.SkillView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.SlotView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Terrain
import app.rork.p7buzvrzcw3frddi8y50ou.sim.WareView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Turns the live world into the immutable snapshots the screens read and the frame the renderer draws. */
class SnapshotSystem(
    private val w: World,
    private val hud: MutableStateFlow<Hud>,
    private val heading: MutableStateFlow<Float>,
    private val pack: MutableStateFlow<PackView>,
    private val journal: MutableStateFlow<JournalView>,
) {
    private val p get() = w.p
    private val save get() = w.save
    private val content get() = w.content
    private var hudTimer = 0f
    private var journalTick = 0
    private val tints = HashMap<String, FloatArray>()

    private fun tint(hex: String): FloatArray = tints.getOrPut(hex) { hexToRgb(hex) }

    fun publish(dt: Float, force: Boolean) {
        hudTimer += dt
        val deg = Math.toDegrees(p.yaw.toDouble()).toFloat()
        if (abs(deg - heading.value) > 0.4f) heading.value = deg
        if (!force && hudTimer < 0.2f) return
        hudTimer = 0f
        val prog = w.progression
        val h = w.hostile
        hud.value = Hud(
            health = p.health, maxHealth = prog.maxHealth,
            stamina = p.stamina, maxStamina = prog.maxStamina,
            magic = p.magic, maxMagic = prog.maxMagic,
            timeLabel = Calendar.label(save.minutes),
            clock = Calendar.clock(save.minutes),
            location = w.discovery.currentLocation()?.name ?: "The Wilds",
            prompt = w.target?.label,
            promptKind = w.target?.kind.orEmpty(),
            thirdPerson = p.thirdPerson,
            fps = w.fps,
            swimming = w.swimming,
            weather = save.weather,
            targetName = h?.def?.name,
            targetHealth = if (h != null) (h.s.health / h.def.health).coerceIn(0f, 1f) else 0f,
            markers = w.discovery.markers(),
            level = p.level,
            points = p.attributePoints,
        )
        journalTick++
        if (force || w.journalDirty || journalTick % 4 == 1) {
            w.journalDirty = false
            publishJournal()
        }
        if (w.packDirty || force) {
            w.packDirty = false
            publishPack()
        }
    }

    private fun date(minute: Double) = Calendar.dateLabel(minute)

    private fun character(): CharacterView {
        val prog = w.progression
        val a = p.appearance
        return CharacterView(
            level = p.level,
            levelProgress = (p.levelProgress / Progression.levelNeed(p.level)).coerceIn(0f, 1f),
            points = p.attributePoints,
            attributes = Progression.ATTRIBUTES.map {
                AttributeView(it, Progression.name(it), prog.base(it), prog.bonus(it), Progression.effect(it))
            },
            skills = (Progression.SKILLS + p.skills.keys.filter { it !in Progression.SKILLS }).map {
                val v = prog.skill(it)
                SkillView(it, Progression.name(it), v.toInt(), v - v.toInt())
            },
            maxHealth = prog.maxHealth, maxStamina = prog.maxStamina, maxMagic = prog.maxMagic,
            capacity = prog.capacity,
            armor = w.inventory.armor(),
            race = content.races[p.race]?.name ?: p.race,
            background = content.backgrounds[p.background]?.name ?: p.background,
            skin = a.skin.ifBlank { "#E0B08A" }, hair = a.hair, cloth = a.cloth,
            beard = a.beard, longHair = a.hairStyle == "long", bald = a.hairStyle == "bald",
        )
    }

    private fun publishJournal() {
        val talk = w.dialogue
        val views = w.npcs.map { n ->
            NpcView(
                id = n.def.id, def = n.def,
                profession = n.prof.name,
                race = content.races[n.def.race]?.name ?: n.def.race,
                activity = n.s.activity,
                affinity = n.s.affinity,
                relation = talk.relationOf(n),
                hunger = n.s.hunger, fatigue = n.s.fatigue, social = n.s.social,
                gold = n.s.gold,
                memories = n.s.memories.toList().asReversed(),
                bonds = n.s.bonds.entries.sortedByDescending { abs(it.value) }.take(4)
                    .mapNotNull { e -> w.npcById[e.key]?.let { it.def.name to e.value } },
                home = n.homeName,
                work = n.workName,
                trader = n.prof.trader,
                wares = if (!n.prof.trader) emptyList() else n.s.stock.filter { it.count > 0 }.mapNotNull { e ->
                    content.items[e.item]?.let { d -> WareView(d.id, d.name, d.icon, e.count, w.trade.buyPrice(n, d.value)) }
                },
                skin = n.skinHex, cloth = n.clothHex, hat = n.hat,
            )
        }.sortedByDescending { it.affinity }

        val places = content.locations.values.map { l ->
            val found = save.discovered[l.id]
            PlaceView(
                id = l.id,
                name = if (found != null) l.name else "Somewhere unknown",
                description = if (found != null) l.description else "",
                discovered = found != null,
                date = if (found != null) date(found) else "",
            )
        }.sortedWith(compareByDescending<PlaceView> { it.discovered }.thenBy { it.name })

        val rumours = save.rumours.asReversed().mapNotNull { r ->
            val def = content.rumours[r.id] ?: return@mapNotNull null
            val place = content.locations[def.location]
            RumourView(
                title = def.title, text = def.text, source = r.source, date = date(r.minute),
                place = when {
                    place == null -> ""
                    place.id in save.discovered -> place.name
                    else -> "A place you have not found yet"
                },
            )
        }

        val bestiary = content.creatures.values.map { c ->
            val e = save.bestiary[c.id]
            BeastView(
                name = if (e != null) c.name else "Unknown creature",
                description = if (e != null) c.description else "",
                known = e != null,
                kills = e?.kills ?: 0,
                date = if (e != null) date(e.firstSeen) else "",
                color = c.color,
            )
        }.sortedByDescending { it.known }

        journal.value = JournalView(
            npcs = views,
            hunger = p.hunger, fatigue = p.fatigue, comfort = p.comfort,
            dateLine = "${Calendar.clock(save.minutes)}, ${date(save.minutes)}",
            season = Calendar.season(save.minutes),
            history = save.history.takeLast(14).asReversed().map { "${date(it.minute)}: ${it.text}" },
            playerName = p.name,
            character = character(),
            places = places,
            rumours = rumours,
            bestiary = bestiary,
        )
    }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    private fun damageOf(it: ItemInstance, def: ItemDef?) = it.forged?.stats?.damage ?: def?.damage ?: 0f
    private fun speedOf(it: ItemInstance, def: ItemDef?) = it.forged?.stats?.speed ?: def?.speed ?: 1f
    private fun reachOf(it: ItemInstance, def: ItemDef?) = it.forged?.stats?.reach ?: def?.reach ?: 0f

    private fun compareLines(it: ItemInstance, def: ItemDef, other: ItemInstance): List<CompareLine> {
        val o = content.items[other.defId]
        val lines = ArrayList<CompareLine>()
        if (def.slot == "main_hand") {
            lines += CompareLine("Damage", fmt(damageOf(it, def)), damageOf(it, def) - damageOf(other, o))
            lines += CompareLine("Speed", "%.2f".format(speedOf(it, def)), speedOf(it, def) - speedOf(other, o))
            lines += CompareLine("Reach", "%.1f m".format(reachOf(it, def)), reachOf(it, def) - reachOf(other, o))
        }
        if (def.armor > 0f || (o?.armor ?: 0f) > 0f) {
            lines += CompareLine("Armour", fmt(def.armor), def.armor - (o?.armor ?: 0f))
        }
        val w1 = w.inventory.weightOf(it)
        val w2 = w.inventory.weightOf(other)
        // Lighter is better, so the sign is flipped.
        lines += CompareLine("Weight", "%.1f".format(w1), w2 - w1)
        return lines
    }

    private fun publishPack() {
        val inv = w.inventory
        val items = p.inventory.map { it ->
            val def = content.items[it.defId]
            val worn = inv.wornIn(it.uid)
            val equippable = def != null && def.slot.isNotEmpty()
            var compare: List<CompareLine> = emptyList()
            var comparedTo = ""
            if (def != null && equippable && worn == null) {
                val other = if (def.slot == "trinket") {
                    if ("trinket_1" in p.equipped && "trinket_2" in p.equipped) inv.equippedItem("trinket_1") else null
                } else {
                    inv.equippedItem(def.slot)
                }
                if (other != null) {
                    compare = compareLines(it, def, other)
                    comparedTo = inv.nameOf(other)
                }
            }
            ItemView(
                uid = it.uid, defId = it.defId,
                name = inv.nameOf(it),
                type = def?.type ?: "missing",
                slot = def?.slot.orEmpty(),
                icon = def?.icon ?: "misc",
                count = it.count,
                condition = it.condition,
                weight = inv.weightOf(it),
                value = inv.valueOf(it, worn = false),
                description = it.forged?.let { f -> "${f.quality} work in ${content.materials[f.materialId]?.name ?: f.materialId}, shaped at your own anvil." }
                    ?: def?.description ?: "This item came from a content pack that is no longer enabled.",
                equipped = worn != null,
                damage = damageOf(it, def),
                armor = def?.armor ?: 0f,
                forged = it.forged,
                usable = def != null && (def.type == "food" || def.type == "potion" || def.type == "book"),
                wornIn = worn.orEmpty(),
                slotLabel = InventorySystem.slotLabel(worn ?: def?.slot.orEmpty()),
                weaponType = inv.weaponType(it),
                speed = speedOf(it, def),
                reach = if (def?.slot == "main_hand") reachOf(it, def) else 0f,
                armorClass = def?.armorClass.orEmpty(),
                requirements = def?.requirements?.map { (k, v) -> "${Progression.name(k)} $v" } ?: emptyList(),
                bonuses = def?.bonuses?.map { (k, v) -> "${if (v >= 0) "+" else ""}$v ${Progression.name(k)}" } ?: emptyList(),
                blocked = if (equippable && worn == null) inv.blockedReason(def) else "",
                compare = compare,
                comparedTo = comparedTo,
            )
        }
        val slots = InventorySystem.SLOTS.map { (id, label) -> SlotView(id, label, inv.equippedItem(id)?.let { inv.nameOf(it) }) }
        pack.value = PackView(items, p.gold, inv.burden(), w.progression.capacity, w.progression.skill("smithing"), slots)
    }

    fun fillFrame(f: RenderFrame) {
        val cp = cos(p.pitch)
        val dx = sin(p.yaw) * cp
        val dy = sin(p.pitch)
        val dz = -cos(p.yaw) * cp
        val eyeY = p.y + if (w.swimming) 1.35f else 1.68f
        if (p.thirdPerson) {
            var cx = p.x - dx * 4.2f
            var cy = eyeY - dy * 4.2f + 0.7f
            var cz = p.z - dz * 4.2f
            val floor = max(Terrain.height(cx, cz), Terrain.WATER) + 0.4f
            if (cy < floor) cy = floor
            cx = cx.coerceIn(-Terrain.HALF + 2f, Terrain.HALF - 2f)
            cz = cz.coerceIn(-Terrain.HALF + 2f, Terrain.HALF - 2f)
            f.camX = cx; f.camY = cy; f.camZ = cz
        } else {
            f.camX = p.x; f.camY = eyeY; f.camZ = p.z
        }
        f.yaw = p.yaw
        f.pitch = p.pitch
        f.hour = Calendar.hour(save.minutes)
        f.mist = when (save.weather) { "mist" -> 1f; "overcast" -> 0.5f; else -> 0f }
        f.swing = w.swing
        f.blocking = w.input.block
        val weapon = w.inventory.equippedItem("main_hand")
        f.firstPersonWeapon = !p.thirdPerson && weapon != null
        if (weapon != null) {
            f.bladeLength = (weapon.forged?.geometry?.length ?: 80f) / 100f
            val mat = weapon.forged?.materialId?.let { content.materials[it] }
            val rgb = if (mat != null) tint(mat.color) else STEEL
            f.bladeColor[0] = rgb[0]; f.bladeColor[1] = rgb[1]; f.bladeColor[2] = rgb[2]
        }

        var a = 0
        fun put(x: Float, y: Float, z: Float, yaw: Float, phase: Float, scale: Float, kind: Int, flags: Int, c1: FloatArray, c2: FloatArray, c3: FloatArray) {
            if (a >= RenderFrame.MAX_ACTORS) return
            f.ax[a] = x; f.ay[a] = y; f.az[a] = z; f.ayaw[a] = yaw; f.aphase[a] = phase
            f.ascale[a] = scale; f.akind[a] = kind; f.aflags[a] = flags
            val o = a * 9
            f.acol[o] = c1[0]; f.acol[o + 1] = c1[1]; f.acol[o + 2] = c1[2]
            f.acol[o + 3] = c2[0]; f.acol[o + 4] = c2[1]; f.acol[o + 5] = c2[2]
            f.acol[o + 6] = c3[0]; f.acol[o + 7] = c3[1]; f.acol[o + 8] = c3[2]
            a++
        }
        if (p.thirdPerson) {
            val look = p.appearance
            val flags = (if (weapon != null) 8 else 0) or (if (look.beard) 2 else 0) or
                (if (look.hairStyle == "long") 4 else 0) or (if (look.hairStyle == "bald") 16 else 0)
            put(
                p.x, p.y, p.z, p.yaw, w.walkPhase, look.build.coerceIn(0.85f, 1.15f), 0, flags,
                tint(look.skin.ifBlank { "#E0B08A" }), tint(look.cloth), tint(look.hair),
            )
        }
        val far = 170f * 170f
        for (n in w.npcs) {
            if (n.s.indoors) continue
            val ddx = n.s.x - p.x
            val ddz = n.s.z - p.z
            if (ddx * ddx + ddz * ddz > far) continue
            val flags = (if (n.hat) 1 else 0) or (if (n.def.beard) 2 else 0) or (if (n.def.longHair) 4 else 0)
            put(n.s.x, w.groundY(n.s.x, n.s.z), n.s.z, n.yaw, if (n.moving) n.phase else 0f, 0.94f + (n.def.age % 7) * 0.012f, 0, flags, n.skin, n.cloth, n.hair)
        }
        for (c in w.creatures) {
            if (c.s.health <= 0f) continue
            val ddx = c.s.x - p.x
            val ddz = c.s.z - p.z
            if (ddx * ddx + ddz * ddz > far) continue
            put(c.s.x, w.groundY(c.s.x, c.s.z), c.s.z, c.yaw, c.phase, c.def.size, 1, 0, c.color, c.color, c.color)
        }
        f.actorCount = a

        // Only what is near enough to be seen is sent, so packs can add as many objects as they like.
        val near = 140f * 140f
        var k = 0
        for (r in w.interaction.resources) {
            if (k >= RenderFrame.MAX_NODES) break
            if ((save.harvested[r.id] ?: 0.0) > save.minutes) continue
            val ddx = r.x - p.x
            val ddz = r.z - p.z
            if (ddx * ddx + ddz * ddz > near) continue
            f.nx[k] = r.x; f.nz[k] = r.z; f.ny[k] = Terrain.height(r.x, r.z)
            f.nkind[k] = if (r.kind == "ore") 1 else 0
            k++
        }
        for (o in w.interaction.objects) {
            if (k >= RenderFrame.MAX_NODES) break
            val ddx = o.x - p.x
            val ddz = o.z - p.z
            if (ddx * ddx + ddz * ddz > near) continue
            f.nx[k] = o.x; f.nz[k] = o.z; f.ny[k] = Terrain.height(o.x, o.z)
            f.nkind[k] = when {
                o.kind == "container" -> 2
                o.kind == "sign" -> 3
                o.kind == "bed" || o.sleep -> 4
                o.kind == "book" -> 6
                else -> 5
            }
            k++
        }
        f.nodeCount = k
    }

    private companion object {
        val STEEL = floatArrayOf(0.72f, 0.73f, 0.76f)
    }
}
