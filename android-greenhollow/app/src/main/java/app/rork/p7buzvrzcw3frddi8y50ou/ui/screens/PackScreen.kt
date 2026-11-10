package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ItemView
import app.rork.p7buzvrzcw3frddi8y50ou.ui.InkChip
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ItemGlyph
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentCard
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentTopBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.StatBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.SwordGlyph
import app.rork.p7buzvrzcw3frddi8y50ou.ui.parchment
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink
import kotlin.math.roundToInt

@Composable
fun PackScreen(engine: GameEngine, onSettings: () -> Unit) {
    val pack by engine.pack.collectAsStateWithLifecycle()
    var slot by rememberSaveable { mutableStateOf("") }
    var selectedUid by rememberSaveable { mutableStateOf("") }
    // Both trinket slots take the same kind of item.
    val wanted = if (slot.startsWith("trinket")) "trinket" else slot
    val shown = pack.items.filter { slot.isEmpty() || it.slot == wanted }
    val active = pack.slots.firstOrNull { it.id == slot }
    val selected = shown.firstOrNull { it.uid == selectedUid } ?: shown.firstOrNull()

    Column(Modifier.fillMaxSize().parchment()) {
        ParchmentTopBar("Pack", icon = Icons.Filled.Backpack, onSettings = onSettings)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pack.slots.forEach { s ->
                InkChip(
                    s.label, slot == s.id, { slot = if (slot == s.id) "" else s.id },
                    when (s.id) {
                        "head" -> Icons.Filled.SportsMotorsports
                        "chest" -> Icons.Filled.Checkroom
                        "off_hand" -> Icons.Filled.Shield
                        else -> null
                    },
                )
            }
        }
        if (active != null) {
            Text(
                active.item?.let { "${active.label}: wearing $it" } ?: "${active.label}: nothing worn",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 4.dp),
            )
        }
        Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 6.dp)) {
            Text("${pack.gold} gold", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "Burden ${"%.1f".format(pack.burden)} / ${pack.capacity.roundToInt()} kg",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pack.burden > pack.capacity) Ink.Blood else Ink.Muted,
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (slot.isEmpty()) "Your pack is empty." else "Nothing in your pack fits there.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = Ink.Muted,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.uid }) { item ->
                    ItemCell(item, item.uid == selected?.uid) { selectedUid = item.uid }
                }
            }
        }
        if (selected != null) ItemDetail(engine, selected)
    }
}

@Composable
private fun ItemCell(item: ItemView, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .aspectRatio(0.92f)
            .clip(shape)
            .background(if (selected) Ink.Surface else Ink.ParchmentLight.copy(alpha = 0.6f))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Ink.Leather else Ink.Line), shape)
            .clickable(role = Role.Button, onClickLabel = "Select ${item.name}", onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            ItemGlyph(item.icon, Modifier.size(40.dp))
            if (item.equipped) {
                Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(Ink.Moss))
            } else if (item.blocked.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(Ink.Blood))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (item.count > 1) "${item.name}\nx${item.count}" else item.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
        )
    }
}

@Composable
private fun ItemDetail(engine: GameEngine, item: ItemView) {
    ParchmentCard(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(Ink.Surface),
                contentAlignment = Alignment.Center,
            ) { ItemGlyph(item.icon, Modifier.size(52.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.slot.isNotEmpty()) {
                    StatBar(
                        Icons.Filled.Favorite, "Condition", item.condition * 100f, 100f, Ink.Moss,
                        labelWidth = 76.dp, trailing = "${(item.condition * 100).roundToInt()}%",
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Ink.LeatherDark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Weight", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${"%.1f".format(item.weight)} kg", style = MaterialTheme.typography.titleSmall)
                }
                val line = buildList {
                    when (item.weaponType) {
                        "two_handed" -> add("Two-handed")
                        "dagger" -> add("Dagger")
                        "one_handed" -> add("One-handed")
                    }
                    if (item.armorClass.isNotEmpty()) add("${item.armorClass.replaceFirstChar { it.uppercase() }} armour")
                    if (item.damage > 0f) add("Damage ${"%.1f".format(item.damage)}")
                    if (item.damage > 0f) add("Speed ${"%.1f".format(item.speed)}")
                    if (item.reach > 0f) add("Reach ${"%.2f".format(item.reach)} m")
                    if (item.armor > 0f) add("Armour ${"%.0f".format(item.armor)}")
                    item.forged?.let { add("Handling ${it.stats.handling.roundToInt()}") }
                    add("Worth ${item.value} gold")
                }.joinToString(" · ")
                Text(line, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
            }
        }
        if (item.bonuses.isNotEmpty()) {
            Text("Grants ${item.bonuses.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = Ink.Moss, modifier = Modifier.padding(top = 6.dp))
        }
        if (item.requirements.isNotEmpty()) {
            Text(
                "Needs ${item.requirements.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.blocked.isNotEmpty()) Ink.Blood else Ink.Muted,
            )
        }
        if (item.blocked.isNotEmpty()) {
            Text(item.blocked, style = MaterialTheme.typography.bodyMedium, color = Ink.Blood)
        }
        if (item.compare.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Against ${item.comparedTo.ifEmpty { "what you wear" }}", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item.compare.forEach { c ->
                    Text(
                        "${c.label} ${c.value} ${if (c.delta > 0f) "▲" else if (c.delta < 0f) "▼" else "="}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (c.delta > 0f) Ink.Moss else if (c.delta < 0f) Ink.Blood else Ink.Muted,
                    )
                }
            }
        }
        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(item.description, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                item.slot.isNotEmpty() -> Button(
                    onClick = { engine.submit(Command.ToggleEquip(item.uid)) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = CircleShape,
                ) {
                    if (item.slot == "main_hand") {
                        SwordGlyph(Ink.ParchmentLight, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (item.equipped) "Unequip" else "Equip", style = MaterialTheme.typography.titleMedium, color = Ink.ParchmentLight)
                }
                item.usable -> Button(
                    onClick = { engine.submit(Command.UseItem(item.uid)) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        when (item.type) { "food" -> "Eat"; "potion" -> "Drink"; else -> "Read" },
                        style = MaterialTheme.typography.titleMedium, color = Ink.ParchmentLight,
                    )
                }
                else -> Spacer(Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { engine.submit(Command.Drop(item.uid)) },
                modifier = Modifier.height(50.dp),
                shape = CircleShape,
                border = BorderStroke(1.2.dp, Ink.Leather),
            ) { Text("Drop", style = MaterialTheme.typography.titleSmall) }
        }
    }
}
