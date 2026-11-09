package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.NpcView
import app.rork.p7buzvrzcw3frddi8y50ou.ui.InkChip
import app.rork.p7buzvrzcw3frddi8y50ou.ui.KeyValue
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentCard
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentTopBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.Portrait
import app.rork.p7buzvrzcw3frddi8y50ou.ui.SectionTitle
import app.rork.p7buzvrzcw3frddi8y50ou.ui.StatBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.SwordGlyph
import app.rork.p7buzvrzcw3frddi8y50ou.ui.parchment
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink
import kotlin.math.roundToInt

private fun needColor(v: Float): Color = if (v < 50f) Ink.Ember else Ink.Moss

@Composable
fun NpcPortrait(n: NpcView, modifier: Modifier = Modifier) {
    Portrait(n.skin, n.def.hair, n.cloth, n.hat, n.def.beard, n.def.longHair, modifier)
}

@Composable
fun JournalScreen(engine: GameEngine, onSettings: () -> Unit, onNpc: (String) -> Unit) {
    val journal by engine.journal.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("friend") }
    val shown = journal.npcs.filter { filter == "all" || it.relation == filter }

    Column(Modifier.fillMaxSize().parchment()) {
        ParchmentTopBar("Journal", onSettings = onSettings)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InkChip("Friends", filter == "friend", { filter = "friend" }, Icons.Filled.Groups)
                    InkChip("Rivals", filter == "rival", { filter = "rival" }, Icons.Filled.Shield)
                    InkChip("Family", filter == "family", { filter = "family" }, Icons.Filled.Home)
                    InkChip("Everyone", filter == "all", { filter = "all" })
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        when (filter) {
                            "friend" -> "No friends yet. Talk with the villagers and bring them gifts."
                            "rival" -> "Nobody in the vale holds a grudge against you."
                            "family" -> "You have no kin in Greenhollow."
                            else -> "The village is empty."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = Ink.Muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
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
                            Text(
                                "${n.def.name}, ${n.profession}",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                n.activity,
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = Ink.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Filled.Favorite, contentDescription = "Affinity", tint = Ink.Leather, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${n.affinity}", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Ink.Line)
                }
            }
            item {
                ParchmentCard(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                    SectionTitle("Needs")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBar(Icons.Filled.Restaurant, "Hunger", journal.hunger, 100f, needColor(journal.hunger))
                        StatBar(Icons.Filled.Hotel, "Fatigue", journal.fatigue, 100f, needColor(journal.fatigue))
                        StatBar(Icons.Filled.Weekend, "Comfort", journal.comfort, 100f, needColor(journal.comfort))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${journal.dateLine} · ${journal.season}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { engine.submit(Command.Wait(1)) }) {
                            Icon(Icons.Filled.HourglassBottom, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pass an hour")
                        }
                    }
                }
            }
            if (journal.skills.isNotEmpty()) {
                item {
                    ParchmentCard(Modifier.padding(horizontal = 14.dp)) {
                        SectionTitle("Skills")
                        Spacer(Modifier.height(6.dp))
                        journal.skills.forEach { (name, value) ->
                            KeyValue(name.replaceFirstChar { it.uppercase() }, "${value.roundToInt()}")
                        }
                    }
                }
            }
            if (journal.history.isNotEmpty()) {
                item {
                    ParchmentCard(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                        SectionTitle("Chronicle")
                        Spacer(Modifier.height(6.dp))
                        journal.history.forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpcDetailScreen(engine: GameEngine, npcId: String, onBack: () -> Unit) {
    val journal by engine.journal.collectAsStateWithLifecycle()
    val n = journal.npcs.firstOrNull { it.id == npcId }
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.parchment(),
        topBar = {
            TopAppBar(
                title = { Text(n?.def?.name ?: "Unknown", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink.LeatherDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { inner ->
        if (n == null) {
            Text(
                "This person is no longer part of the world. Their content pack may be disabled.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(inner).padding(20.dp),
            )
            return@Scaffold
        }
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NpcPortrait(n, Modifier.size(112.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("${n.profession} · ${n.race}", style = MaterialTheme.typography.titleMedium)
                    Text("${n.def.age} winters", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(n.activity, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.relation == "rival") {
                            SwordGlyph(Ink.Blood, Modifier.size(18.dp), crossed = true)
                        } else {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Ink.Leather, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${n.affinity} · ${n.relation.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            ParchmentCard {
                SectionTitle("Needs")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBar(Icons.Filled.Restaurant, "Hunger", n.hunger, 100f, needColor(n.hunger))
                    StatBar(Icons.Filled.Hotel, "Rest", n.fatigue, 100f, needColor(n.fatigue))
                    StatBar(Icons.Filled.Forum, "Company", n.social, 100f, needColor(n.social))
                }
            }
            ParchmentCard {
                SectionTitle("Life")
                Spacer(Modifier.height(6.dp))
                KeyValue("Home", n.home.ifEmpty { "None" })
                KeyValue("Works at", n.work.ifEmpty { "Nowhere" })
                KeyValue("Purse", "${n.gold} gold")
                if (n.def.likes.isNotBlank()) KeyValue("Loves", n.def.likes)
                if (n.def.fears.isNotBlank()) KeyValue("Fears", n.def.fears)
            }
            if (n.def.personality.isNotEmpty()) {
                ParchmentCard {
                    SectionTitle("Character")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        n.def.personality.forEach { (trait, value) ->
                            StatBar(
                                Icons.Filled.MenuBook, trait.replaceFirstChar { it.uppercase() }, value * 100f, 100f, Ink.Leather,
                                labelWidth = 92.dp, trailing = "${(value * 100).roundToInt()}",
                            )
                        }
                    }
                }
            }
            if (n.bonds.isNotEmpty()) {
                ParchmentCard {
                    SectionTitle("Bonds")
                    Spacer(Modifier.height(6.dp))
                    n.bonds.forEach { (name, value) ->
                        KeyValue(name, if (value >= 0) "Fond ($value)" else "At odds ($value)")
                    }
                }
            }
            ParchmentCard {
                SectionTitle("Remembers")
                Spacer(Modifier.height(6.dp))
                if (n.memories.isEmpty()) {
                    Text("Nothing of note yet.", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Ink.Muted)
                }
                n.memories.take(10).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}
