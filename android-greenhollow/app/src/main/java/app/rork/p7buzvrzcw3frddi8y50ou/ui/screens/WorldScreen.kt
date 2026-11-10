package app.rork.p7buzvrzcw3frddi8y50ou.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rork.p7buzvrzcw3frddi8y50ou.render.GameSurfaceView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Hud
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Settings
import app.rork.p7buzvrzcw3frddi8y50ou.ui.ParchmentTopBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.StatBar
import app.rork.p7buzvrzcw3frddi8y50ou.ui.SwordGlyph
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Crimson
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink
import kotlin.math.sqrt

private val HudPaper = Color(0xE6EADCC0)
private val HudLeather = Color(0xD95A3A1E)

@Composable
fun WorldScreen(
    engine: GameEngine,
    settings: Settings,
    hurtPulse: Int,
    onJournal: () -> Unit,
    onSettings: () -> Unit,
) {
    val hud by engine.hud.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surface by remember { mutableStateOf<GameSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, surface) {
        val view = surface
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(engine) {
        onDispose { engine.input.reset() }
    }

    val flash = remember { Animatable(0f) }
    LaunchedEffect(hurtPulse) {
        if (hurtPulse > 0) {
            flash.snapTo(0.5f)
            flash.animateTo(0f, tween(500))
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF1B1611))) {
        AndroidView(
            factory = { ctx -> GameSurfaceView(ctx, engine, settings.graphics, settings.adaptive).also { surface = it } },
            update = { it.configure(engine, settings.graphics, settings.adaptive) },
            modifier = Modifier.fillMaxSize(),
        )
        // Drag anywhere on the view to look around.
        Box(
            Modifier.fillMaxSize().pointerInput(engine) {
                detectDragGestures { change, drag ->
                    change.consume()
                    engine.input.look(drag.x, drag.y)
                }
            },
        )
        if (flash.value > 0.01f) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color.Transparent, Color(0xFF9B2A1E).copy(alpha = flash.value))),
                ),
            )
        }

        Column(Modifier.fillMaxSize()) {
            ParchmentTopBar(
                title = hud.location,
                onSettings = onSettings,
                background = HudPaper,
                onIcon = onJournal,
            )
            CompassCard(engine, hud, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    AnimatedVisibility(hud.targetName != null, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(HudPaper).padding(horizontal = 12.dp, vertical = 6.dp).width(170.dp),
                        ) {
                            Text(hud.targetName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Ink.Track)) {
                                Box(Modifier.fillMaxWidth(hud.targetHealth).height(7.dp).background(Ink.Blood))
                            }
                        }
                    }
                    if (settings.showFps) {
                        Text(
                            "${hud.fps} fps",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x80000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallRound(Icons.Filled.Cameraswitch, if (hud.thirdPerson) "Switch to first person" else "Switch to third person") {
                        engine.submit(Command.ToggleCamera)
                    }
                    SmallRound(Icons.Filled.AutoAwesome, "Cast mending spell") {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        engine.submit(Command.CastHeal)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val stick: @Composable () -> Unit = { Joystick(engine) }
                val actions: @Composable () -> Unit = {
                    ActionCluster(engine, hud, alignEnd = !settings.leftHanded)
                }
                if (settings.leftHanded) actions() else stick()
                Spacer(Modifier.weight(1f))
                if (settings.leftHanded) stick() else actions()
            }
            VitalsCard(hud, Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun SmallRound(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(HudLeather)
            .border(BorderStroke(1.dp, Ink.Surface.copy(alpha = 0.8f)), CircleShape)
            .tapButton(label, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Ink.ParchmentLight, modifier = Modifier.size(22.dp))
    }
}

/** Fires on touch-down so combat feels immediate, while still exposing a click to accessibility. */
private fun Modifier.tapButton(label: String, action: () -> Unit): Modifier = this
    .semantics {
        contentDescription = label
        role = Role.Button
        onClick(label = label) { action(); true }
    }
    .pointerInput(action) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            action()
            do {
                val e = awaitPointerEvent()
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }

@Composable
private fun CompassCard(engine: GameEngine, hud: Hud, modifier: Modifier = Modifier) {
    val heading by engine.heading.collectAsStateWithLifecycle()
    val measurer = rememberTextMeasurer()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HudPaper)
            .border(BorderStroke(1.2.dp, Ink.Leather.copy(alpha = 0.7f)), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WbTwilight, contentDescription = null, tint = Ink.LeatherDark, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                hud.timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(22.dp).background(Ink.Leather.copy(alpha = 0.6f)))
            Text(hud.clock, style = MaterialTheme.typography.titleMedium)
        }
        val style = TextStyle(fontFamily = Crimson, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink.Text)
        val nameStyle = TextStyle(fontFamily = Crimson, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Ink.LeatherDark)
        Canvas(Modifier.fillMaxWidth().height(34.dp).padding(top = 2.dp)) {
            val cx = size.width / 2f
            val span = 75f
            val baseY = size.height - 4.dp.toPx()
            drawLine(Ink.Leather.copy(alpha = 0.6f), Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
            var a = 0
            while (a < 360) {
                var off = a - heading
                while (off > 180f) off -= 360f
                while (off < -180f) off += 360f
                if (off > -span && off < span) {
                    val x = cx + off / span * cx
                    val major = a % 90 == 0
                    val tick = if (major) 7.dp.toPx() else if (a % 45 == 0) 5.dp.toPx() else 3.dp.toPx()
                    drawLine(Ink.Text.copy(alpha = if (major) 0.9f else 0.5f), Offset(x, baseY), Offset(x, baseY - tick), 1.2.dp.toPx())
                    if (major) {
                        val letter = when (a) { 0 -> "N"; 90 -> "E"; 180 -> "S"; else -> "W" }
                        val layout = measurer.measure(letter, style)
                        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, baseY - tick - layout.size.height + 2.dp.toPx()))
                    }
                }
                a += 15
            }
            // Places: a filled diamond once found, a hollow one until then. Only found places are named.
            var facing: String? = null
            var facingOff = 9f
            var facingX = 0f
            val d = 4.dp.toPx()
            hud.markers.forEach { m ->
                var off = m.bearing - heading
                while (off > 180f) off -= 360f
                while (off < -180f) off += 360f
                if (off > -span && off < span) {
                    val x = cx + off / span * cx
                    val y = baseY - 11.dp.toPx()
                    val diamond = Path().apply {
                        moveTo(x, y - d)
                        lineTo(x + d, y)
                        lineTo(x, y + d)
                        lineTo(x - d, y)
                        close()
                    }
                    if (m.name != null) {
                        drawPath(diamond, Ink.Ember)
                        drawPath(diamond, Ink.Text.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()))
                        if (kotlin.math.abs(off) < facingOff) {
                            facingOff = kotlin.math.abs(off)
                            facing = m.name
                            facingX = x
                        }
                    } else {
                        drawPath(diamond, Ink.Muted, style = Stroke(1.2.dp.toPx()))
                    }
                }
            }
            facing?.let { name ->
                var cardinal = heading % 90f
                if (cardinal < 0f) cardinal += 90f
                // Skip the name when a compass letter sits in the same spot.
                if (cardinal > 16f && cardinal < 74f) {
                    val layout = measurer.measure(name, nameStyle, maxLines = 1)
                    val left = (facingX - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                    drawText(layout, topLeft = Offset(left, 0f))
                }
            }
            val p = Path().apply {
                moveTo(cx, baseY - 6.dp.toPx())
                lineTo(cx - 5.dp.toPx(), baseY + 3.dp.toPx())
                lineTo(cx + 5.dp.toPx(), baseY + 3.dp.toPx())
                close()
            }
            drawPath(p, Ink.LeatherDark)
        }
    }
}

@Composable
private fun VitalsCard(hud: Hud, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HudPaper)
            .border(BorderStroke(1.2.dp, Ink.Leather.copy(alpha = 0.7f)), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StatBar(Icons.Filled.Favorite, "Health", hud.health, hud.maxHealth, Ink.Moss)
        StatBar(Icons.Filled.Bolt, "Stamina", hud.stamina, hud.maxStamina, Ink.Ember)
        StatBar(Icons.Filled.LocalFireDepartment, "Magic", hud.magic, hud.maxMagic, Ink.Magic)
    }
}

@Composable
private fun Joystick(engine: GameEngine) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .size(140.dp)
            .semantics { contentDescription = "Movement stick" }
            .pointerInput(engine) {
                val radius = size.width / 2f
                val reach = radius * 0.62f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val id = down.id
                    fun apply(pos: Offset) {
                        var dx = pos.x - radius
                        var dy = pos.y - radius
                        val len = sqrt(dx * dx + dy * dy)
                        if (len > reach) {
                            dx = dx / len * reach
                            dy = dy / len * reach
                        }
                        knob = Offset(dx, dy)
                        engine.input.moveX = dx / reach
                        engine.input.moveY = -dy / reach
                    }
                    apply(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        apply(change.position)
                    }
                    knob = Offset.Zero
                    engine.input.moveX = 0f
                    engine.input.moveY = 0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(HudLeather, r)
            drawCircle(Ink.Surface.copy(alpha = 0.8f), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            val chevron = 6.dp.toPx()
            val inset = 14.dp.toPx()
            val ink = Ink.Surface.copy(alpha = 0.9f)
            val w = 2.dp.toPx()
            val c = center
            // up, down, left, right chevrons
            drawLine(ink, Offset(c.x - chevron, c.y - r + inset + chevron), Offset(c.x, c.y - r + inset), w)
            drawLine(ink, Offset(c.x + chevron, c.y - r + inset + chevron), Offset(c.x, c.y - r + inset), w)
            drawLine(ink, Offset(c.x - chevron, c.y + r - inset - chevron), Offset(c.x, c.y + r - inset), w)
            drawLine(ink, Offset(c.x + chevron, c.y + r - inset - chevron), Offset(c.x, c.y + r - inset), w)
            drawLine(ink, Offset(c.x - r + inset + chevron, c.y - chevron), Offset(c.x - r + inset, c.y), w)
            drawLine(ink, Offset(c.x - r + inset + chevron, c.y + chevron), Offset(c.x - r + inset, c.y), w)
            drawLine(ink, Offset(c.x + r - inset - chevron, c.y - chevron), Offset(c.x + r - inset, c.y), w)
            drawLine(ink, Offset(c.x + r - inset - chevron, c.y + chevron), Offset(c.x + r - inset, c.y), w)
            val k = Offset(c.x + knob.x, c.y + knob.y)
            drawCircle(Color(0x55000000), r * 0.36f, Offset(k.x, k.y + 2.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF1E6CE), Color(0xFFD0B98C)), k, r * 0.36f), r * 0.34f, k)
        }
    }
}

@Composable
private fun ActionCluster(engine: GameEngine, hud: Hud, alignEnd: Boolean) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(hud.prompt != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            var label by remember { mutableStateOf("") }
            hud.prompt?.let { label = it }
            Row(
                Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(HudLeather)
                    .border(BorderStroke(1.2.dp, Ink.Surface.copy(alpha = 0.85f)), CircleShape)
                    .tapButton(label) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        engine.submit(Command.Interact)
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (hud.promptKind == "npc") Icons.AutoMirrored.Filled.Chat else Icons.Filled.PanTool,
                    contentDescription = null, tint = Ink.ParchmentLight, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = Ink.ParchmentLight, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundAction("Attack", onDown = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                engine.submit(Command.Attack)
            }) { SwordGlyph(Ink.ParchmentLight, Modifier.size(26.dp), crossed = true) }
            RoundAction("Block", onDown = { engine.input.block = true }, onUp = { engine.input.block = false }) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Ink.ParchmentLight, modifier = Modifier.size(24.dp))
            }
        }
        RoundAction("Jump", showLabel = false, onDown = { engine.submit(Command.Jump) }) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Ink.ParchmentLight, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun RoundAction(
    label: String,
    showLabel: Boolean = true,
    onDown: () -> Unit,
    onUp: () -> Unit = {},
    icon: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .size(if (showLabel) 74.dp else 66.dp)
            .clip(CircleShape)
            .background(if (pressed) Color(0xF07A4E26) else HudLeather)
            .border(BorderStroke(1.5.dp, Ink.Surface.copy(alpha = 0.85f)), CircleShape)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) { onDown(); onUp(); true }
            }
            .pointerInput(onDown, onUp) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    onDown()
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    pressed = false
                    onUp()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, color = Ink.ParchmentLight)
    }
}
