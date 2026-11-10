package app.rork.p7buzvrzcw3frddi8y50ou.sim.systems

import app.rork.p7buzvrzcw3frddi8y50ou.data.ConditionDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.DialogueDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.DialogueNodeDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.ReplyDef
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Calendar
import app.rork.p7buzvrzcw3frddi8y50ou.sim.DialogueView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Npc
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ReplyView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.UiEvent
import app.rork.p7buzvrzcw3frddi8y50ou.sim.World
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Conversations. A villager with a dialogue written in a content pack is spoken to through its
 * nodes, conditions and outcomes; anyone else falls back to a greeting and small talk.
 */
class DialogueSystem(private val w: World, private val out: MutableStateFlow<DialogueView?>) {
    private val p get() = w.p
    private val save get() = w.save

    private var npc: Npc? = null
    private var def: DialogueDef? = null
    private var nodeId = ""
    private var tradeNonce = 0
    /** Indices into the current node's replies, in the order they are shown. */
    private var shown: List<Int> = emptyList()

    // ------------------------------------------------------------ relations

    fun relationOf(n: Npc): String = when {
        n.def.relation == "family" -> "family"
        n.s.affinity >= 50 -> "friend"
        n.s.affinity < 25 -> "rival"
        else -> "stranger"
    }

    fun greeting(n: Npc): String {
        if (n.def.greeting.isNotBlank()) return n.def.greeting
        return when (relationOf(n)) {
            "friend" -> "Good to see you, ${p.name}."
            "rival" -> "Oh. It's you."
            "family" -> "There you are!"
            else -> "Can I help you, stranger?"
        }
    }

    fun remember(n: Npc, text: String) {
        n.s.memories += "${Calendar.dateLabel(save.minutes)}: $text"
        while (n.s.memories.size > 10) n.s.memories.removeAt(0)
        w.journalDirty = true
    }

    /** Changes how someone feels about the player, noting the moment they become a friend. */
    fun warm(n: Npc, amount: Int) {
        if (amount == 0) return
        val before = n.s.affinity
        n.s.affinity = (before + amount).coerceIn(0, 100)
        if (before < 50 && n.s.affinity >= 50 && n.def.relation != "family") {
            w.record("${n.def.name} now counts ${p.name} as a friend.")
        }
        w.journalDirty = true
    }

    // ------------------------------------------------------------ conversation

    fun start(n: Npc) {
        n.pause = 10f
        n.yaw = atan2(p.x - n.s.x, -(p.z - n.s.z))
        open(n)
        val node = currentNode()
        val line = node?.let { lineOf(it, n) }.orEmpty().ifBlank { greeting(n) }
        present(n, line)
    }

    private fun open(n: Npc) {
        npc = n
        def = w.content.dialogueFor(n.def.id, n.def.profession)
        nodeId = def?.nodes?.firstOrNull()?.id.orEmpty()
    }

    fun end() {
        npc = null
        def = null
        nodeId = ""
        shown = emptyList()
        out.value = null
    }

    private fun currentNode(): DialogueNodeDef? {
        val d = def ?: return null
        return d.nodes.firstOrNull { it.id == nodeId } ?: d.nodes.firstOrNull()
    }

    private fun fill(text: String) = text.replace("{player}", p.name)

    private fun lineOf(node: DialogueNodeDef, n: Npc): String {
        for (l in node.lines) if (l.conditions.all { holds(it, n) }) return fill(l.text)
        return fill(node.text)
    }

    private fun saidFlag(d: DialogueDef, node: DialogueNodeDef, index: Int) = "said:${d.id}:${node.id}:$index"

    /** Shows [line] as what [n] says now, with whatever may be answered. */
    fun say(n: Npc, line: String) {
        if (npc !== n) open(n)
        present(n, line)
    }

    private fun present(n: Npc, line: String) {
        val d = def
        val node = currentNode()
        val replies = ArrayList<ReplyView>()
        if (d != null && node != null) {
            val indices = ArrayList<Int>()
            node.replies.forEachIndexed { i, r ->
                if (r.once && saidFlag(d, node, i) in save.flags) return@forEachIndexed
                if (!r.conditions.all { holds(it, n) }) return@forEachIndexed
                replies += ReplyView(indices.size, fill(r.text))
                indices += i
            }
            shown = indices
        } else {
            shown = emptyList()
            replies += ReplyView(0, "Talk a while")
        }
        out.value = DialogueView(n.def.id, line, replies, tradeNonce)
        w.events.tryEmit(UiEvent.Dialogue(n.def.id, line))
    }

    fun reply(index: Int) {
        val n = npc ?: return
        val d = def
        val node = currentNode()
        if (d == null || node == null) {
            if (index == 0) chat(n.def.id)
            return
        }
        val original = shown.getOrNull(index) ?: return
        val r: ReplyDef = node.replies.getOrNull(original) ?: return
        n.pause = 10f
        if (r.once) save.flags += saidFlag(d, node, original)
        var ended = false
        var spoken: String? = null
        for (o in r.outcomes) {
            when (o.type) {
                "affinity" -> warm(n, o.amount.toInt())
                "rumour" -> w.discovery.learnRumour(o.key, n.def.name)
                "give_item" -> {
                    val count = max(1, o.amount.toInt())
                    w.inventory.addItem(o.key, count)
                    w.toast("${n.def.name} gives you ${if (count > 1) "$count " else ""}${w.content.itemName(o.key)}.")
                }
                "take_item" -> w.inventory.removeByDef(o.key, max(1, o.amount.toInt()))
                "gold" -> {
                    p.gold = max(0, p.gold + o.amount.toInt())
                    w.packDirty = true
                }
                "trade" -> tradeNonce++
                "flag" -> save.flags += o.key
                "memory" -> remember(n, fill(o.text.ifBlank { o.key }))
                "skill_xp" -> w.progression.gainXp(o.key, o.amount)
                "small_talk" -> spoken = smallTalk(n)
                "end" -> ended = true
            }
        }
        if (ended) {
            end()
            w.events.tryEmit(UiEvent.EndDialogue)
            return
        }
        nodeId = d.nodes.firstOrNull { it.id == r.goto }?.id ?: d.nodes.first().id
        val next = currentNode() ?: node
        val line = fill(r.answer).ifBlank { spoken ?: lineOf(next, n) }.ifBlank { greeting(n) }
        present(n, line)
    }

    private fun inRange(value: Float, c: ConditionDef): Boolean {
        val lo = c.min
        val hi = c.max
        return (lo == null || value >= lo) && (hi == null || value <= hi)
    }

    /** Whether a condition written in a pack is true right now, for the person being spoken to. */
    fun holds(c: ConditionDef, n: Npc): Boolean {
        val result = when (c.type) {
            "affinity" -> inRange(n.s.affinity.toFloat(), c)
            "skill" -> inRange(w.progression.skill(c.key), c)
            "attribute" -> inRange(w.progression.attr(c.key).toFloat(), c)
            "level" -> inRange(p.level.toFloat(), c)
            "hour" -> {
                val h = Calendar.hour(save.minutes)
                val lo = c.min
                val hi = c.max
                // "From 20 to 5" wraps past midnight.
                if (lo != null && hi != null && lo > hi) h >= lo || h <= hi else inRange(h, c)
            }
            "gold" -> inRange(p.gold.toFloat(), c)
            "item" -> {
                val have = w.inventory.count(c.key).toFloat()
                if (c.min == null && c.max == null) have >= 1f else inRange(have, c)
            }
            "flag" -> c.key in save.flags
            "memory" -> n.s.memories.any { it.contains(c.key, ignoreCase = true) }
            "rumour" -> w.discovery.knowsRumour(c.key)
            "place" -> save.discovered.containsKey(w.content.locations.canonical(c.key))
            "relation" -> relationOf(n) == c.key
            else -> true
        }
        return result != c.negate
    }

    // ------------------------------------------------------------ small talk and gifts

    /** A passing remark. The first in a while warms the villager to the player. */
    private fun smallTalk(n: Npc): String {
        n.pause = 10f
        val options = ArrayList<String>()
        options += "I was ${n.s.activity.replaceFirstChar { it.lowercase() }} until you came by."
        if (n.s.hunger < 35f) options += "Forgive me, my stomach is louder than I am."
        if (n.s.fatigue < 30f) options += "I can barely keep my eyes open today."
        if (n.def.likes.isNotBlank()) options += "Nothing in the vale beats ${n.def.likes}."
        if (n.def.fears.isNotBlank()) options += "Between us? What keeps me up is ${n.def.fears}."
        n.s.memories.lastOrNull()?.let { options += "You heard? ${it.substringAfter(": ")}." }
        n.s.bonds.maxByOrNull { it.value }?.let { e -> w.npcById[e.key]?.let { o -> options += "${o.def.name} and I get on well these days." } }
        n.s.bonds.minByOrNull { it.value }?.takeIf { it.value < 0 }?.let { e -> w.npcById[e.key]?.let { o -> options += "Do not get me started on ${o.def.name}." } }
        if (save.weather == "mist") options += "This mist gets into the bones."
        var line = options[w.rng.nextInt(options.size)]
        if (save.minutes - n.s.lastChat > 180.0) {
            val prog = w.progression
            val gain = 2 + (prog.skill("speech") / 20f).toInt() + (if (n.trait("sociability") > 0.6f) 1 else 0) +
                Progression.affinityBonus(prog.attr("charm"))
            warm(n, max(1, gain))
            n.s.social = min(100f, n.s.social + 15f)
            n.s.lastChat = save.minutes
            prog.gainXp("speech", 2f)
            remember(n, "Talked with ${p.name}")
        } else {
            line += " ...But we spoke not long ago."
        }
        return line
    }

    fun chat(id: String) {
        val n = w.npcById[id] ?: return
        say(n, smallTalk(n))
    }

    fun gift(id: String, uid: String) {
        val n = w.npcById[id] ?: return
        val it = w.inventory.find(uid) ?: return
        val value = w.inventory.valueOf(it, worn = false)
        val gain = (value / 3 + 2).coerceIn(2, 12) + max(0, Progression.affinityBonus(w.progression.attr("charm")))
        val name = w.inventory.nameOf(it)
        w.inventory.removeItem(it, 1)
        warm(n, gain)
        remember(n, "Received $name from ${p.name}")
        say(n, if (n.trait("greed") > 0.6f) "For me? I will not say no." else "You did not have to. Thank you, truly.")
    }
}
