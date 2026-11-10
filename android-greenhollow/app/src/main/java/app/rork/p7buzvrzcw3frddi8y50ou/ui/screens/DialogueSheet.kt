package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.ui.InkChip
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ItemGlyph
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentCard
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink

@Composable
private fun TradeRow(icon: String, title: String, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemGlyph(icon, Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.titleSmall, color = Ink.LeatherDark)
    }
    HorizontalDivider(color = Ink.Line)
}

/** Something long to read: a sign, a book, a notice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSheet(title: String, text: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.Parchment,
        contentColor = Ink.Text,
    ) {
        Column(
            Modifier.padding(horizontal = 22.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            HorizontalDivider(color = Ink.Line)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                text.split("\n").filter { it.isNotBlank() }.forEach {
                    Text(it.trim(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReplyRow(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(Ink.ParchmentLight.copy(alpha = 0.7f))
            .border(BorderStroke(1.dp, Ink.Leather.copy(alpha = 0.55f)), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ink.Leather, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Conversation with a villager: branching replies, gifts, and trade when they keep a stall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogueSheet(engine: GameEngine, npcId: String, line: String, onDismiss: () -> Unit) {
    val journal by engine.journal.collectAsStateWithLifecycle()
    val pack by engine.pack.collectAsStateWithLifecycle()
    val talk by engine.dialogue.collectAsStateWithLifecycle()
    val live = talk?.takeIf { it.npcId == npcId }
    val npc = journal.npcs.firstOrNull { it.id == npcId }
    var mode by rememberSaveable(npcId) { mutableStateOf("talk") }
    // An authored reply that opens trade bumps the nonce; show the wares when it changes.
    val nonce = live?.tradeNonce ?: 0
    var seenNonce by remember(npcId) { mutableIntStateOf(nonce) }
    LaunchedEffect(nonce) {
        if (nonce != seenNonce) {
            seenNonce = nonce
            mode = "buy"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.Parchment,
        contentColor = Ink.Text,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (npc == null) {
                Text("They have gone.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NpcPortrait(npc, Modifier.size(72.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${npc.def.name}, ${npc.profession}", style = MaterialTheme.typography.titleLarge)
                    Text(npc.activity, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Ink.Muted)
                }
                Icon(Icons.Filled.Favorite, contentDescription = "Affinity", tint = Ink.Leather, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("${npc.affinity}", style = MaterialTheme.typography.titleMedium)
            }
            ParchmentCard(Modifier.fillMaxWidth()) {
                Text("“${live?.line ?: line}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkChip("Talk", mode == "talk", { mode = "talk" })
                InkChip("Give a gift", mode == "gift", { mode = "gift" })
                if (npc.trader) {
                    InkChip("Buy", mode == "buy", { mode = "buy" })
                    InkChip("Sell", mode == "sell", { mode = "sell" })
                }
            }
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                when (mode) {
                    "gift" -> {
                        val gifts = pack.items.filter { !it.equipped }
                        if (gifts.isEmpty()) Text("You have nothing to give.", color = Ink.Muted)
                        gifts.forEach { item ->
                            TradeRow(item.icon, item.name, if (item.count > 1) "x${item.count}" else "Give") {
                                engine.submit(Command.Gift(npcId, item.uid))
                            }
                        }
                    }
                    "buy" -> {
                        Text("Your purse: ${pack.gold} gold", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
                        if (npc.wares.isEmpty()) Text("The shelves are bare today.", color = Ink.Muted)
                        npc.wares.forEach { w ->
                            TradeRow(w.icon, "${w.name} (${w.count} left)", "${w.price} g") {
                                engine.submit(Command.Buy(npcId, w.itemId))
                            }
                        }
                    }
                    "sell" -> {
                        Text("${npc.def.name} has ${npc.gold} gold to spend", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
                        val goods = pack.items.filter { !it.equipped }
                        if (goods.isEmpty()) Text("You have nothing to sell.", color = Ink.Muted)
                        goods.forEach { item ->
                            TradeRow(
                                item.icon,
                                if (item.count > 1) "${item.name} x${item.count}" else item.name,
                                "worth ${item.value} g",
                            ) { engine.submit(Command.Sell(npcId, item.uid)) }
                        }
                    }
                    else -> {
                        val replies = live?.replies.orEmpty()
                        if (replies.isEmpty()) {
                            Text(
                                "There is nothing more to say for now.",
                                style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Ink.Muted,
                            )
                        }
                        replies.forEach { r -> ReplyRow(r.text) { engine.submit(Command.Reply(r.index)) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
