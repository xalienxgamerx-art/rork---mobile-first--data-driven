package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Appearance
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Progression
import app.rork.p7buzvrzcw3frddi8y50ou.ui.InkChip
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentCard
import app.rork.p7buzvrzcw3frddi8y50ou.ui.Portrait
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.rork.p7buzvrzcw3frddi8y50ou.data.ContentRegistry
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentTopBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.SectionTitle
import app.rork.p7buzvrzcw3frddi8y50ou.ui.parchment
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink

@Composable
private fun ChoiceCard(title: String, body: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Ink.Surface else Ink.ParchmentLight.copy(alpha = 0.6f))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Ink.Leather else Ink.Leather.copy(alpha = 0.45f)), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
        if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Ink.Muted, fontStyle = FontStyle.Italic)
        }
    }
}

private fun bonus(label: String, v: Float): String? = if (v != 0f) "${if (v > 0) "+" else ""}${v.toInt()} $label" else null

private val HAIR_COLOURS = listOf(
    "#3B291F" to "Dark brown", "#1E1A17" to "Black", "#7A4A28" to "Chestnut",
    "#B5622E" to "Copper", "#C9A45C" to "Straw", "#9A958C" to "Grey",
)

private val CLOTH_COLOURS = listOf(
    "#5E6B45" to "Moss", "#7A4B2A" to "Russet", "#4F5F73" to "Slate blue",
    "#8A3A2E" to "Madder red", "#8C7A56" to "Undyed wool", "#4A3F57" to "Plum",
)

private fun hexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFFD9A77F)
}

@Composable
private fun Swatch(hex: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 38.dp else 32.dp)
                .clip(CircleShape)
                .background(hexColor(hex))
                .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Ink.Text else Ink.Leather.copy(alpha = 0.6f)), CircleShape),
        )
    }
}

@Composable
private fun SwatchRow(label: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (hex, name) -> Swatch(hex, "$label: $name", hex == selected) { onPick(hex) } }
    }
}

@Composable
fun CreationScreen(
    content: ContentRegistry,
    onSettings: () -> Unit,
    onBegin: (String, String, String, Appearance, Map<String, Int>) -> Unit,
) {
    val races = content.races.values.toList()
    val backgrounds = content.backgrounds.values.toList()
    var name by rememberSaveable { mutableStateOf("") }
    var raceId by rememberSaveable { mutableStateOf(races.firstOrNull()?.id.orEmpty()) }
    var backgroundId by rememberSaveable { mutableStateOf(backgrounds.firstOrNull()?.id.orEmpty()) }
    var skinIndex by rememberSaveable { mutableIntStateOf(0) }
    var hair by rememberSaveable { mutableStateOf(HAIR_COLOURS.first().first) }
    var hairStyle by rememberSaveable { mutableStateOf("short") }
    var beard by rememberSaveable { mutableStateOf(false) }
    var build by rememberSaveable { mutableFloatStateOf(1f) }
    var cloth by rememberSaveable { mutableStateOf(CLOTH_COLOURS.first().first) }
    var spent by rememberSaveable { mutableStateOf(List(Progression.ATTRIBUTES.size) { 0 }) }
    val race = races.firstOrNull { it.id == raceId }
    val tones = race?.skinTones.orEmpty().ifEmpty { listOf("#D9A77F") }
    val skin = tones[skinIndex.coerceIn(0, tones.size - 1)]
    val left = Progression.STARTING_POINTS - spent.sum()

    Column(Modifier.fillMaxSize().parchment().imePadding()) {
        ParchmentTopBar("A New Life", icon = Icons.Filled.HistoryEdu, onSettings = onSettings)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "The road into Greenhollow is long, and nobody there knows your name yet. Write it down.",
                style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("People", Modifier.padding(top = 6.dp))
            if (races.isEmpty()) Text("No peoples are defined by the enabled packs.", color = Ink.Blood)
            races.forEach { r ->
                val detail = listOfNotNull(
                    bonus("health", r.healthBonus), bonus("stamina", r.staminaBonus), bonus("magic", r.magicBonus),
                ).joinToString(" · ")
                val traits = r.attributes.filterValues { it != 0 }.map { (k, v) -> "${if (v > 0) "+" else ""}$v ${Progression.name(k)}" }
                ChoiceCard(r.name, r.description, (traits + detail).filter { it.isNotBlank() }.joinToString(" · "), r.id == raceId) {
                    raceId = r.id
                    skinIndex = 0
                }
            }

            SectionTitle("Appearance", Modifier.padding(top = 6.dp))
            ParchmentCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Portrait(
                        skin, hair, cloth, hat = false, beard = beard, longHair = hairStyle == "long",
                        modifier = Modifier.size(width = (96 * build).dp, height = 112.dp),
                        bald = hairStyle == "bald",
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name.trim().ifEmpty { "Wanderer" }, style = MaterialTheme.typography.titleLarge)
                        Text(
                            listOfNotNull(race?.name, backgrounds.firstOrNull { it.id == backgroundId }?.name).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = Ink.Muted,
                        )
                        Text(
                            when {
                                build < 0.97f -> "Slight of build"
                                build > 1.03f -> "Broad of build"
                                else -> "Of middling build"
                            },
                            style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SwatchRow("Skin", tones.mapIndexed { i, t -> t to "tone ${i + 1}" }, skin) { skinIndex = tones.indexOf(it).coerceAtLeast(0) }
                SwatchRow("Hair colour", HAIR_COLOURS, hair) { hair = it }
                Text("Hair", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkChip("Short", hairStyle == "short", { hairStyle = "short" })
                    InkChip("Long", hairStyle == "long", { hairStyle = "long" })
                    InkChip("Bald", hairStyle == "bald", { hairStyle = "bald" })
                    InkChip("Beard", beard, { beard = !beard })
                }
                SwatchRow("Clothes", CLOTH_COLOURS, cloth) { cloth = it }
                Text("Build", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = build,
                    onValueChange = { build = it },
                    valueRange = 0.9f..1.1f,
                    modifier = Modifier.semantics { contentDescription = "Build, slight to broad" },
                    colors = SliderDefaults.colors(thumbColor = Ink.Leather, activeTrackColor = Ink.Leather, inactiveTrackColor = Ink.Track),
                )
            }

            SectionTitle("Attributes", Modifier.padding(top = 6.dp))
            ParchmentCard(Modifier.fillMaxWidth()) {
                Text(
                    if (left > 0) "$left points left to place. Unplaced points can be spent later in the Journal." else "All points placed.",
                    style = MaterialTheme.typography.bodyMedium, color = if (left > 0) Ink.LeatherDark else Ink.Muted,
                )
                Progression.ATTRIBUTES.forEachIndexed { i, a ->
                    val value = Progression.BASE_ATTRIBUTE + (race?.attributes?.get(a) ?: 0) + spent[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Progression.name(a), style = MaterialTheme.typography.titleSmall)
                            Text(Progression.effect(a), style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                        }
                        IconButton(
                            onClick = { spent = spent.toMutableList().also { it[i] = it[i] - 1 } },
                            enabled = spent[i] > 0,
                        ) { Icon(Icons.Filled.Remove, contentDescription = "Lower ${Progression.name(a)}", tint = if (spent[i] > 0) Ink.LeatherDark else Ink.Line) }
                        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                        IconButton(
                            onClick = { spent = spent.toMutableList().also { it[i] = it[i] + 1 } },
                            enabled = left > 0,
                        ) { Icon(Icons.Filled.Add, contentDescription = "Raise ${Progression.name(a)}", tint = if (left > 0) Ink.LeatherDark else Ink.Line) }
                    }
                }
            }
            SectionTitle("Background", Modifier.padding(top = 6.dp))
            if (backgrounds.isEmpty()) Text("No backgrounds are defined by the enabled packs.", color = Ink.Blood)
            backgrounds.forEach { b ->
                val carry = b.items.joinToString(", ") { s ->
                    val n = content.itemName(s.item)
                    if (s.count > 1) "$n x${s.count}" else n
                }
                val practised = b.skills.keys.joinToString(", ") { Progression.name(it) }
                val detail = listOfNotNull(
                    carry.takeIf { it.isNotBlank() }?.let { "Carries $it" },
                    "${b.gold} gold",
                    practised.takeIf { it.isNotBlank() }?.let { "Practised in $it" },
                ).joinToString(" · ")
                ChoiceCard(b.name, b.description, detail, b.id == backgroundId) { backgroundId = b.id }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Button(
                onClick = {
                    onBegin(
                        name.trim().ifEmpty { "Wanderer" }, raceId, backgroundId,
                        Appearance(skin = skin, hair = hair, hairStyle = hairStyle, beard = beard, build = build, cloth = cloth),
                        Progression.ATTRIBUTES.mapIndexed { i, a -> a to spent[i] }.toMap(),
                    )
                },
                enabled = raceId.isNotEmpty() && backgroundId.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Leather, contentColor = Ink.ParchmentLight),
            ) {
                Text("Walk into Greenhollow", style = MaterialTheme.typography.titleMedium, color = Ink.ParchmentLight)
            }
        }
    }
}
