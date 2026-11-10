package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.JournalView
import app.rork.p7buzvrzcw3frddi8y50ou.ui.InkChip
import app.rork.p7buzvrzcw3frddi8y50ou.ui.KeyValue
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentCard
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentTopBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.Portrait
import app.rork.p7buzvrzcw3frddi8y50ou.ui.SectionTitle
import app.rork.p7buzvrzcw3frddi8y50ou.ui.StatBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.parchment
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink
import kotlin.math.roundToInt

private val cardPad = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontStyle = FontStyle.Italic,
        color = Ink.Muted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

private fun tint(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Ink.Leather
}

/** The player's book: who they are, where they have been, what they have heard and who they know. */
@Composable
fun JournalScreen(engine: GameEngine, onSettings: () -> Unit, onNpc: (String) -> Unit) {
    val journal by engine.journal.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("character") }
    var filter by rememberSaveable { mutableStateOf("all") }

    Column(Modifier.fillMaxSize().parchment()) {
        ParchmentTopBar("Journal", onSettings = onSettings)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val points = journal.character.points
            InkChip(if (points > 0) "Character ($points)" else "Character", section == "character", { section = "character" }, Icons.Filled.Person)
            InkChip("Places", section == "places", { section = "places" }, Icons.Filled.Place)
            InkChip("Rumours", section == "rumours", { section = "rumours" })
            InkChip("Bestiary", section == "bestiary", { section = "bestiary" }, Icons.Filled.Pets)
            InkChip("People", section == "people", { section = "people" }, Icons.Filled.Groups)
            InkChip("Chronicle", section == "chronicle", { section = "chronicle" }, Icons.Filled.AutoStories)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            when (section) {
                "places" -> places(journal)
                "rumours" -> rumours(journal)
                "bestiary" -> bestiary(journal)
                "people" -> people(journal, filter, { filter = it }, onNpc)
                "chronicle" -> chronicle(journal)
                else -> character(engine, journal)
            }
        }
    }
}

private fun LazyListScope.character(engine: GameEngine, journal: JournalView) {
    val c = journal.character
    item {
        ParchmentCard(cardPad.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Portrait(c.skin, c.hair, c.cloth, hat = false, beard = c.beard, longHair = c.longHair, modifier = Modifier.size(96.dp), bald = c.bald)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(journal.playerName.ifEmpty { "Wanderer" }, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(c.race, c.background).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge, color = Ink.Muted,
                    )
                    Text("${journal.dateLine} · ${journal.season}", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            }
            Spacer(Modifier.height(10.dp))
            StatBar(
                Icons.Filled.Star, "Level ${c.level}", c.levelProgress * 100f, 100f, Ink.Ember,
                trailing = "${(c.levelProgress * 100).roundToInt()}%",
            )
            Text(
                "Raising any skill by a rank moves you toward the next level. Each level grants an attribute point.",
                style = MaterialTheme.typography.bodySmall, color = Ink.Muted, modifier = Modifier.padding(top = 4.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Ink.Line)
            KeyValue("Health", "${c.maxHealth.roundToInt()}")
            KeyValue("Stamina", "${c.maxStamina.roundToInt()}")
            KeyValue("Magic", "${c.maxMagic.roundToInt()}")
            KeyValue("Can carry", "${c.capacity.roundToInt()} kg")
            KeyValue("Armour", "${c.armor.roundToInt()}")
        }
    }
    item {
        ParchmentCard(cardPad.fillMaxWidth()) {
            SectionTitle("Attributes")
            Text(
                when (c.points) {
                    0 -> "No points to spend. Level up to earn more."
                    1 -> "1 point to spend."
                    else -> "${c.points} points to spend."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (c.points > 0) Ink.LeatherDark else Ink.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            c.attributes.forEach { a ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium)
                        Text(a.effect, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Text("${a.base + a.bonus}", style = MaterialTheme.typography.titleLarge)
                        if (a.bonus != 0) {
                            Text(
                                "${a.base} ${if (a.bonus > 0) "+" else "−"} ${kotlin.math.abs(a.bonus)} worn",
                                style = MaterialTheme.typography.bodySmall, color = Ink.Moss,
                            )
                        }
                    }
                    if (c.points > 0) {
                        FilledIconButton(
                            onClick = { engine.submit(Command.SpendPoint(a.id)) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Ink.Leather, contentColor = Ink.ParchmentLight),
                        ) { Icon(Icons.Filled.Add, contentDescription = "Raise ${a.name}") }
                    }
                }
            }
        }
    }
    item {
        ParchmentCard(cardPad.fillMaxWidth()) {
            SectionTitle("Skills")
            Spacer(Modifier.height(8.dp))
            if (c.skills.isEmpty()) Text("Nothing practised yet.", color = Ink.Muted, fontStyle = FontStyle.Italic)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                c.skills.forEach { s ->
                    StatBar(
                        Icons.Filled.TrendingUp, s.name, s.progress * 100f, 100f, Ink.Leather,
                        labelWidth = 92.dp, trailing = "Rank ${s.rank}",
                    )
                }
            }
            Text(
                "Skills grow by doing. The bar shows how close each is to its next rank.",
                style = MaterialTheme.typography.bodySmall, color = Ink.Muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    item {
        ParchmentCard(cardPad.fillMaxWidth()) {
            SectionTitle("Needs")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBar(Icons.Filled.Restaurant, "Hunger", journal.hunger, 100f, needColor(journal.hunger))
                StatBar(Icons.Filled.Hotel, "Fatigue", journal.fatigue, 100f, needColor(journal.fatigue))
                StatBar(Icons.Filled.Weekend, "Comfort", journal.comfort, 100f, needColor(journal.comfort))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { engine.submit(Command.Wait(1)) }) {
                Icon(Icons.Filled.HourglassBottom, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pass an hour")
            }
        }
    }
}

private fun LazyListScope.places(journal: JournalView) {
    val found = journal.places.filter { it.discovered }
    val hidden = journal.places.size - found.size
    if (found.isEmpty()) item { EmptyNote("You have not found anywhere worth a name yet.") }
    items(found, key = { it.id }) { p ->
        ParchmentCard(cardPad.fillMaxWidth()) {
            Text(p.name, style = MaterialTheme.typography.titleLarge)
            if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyMedium)
            if (p.date.isNotBlank()) {
                Text("Found ${p.date}", style = MaterialTheme.typography.bodySmall, color = Ink.Muted, fontStyle = FontStyle.Italic)
            }
        }
    }
    if (hidden > 0) {
        item {
            Text(
                if (hidden == 1) "One place in the vale is still unfound. It shows as a hollow mark on the compass."
                else "$hidden places in the vale are still unfound. They show as hollow marks on the compass.",
                style = MaterialTheme.typography.bodyMedium, color = Ink.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

private fun LazyListScope.rumours(journal: JournalView) {
    if (journal.rumours.isEmpty()) {
        item { EmptyNote("No leads yet. Talk with people, read what you find, and listen.") }
    }
    items(journal.rumours) { r ->
        ParchmentCard(cardPad.fillMaxWidth()) {
            Text(r.title, style = MaterialTheme.typography.titleLarge)
            Text(r.text, style = MaterialTheme.typography.bodyMedium)
            val foot = listOf(r.source, r.date, r.place.takeIf { it.isNotBlank() }?.let { "Leads to $it" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (foot.isNotBlank()) {
                Text(foot, style = MaterialTheme.typography.bodySmall, color = Ink.Muted, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun LazyListScope.bestiary(journal: JournalView) {
    val known = journal.bestiary.filter { it.known }
    val unknown = journal.bestiary.size - known.size
    if (known.isEmpty()) item { EmptyNote("You have met no creatures yet. The meadow and the woods are not empty.") }
    items(known) { b ->
        ParchmentCard(cardPad.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(tint(b.color)))
                Spacer(Modifier.width(10.dp))
                Text(b.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (b.kills > 0) Text("${b.kills} slain", style = MaterialTheme.typography.titleSmall, color = Ink.Blood)
            }
            if (b.description.isNotBlank()) Text(b.description, style = MaterialTheme.typography.bodyMedium)
            if (b.date.isNotBlank()) {
                Text("First met ${b.date}", style = MaterialTheme.typography.bodySmall, color = Ink.Muted, fontStyle = FontStyle.Italic)
            }
        }
    }
    if (unknown > 0) {
        item {
            Text(
                if (unknown == 1) "One kind of creature is still unknown to you." else "$unknown kinds of creature are still unknown to you.",
                style = MaterialTheme.typography.bodyMedium, color = Ink.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

private fun LazyListScope.people(journal: JournalView, filter: String, onFilter: (String) -> Unit, onNpc: (String) -> Unit) {
    val shown = journal.npcs.filter { filter == "all" || it.relation == filter }
    item {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkChip("Everyone", filter == "all", { onFilter("all") })
            InkChip("Friends", filter == "friend", { onFilter("friend") })
            InkChip("Rivals", filter == "rival", { onFilter("rival") })
            InkChip("Family", filter == "family", { onFilter("family") })
        }
    }
    if (shown.isEmpty()) {
        item {
            EmptyNote(
                when (filter) {
                    "friend" -> "No friends yet. Talk with the villagers and bring them gifts."
                    "rival" -> "Nobody in the vale holds a grudge against you."
                    "family" -> "You have no kin in Greenhollow."
                    else -> "The village is empty."
                },
            )
        }
    }
    items(shown, key = { it.id }) { n ->
        Column(Modifier.clickable(onClickLabel = "Open ${n.def.name}") { onNpc(n.id) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NpcPortrait(n, Modifier.size(72.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("${n.def.name}, ${n.profession}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        n.activity, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                        color = Ink.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Filled.Favorite, contentDescription = "Affinity", tint = Ink.Leather, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${n.affinity}", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Ink.Line)
        }
    }
}

private fun LazyListScope.chronicle(journal: JournalView) {
    if (journal.history.isEmpty()) {
        item { EmptyNote("Nothing has been written down yet.") }
        return
    }
    item {
        ParchmentCard(cardPad.fillMaxWidth()) {
            journal.history.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}
