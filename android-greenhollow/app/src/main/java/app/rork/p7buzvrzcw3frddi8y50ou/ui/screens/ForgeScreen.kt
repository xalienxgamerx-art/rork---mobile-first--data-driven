package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rork.p7buzvrzcw3frddi8y50ou.ForgeSession
import app.rork.p7buzvrzcw3frddi8y50ou.data.MaterialDef
import app.rork.p7buzvrzcw3frddi8y50ou.data.parseHex
import app.rork.p7buzvrzcw3frddi8y50ou.sim.BladeGeometry
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.ForgePhysics
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.ui.InkChip
import app.rork.p7buzvrzcw3frddi8y50ou.ui.KeyValue
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentCard
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentTopBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.parchment
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

private fun heatColor(t: Float, base: Color): Color = when {
    t < 420f -> base
    t < 700f -> lerp(base, Color(0xFFB0301A), (t - 420f) / 280f)
    t < 950f -> lerp(Color(0xFFB0301A), Color(0xFFF07A1E), (t - 700f) / 250f)
    t < 1200f -> lerp(Color(0xFFF07A1E), Color(0xFFFFD257), (t - 950f) / 250f)
    else -> lerp(Color(0xFFFFD257), Color(0xFFFFF6D8), ((t - 1200f) / 250f).coerceAtMost(1f))
}

@Composable
fun ForgeScreen(engine: GameEngine, forge: ForgeSession, onSettings: () -> Unit) {
    val pack by engine.pack.collectAsStateWithLifecycle()
    val materials = engine.content.materials
    val material = materials[forge.materialId] ?: materials.values.firstOrNull()
    val haptics = LocalHapticFeedback.current
    var showMore by rememberSaveable { mutableStateOf(false) }
    val recoil = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            forge.cool(0.1f)
        }
    }
    LaunchedEffect(forge.strike) {
        if (forge.strike > 0) {
            recoil.snapTo(1f)
            recoil.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }

    Column(Modifier.fillMaxSize().parchment()) {
        ParchmentTopBar("Forge", icon = Icons.Filled.Handyman, onSettings = onSettings)
        if (material == null) {
            Text(
                "No workable materials are defined by the enabled content packs.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }
        val g = forge.geometry
        val need = ForgePhysics.ingotsNeeded(g, material)
        val have = pack.items.filter { it.defId == material.itemId }.sumOf { it.count }
        val stats = forge.stats(material)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                materials.values.forEach { m ->
                    InkChip(m.name, m.id == material.id, {
                        if (!forge.locked) forge.materialId = m.id else forge.note = "The bar is already on the anvil."
                    })
                }
            }
            BladeOnAnvil(g, material, forge.temperature, forge.progress, recoil.value, forge.quenched)
            HeatGauge(forge.temperature, material)
            Text(forge.note, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
            Text(
                "Needs $need ${engine.content.itemName(material.itemId)} · you carry $have",
                style = MaterialTheme.typography.bodyMedium,
                color = if (have < need) Ink.Blood else Ink.Muted,
            )

            if (!forge.quenched) {
                ParchmentCard {
                    val enabled = !forge.locked
                    DesignSlider("Blade length", "${g.length.roundToInt()} cm", g.length, 25f..125f, enabled) {
                        forge.geometry = g.copy(length = it.roundToInt().toFloat())
                    }
                    DesignSlider("Width", "${"%.1f".format(g.width)} cm", g.width, 2.5f..7f, enabled) {
                        forge.geometry = g.copy(width = (it * 10).roundToInt() / 10f)
                    }
                    DesignSlider("Thickness", "${"%.1f".format(g.thickness)} mm", g.thickness, 3f..9f, enabled) {
                        forge.geometry = g.copy(thickness = (it * 2).roundToInt() / 2f)
                    }
                    DesignSlider("Curvature", "${g.curvature.roundToInt()} deg", g.curvature, 0f..25f, enabled) {
                        forge.geometry = g.copy(curvature = it.roundToInt().toFloat())
                    }
                    TextButton(onClick = { showMore = !showMore }) {
                        Text(if (showMore) "Hide hilt and edge" else "Hilt, taper and edge")
                    }
                    if (showMore) {
                        DesignSlider("Distal taper", "${(g.taper * 100).roundToInt()}%", g.taper, 0f..1f, enabled) {
                            forge.geometry = g.copy(taper = (it * 20).roundToInt() / 20f)
                        }
                        DesignSlider("Handle", "${g.handle.roundToInt()} cm", g.handle, 9f..40f, enabled) {
                            forge.geometry = g.copy(handle = it.roundToInt().toFloat())
                        }
                        DesignSlider("Pommel", "${g.pommel.roundToInt()} g", g.pommel, 0f..450f, enabled) {
                            forge.geometry = g.copy(pommel = (it / 10).roundToInt() * 10f)
                        }
                        DesignSlider("Edge angle", "${g.edgeAngle.roundToInt()} deg", g.edgeAngle, 18f..50f, enabled) {
                            forge.geometry = g.copy(edgeAngle = it.roundToInt().toFloat())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fuller groove", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = g.fuller, enabled = enabled, onCheckedChange = { forge.geometry = g.copy(fuller = it) })
                        }
                    }
                    if (!enabled) {
                        Text(
                            "The shape is set once the first blow lands.",
                            style = MaterialTheme.typography.bodySmall, color = Ink.Muted,
                        )
                    }
                }
            } else {
                ParchmentCard {
                    DesignSlider(
                        "Temper", if (forge.temper < 0.35f) "Hard, brittle" else if (forge.temper > 0.7f) "Tough, soft" else "Balanced",
                        forge.temper, 0f..1f, true,
                    ) { forge.temper = it }
                    OutlinedTextField(
                        value = forge.name,
                        onValueChange = { forge.name = it.take(28) },
                        label = { Text("Name the blade") },
                        placeholder = { Text("${material.name} ${ForgePhysics.className(g)}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ParchmentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ForgePhysics.className(g), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (forge.blows > 0) ForgePhysics.qualityName(forge.quality()) else "Projected",
                        style = MaterialTheme.typography.titleSmall, color = Ink.Leather,
                    )
                }
                Spacer(Modifier.height(4.dp))
                KeyValue("Mass", "${"%.2f".format(stats.mass)} kg")
                KeyValue("Balance", "${"%.1f".format(stats.balance)} cm from guard")
                KeyValue("Edge retention", "${stats.edgeRetention.roundToInt()}")
                KeyValue("Handling", "${stats.handling.roundToInt()}")
                KeyValue("Cutting", "${stats.cutting.roundToInt()}")
                KeyValue("Thrust", "${stats.thrust.roundToInt()}")
                KeyValue("Integrity", "${stats.integrity.roundToInt()}")
                KeyValue("Reach", "${"%.2f".format(stats.reach)} m")
                KeyValue("Damage", "%.1f".format(stats.damage))
            }
            Spacer(Modifier.height(4.dp))
        }

        // Process actions stay pinned above the tab bar.
        Row(
            Modifier.fillMaxWidth().background(Ink.Parchment.copy(alpha = 0.9f)).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!forge.quenched) {
                ForgeButton("Heat", Icons.Filled.LocalFireDepartment, Ink.Ember, Modifier.weight(1f)) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    forge.heat(material)
                }
                ForgeButton("Hammer", Icons.Filled.Gavel, Ink.Leather, Modifier.weight(1f)) {
                    if (forge.blows == 0 && have < need) {
                        forge.note = "You need $need ${engine.content.itemName(material.itemId)} for a blade this size."
                    } else if (forge.hammer(material, pack.smithing)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                ForgeButton("Quench", Icons.Filled.WaterDrop, Color(0xFF4F7480), Modifier.weight(1f)) {
                    if (forge.quench(material)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } else {
                ForgeButton("Scrap", Icons.Filled.WaterDrop, Ink.Muted, Modifier.weight(0.6f)) { forge.reset() }
                ForgeButton("Finish blade", Icons.Filled.Handyman, Ink.Leather, Modifier.weight(1f)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    engine.submit(Command.AddForged(forge.finish(material), material.itemId, need))
                    forge.reset()
                }
            }
        }
    }
}

@Composable
private fun ForgeButton(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Ink.ParchmentLight),
        border = BorderStroke(1.dp, Ink.Text.copy(alpha = 0.25f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink.ParchmentLight, maxLines = 1)
    }
}

@Composable
private fun DesignSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.height(36.dp).semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = Ink.Leather,
                activeTrackColor = Ink.Leather,
                inactiveTrackColor = Ink.Track,
                disabledThumbColor = Ink.Muted,
                disabledActiveTrackColor = Ink.Muted.copy(alpha = 0.6f),
            ),
        )
    }
}

@Composable
private fun HeatGauge(temperature: Float, m: MaterialDef) {
    Column {
        Row {
            Text("Heat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${temperature.roundToInt()} °C", style = MaterialTheme.typography.titleSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(16.dp).padding(top = 4.dp)) {
            val max = 1500f
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Ink.Track, size = size, cornerRadius = r)
            // working window for this material
            val a = ((m.forgeTemp - 90f) / max).coerceIn(0f, 1f) * size.width
            val b = ((m.forgeTemp + 90f) / max).coerceIn(0f, 1f) * size.width
            drawRect(Ink.Moss.copy(alpha = 0.55f), Offset(a, 0f), Size(b - a, size.height))
            val w = (temperature / max).coerceIn(0f, 1f) * size.width
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF7A3A1E), Color(0xFFD9772B), Color(0xFFFFD257)), 0f, size.width),
                size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r,
            )
            drawLine(Ink.Text, Offset(a, 0f), Offset(a, size.height), 1.5.dp.toPx())
            drawLine(Ink.Text, Offset(b, 0f), Offset(b, size.height), 1.5.dp.toPx())
        }
    }
}

/** The work piece. Its outline is generated from the same geometry the physics uses. */
@Composable
private fun BladeOnAnvil(g: BladeGeometry, m: MaterialDef, temperature: Float, progress: Float, recoil: Float, quenched: Boolean) {
    val base = Color(parseHex(m.color, 0xFF9A9A9A.toInt()))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1D14), Color(0xFF3B2718), Color(0xFF1C140E))))
            .semantics { contentDescription = "Blade on the anvil, ${temperature.roundToInt()} degrees" },
    ) {
        val w = size.width
        val h = size.height
        val hot = ((temperature - 420f) / 700f).coerceIn(0f, 1f)
        // forge glow
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFD9772B).copy(alpha = 0.25f + hot * 0.45f), Color.Transparent),
                Offset(w * 0.5f, h * 0.52f), w * 0.6f,
            ),
            w * 0.6f, Offset(w * 0.5f, h * 0.52f),
        )
        // anvil
        val iron = Color(0xFF3A3A3E)
        val top = h * 0.62f
        val anvil = Path().apply {
            moveTo(w * 0.12f, top)
            lineTo(w * 0.82f, top)
            lineTo(w * 0.95f, top + h * 0.05f)
            lineTo(w * 0.8f, top + h * 0.1f)
            lineTo(w * 0.62f, top + h * 0.12f)
            lineTo(w * 0.58f, top + h * 0.24f)
            lineTo(w * 0.72f, top + h * 0.34f)
            lineTo(w * 0.28f, top + h * 0.34f)
            lineTo(w * 0.42f, top + h * 0.24f)
            lineTo(w * 0.38f, top + h * 0.12f)
            lineTo(w * 0.12f, top + h * 0.1f)
            close()
        }
        drawPath(anvil, iron)
        drawLine(Color(0xFF6A6A70), Offset(w * 0.12f, top), Offset(w * 0.82f, top), 2.dp.toPx())

        // blade, scaled so the longest design still fits
        val scale = w * 0.74f / 125f
        val len = g.length * scale
        val formed = 0.35f + 0.65f * (if (quenched) 1f else progress)
        val halfW = g.width * scale * 0.5f * 2.2f * (1.5f - 0.5f * formed)
        val x0 = w * 0.5f - (len + g.handle * scale * 0.5f) / 2f + g.handle * scale * 0.5f
        val y = top - halfW - 3.dp.toPx() - recoil * 5.dp.toPx()
        val sag = g.curvature / 25f * len * 0.14f
        val tipLen = len * (0.08f + 0.2f * g.taper) * formed
        fun yc(t: Float) = y - sin(t * 3.1416f * 0.5f) * sag * formed
        val path = Path().apply {
            moveTo(x0, y - halfW)
            val steps = 14
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val x = x0 + (len - tipLen) * t
                lineTo(x, yc(t) - halfW * (1f - 0.25f * g.taper * t))
            }
            lineTo(x0 + len, yc(1f))
            for (i in steps downTo 0) {
                val t = i / steps.toFloat()
                val x = x0 + (len - tipLen) * t
                lineTo(x, yc(t) + halfW * (1f - 0.25f * g.taper * t))
            }
            close()
        }
        val color = heatColor(temperature, base)
        if (hot > 0.05f) {
            drawPath(path, color.copy(alpha = 0.35f * hot), style = Stroke(width = 14.dp.toPx() * hot))
        }
        drawPath(path, Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.28f)), y - halfW, y + halfW))
        if (g.fuller && formed > 0.6f) {
            drawLine(lerp(color, Color.Black, 0.35f), Offset(x0 + len * 0.04f, y), Offset(x0 + len * 0.62f, yc(0.62f)), halfW * 0.35f)
        }
        // tang, then guard and grip once the blade is hardened
        val grip = g.handle * scale * 0.5f
        if (quenched) {
            drawRoundRect(Color(0xFF5A3A22), Offset(x0 - grip, y - halfW * 0.45f), Size(grip, halfW * 0.9f), CornerRadius(4f))
            drawRoundRect(Color(0xFF9A8250), Offset(x0 - 3.dp.toPx(), y - halfW * 1.9f), Size(6.dp.toPx(), halfW * 3.8f), CornerRadius(4f))
            drawCircle(Color(0xFF9A8250), halfW * 0.6f + g.pommel / 450f * 4.dp.toPx(), Offset(x0 - grip, y))
        } else {
            drawRect(lerp(color, Color.Black, 0.2f), Offset(x0 - grip * 0.8f, y - halfW * 0.3f), Size(grip * 0.8f, halfW * 0.6f))
        }
        // sparks on a fresh blow
        if (recoil > 0.2f) {
            for (i in 0 until 9) {
                val ang = -0.3f - i * 0.3f
                val d = (1f - recoil) * 60.dp.toPx() + 8.dp.toPx()
                val cx = x0 + len * 0.55f + kotlin.math.cos(ang) * d
                val cy = y + sin(ang) * d
                drawCircle(Color(0xFFFFD257).copy(alpha = recoil), 2.dp.toPx(), Offset(cx, cy))
            }
        }
    }
}
