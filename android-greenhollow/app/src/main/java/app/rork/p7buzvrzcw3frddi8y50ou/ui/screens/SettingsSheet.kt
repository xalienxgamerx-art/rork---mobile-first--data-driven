package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rork.p7buzvrzcw3frddi8y50ou.GameViewModel
import app.rork.p7buzvrzcw3frddi8y50ou.Phase
import app.rork.p7buzvrzcw3frddi8y50ou.ui.InkChip
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentCard
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Ink.Leather,
                checkedThumbColor = Ink.ParchmentLight,
                uncheckedTrackColor = Ink.Track,
                uncheckedThumbColor = Ink.Muted,
                uncheckedBorderColor = Ink.Leather.copy(alpha = 0.5f),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(vm: GameViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val content by vm.content.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    var confirmNewLife by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPack(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.Parchment,
        contentColor = Ink.Text,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            ParchmentCard(Modifier.fillMaxWidth()) {
                Text("Graphics", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low", "Medium", "High").forEachIndexed { i, label ->
                        InkChip(label, settings.graphics == i, { vm.updateSettings(settings.copy(graphics = i)) })
                    }
                }
                Text(
                    "Controls draw distance, terrain detail and how many trees are drawn.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Muted,
                )
                ToggleRow(
                    "Adaptive resolution", "Lowers the render resolution when the frame rate drops.",
                    settings.adaptive,
                ) { vm.updateSettings(settings.copy(adaptive = it)) }
                ToggleRow("Show frame rate", "A small counter in the corner of the world.", settings.showFps) {
                    vm.updateSettings(settings.copy(showFps = it))
                }
            }

            ParchmentCard(Modifier.fillMaxWidth()) {
                Text("Controls", style = MaterialTheme.typography.titleMedium)
                ToggleRow("Left-handed layout", "Swaps the joystick and the action buttons.", settings.leftHanded) {
                    vm.updateSettings(settings.copy(leftHanded = it))
                }
                Row {
                    Text("Look sensitivity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("%.1fx".format(settings.sensitivity), style = MaterialTheme.typography.titleSmall)
                }
                Slider(
                    value = settings.sensitivity,
                    onValueChange = { vm.updateSettings(settings.copy(sensitivity = (it * 10).toInt() / 10f)) },
                    valueRange = 0.4f..2f,
                    modifier = Modifier.semantics { contentDescription = "Look sensitivity" },
                    colors = SliderDefaults.colors(
                        thumbColor = Ink.Leather, activeTrackColor = Ink.Leather, inactiveTrackColor = Ink.Track,
                    ),
                )
            }

            ParchmentCard(Modifier.fillMaxWidth()) {
                Text("Content packs", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The whole world is defined by packs. Add your own JSON pack to bring new materials, items, people, creatures and places.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Muted,
                )
                val registry = content
                if (registry == null) {
                    Text("Reading packs…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    registry.packs.forEach { p ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Ink.Line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.manifest.name} ${p.manifest.version}", style = MaterialTheme.typography.titleSmall)
                                val status = when {
                                    !p.enabled -> "Disabled"
                                    !p.loaded -> "Could not be loaded"
                                    else -> "${p.definitionCount} definitions"
                                }
                                val by = if (p.manifest.author.isNotBlank()) " · by ${p.manifest.author}" else ""
                                Text(
                                    status + by + if (p.builtin) " · built in" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p.enabled && !p.loaded) Ink.Blood else Ink.Muted,
                                )
                                if (p.manifest.description.isNotBlank()) {
                                    Text(p.manifest.description, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!p.builtin) {
                                    TextButton(
                                        onClick = { vm.removePack(p.manifest.id) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    ) { Text("Remove", color = Ink.Blood) }
                                }
                            }
                            if (p.manifest.id != "core") {
                                Switch(
                                    checked = p.enabled,
                                    onCheckedChange = { vm.setPackEnabled(p.manifest.id, it) },
                                    modifier = Modifier.semantics { contentDescription = "Enable ${p.manifest.name}" },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Ink.Leather,
                                        checkedThumbColor = Ink.ParchmentLight,
                                        uncheckedTrackColor = Ink.Track,
                                        uncheckedThumbColor = Ink.Muted,
                                    ),
                                )
                            }
                        }
                    }
                    if (registry.issues.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Ink.Line)
                        Text("Pack report", style = MaterialTheme.typography.titleSmall)
                        registry.issues.take(12).forEach { issue ->
                            Text(
                                "${issue.packId}: ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (issue.severity == "error") Ink.Blood else Ink.Muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    shape = CircleShape,
                ) { Text("Import a pack…", color = Ink.LeatherDark) }
            }

            if (phase is Phase.Playing) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.saveNow() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Ink.Leather, contentColor = Ink.ParchmentLight),
                    ) { Text("Save now", color = Ink.ParchmentLight) }
                    OutlinedButton(onClick = { confirmNewLife = true }, shape = CircleShape) {
                        Text("Begin a new life", color = Ink.Blood)
                    }
                }
                Text(
                    "The world saves itself every minute and whenever you leave the app.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Muted,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmNewLife) {
        AlertDialog(
            onDismissRequest = { confirmNewLife = false },
            containerColor = Ink.ParchmentLight,
            title = { Text("Begin a new life?") },
            text = { Text("This world, its people's memories and everything you carry will be lost for good.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewLife = false
                    onDismiss()
                    vm.newLife()
                }) { Text("Erase and begin", color = Ink.Blood) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewLife = false }) { Text("Keep this world", color = Ink.LeatherDark) }
            },
        )
    }
}
