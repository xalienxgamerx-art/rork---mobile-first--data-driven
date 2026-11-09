package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun CreationScreen(content: ContentRegistry, onSettings: () -> Unit, onBegin: (String, String, String) -> Unit) {
    val races = content.races.values.toList()
    val backgrounds = content.backgrounds.values.toList()
    var name by rememberSaveable { mutableStateOf("") }
    var raceId by rememberSaveable { mutableStateOf(races.firstOrNull()?.id.orEmpty()) }
    var backgroundId by rememberSaveable { mutableStateOf(backgrounds.firstOrNull()?.id.orEmpty()) }

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
                ChoiceCard(r.name, r.description, detail, r.id == raceId) { raceId = r.id }
            }
            SectionTitle("Background", Modifier.padding(top = 6.dp))
            if (backgrounds.isEmpty()) Text("No backgrounds are defined by the enabled packs.", color = Ink.Blood)
            backgrounds.forEach { b ->
                val carry = b.items.joinToString(", ") { s ->
                    val n = content.itemName(s.item)
                    if (s.count > 1) "$n x${s.count}" else n
                }
                val detail = listOfNotNull(
                    carry.takeIf { it.isNotBlank() }?.let { "Carries $it" },
                    "${b.gold} gold",
                ).joinToString(" · ")
                ChoiceCard(b.name, b.description, detail, b.id == backgroundId) { backgroundId = b.id }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Button(
                onClick = { onBegin(name.trim().ifEmpty { "Wanderer" }, raceId, backgroundId) },
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
