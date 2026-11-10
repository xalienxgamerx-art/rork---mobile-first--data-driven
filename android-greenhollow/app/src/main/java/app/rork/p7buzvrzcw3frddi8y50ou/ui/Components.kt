package app.rork.p7buzvrzcw3frddi8y50ou.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KebabDining
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rork.p7buzvrzcw3frddi8y50ou.data.parseHex
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink
import kotlin.math.roundToInt

/** Aged paper: a warm wash, darker toward the edges, with a few faint stains. */
fun Modifier.parchment(): Modifier = this
    .background(Ink.Parchment)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x22FFF6DF), Color.Transparent, Color(0x268B5A2B)),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.maxDimension * 0.75f,
            ),
        )
        val stains = floatArrayOf(0.14f, 0.2f, 0.3f, 0.82f, 0.62f, 0.22f, 0.3f, 0.86f, 0.36f, 0.6f, 0.48f, 0.18f)
        for (i in stains.indices step 3) {
            val c = Offset(size.width * stains[i], size.height * stains[i + 1])
            val r = size.minDimension * stains[i + 2]
            drawCircle(Brush.radialGradient(listOf(Color(0x148B5A2B), Color.Transparent), c, r), r, c)
        }
    }

@Composable
fun ParchmentTopBar(
    title: String,
    icon: ImageVector = Icons.AutoMirrored.Filled.MenuBook,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    onIcon: (() -> Unit)? = null,
) {
    Column(modifier.background(background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onIcon != null) {
                IconButton(onClick = onIcon) {
                    Icon(icon, contentDescription = "Open journal", tint = Ink.LeatherDark, modifier = Modifier.size(30.dp))
                }
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Ink.LeatherDark, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Ink.LeatherDark, modifier = Modifier.size(28.dp))
            }
        }
        HorizontalDivider(thickness = 1.5.dp, color = Ink.Leather.copy(alpha = 0.7f))
    }
}

@Composable
fun ParchmentCard(
    modifier: Modifier = Modifier,
    color: Color = Ink.ParchmentLight.copy(alpha = 0.7f),
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .border(BorderStroke(1.2.dp, Ink.Leather.copy(alpha = 0.55f)), RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Ink.Leather.copy(alpha = 0.6f))
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f), color = Ink.Leather.copy(alpha = 0.6f))
    }
}

@Composable
fun StatBar(
    icon: ImageVector,
    label: String,
    value: Float,
    max: Float,
    color: Color,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 70.dp,
    trailing: String = "${value.roundToInt()} / ${max.roundToInt()}",
) {
    val fraction by animateFloatAsState((value / max).coerceIn(0f, 1f), label = "bar")
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label $trailing" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Ink.LeatherDark, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(labelWidth), maxLines = 1)
        Box(
            Modifier.weight(1f).height(9.dp).clip(CircleShape).background(Ink.Track),
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(9.dp).clip(CircleShape).background(color))
        }
        Text(
            trailing,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(74.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
fun InkChip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Ink.Text,
            iconColor = Ink.LeatherDark,
            selectedContainerColor = Ink.Leather,
            selectedLabelColor = Ink.ParchmentLight,
            selectedLeadingIconColor = Ink.ParchmentLight,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Ink.Leather,
            selectedBorderColor = Ink.Leather,
            borderWidth = 1.2.dp,
        ),
    )
}

/** A small ink-and-wash bust drawn from the character's appearance data. */
@Composable
fun Portrait(
    skin: String,
    hair: String,
    cloth: String,
    hat: Boolean,
    beard: Boolean,
    longHair: Boolean,
    modifier: Modifier = Modifier,
    bald: Boolean = false,
) {
    val skinC = Color(parseHex(skin, 0xFFD9A77F.toInt()))
    val hairC = Color(parseHex(hair, 0xFF4A3222.toInt()))
    val clothC = Color(parseHex(cloth, 0xFF7A6548.toInt()))
    Canvas(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFDCC8A0), Color(0xFFC7AE82)))),
    ) {
        val w = size.width
        val h = size.height
        val ink = Color(0xFF2B1D12)
        val line = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
        // long hair behind the head
        if (longHair && !bald) {
            drawRoundRect(hairC, Offset(w * 0.25f, h * 0.2f), Size(w * 0.5f, h * 0.52f), CornerRadius(w * 0.2f))
        }
        // shoulders
        val body = Path().apply {
            moveTo(w * 0.06f, h * 1.02f)
            cubicTo(w * 0.08f, h * 0.72f, w * 0.3f, h * 0.68f, w * 0.5f, h * 0.68f)
            cubicTo(w * 0.7f, h * 0.68f, w * 0.92f, h * 0.72f, w * 0.94f, h * 1.02f)
            close()
        }
        drawPath(body, clothC)
        drawPath(body, ink.copy(alpha = 0.55f), style = line)
        // collar
        val collar = Path().apply {
            moveTo(w * 0.38f, h * 0.69f)
            lineTo(w * 0.5f, h * 0.84f)
            lineTo(w * 0.62f, h * 0.69f)
            close()
        }
        drawPath(collar, Color(0xFFE9DCC0))
        // neck and head
        drawRect(skinC.copy(alpha = 0.92f), Offset(w * 0.43f, h * 0.56f), Size(w * 0.14f, h * 0.16f))
        drawOval(skinC, Offset(w * 0.31f, h * 0.2f), Size(w * 0.38f, h * 0.44f))
        drawOval(ink.copy(alpha = 0.5f), Offset(w * 0.31f, h * 0.2f), Size(w * 0.38f, h * 0.44f), style = line)
        // hair cap
        val cap = Path().apply {
            moveTo(w * 0.3f, h * 0.4f)
            cubicTo(w * 0.27f, h * 0.12f, w * 0.73f, h * 0.12f, w * 0.7f, h * 0.4f)
            cubicTo(w * 0.66f, h * 0.27f, w * 0.52f, h * 0.25f, w * 0.44f, h * 0.29f)
            cubicTo(w * 0.36f, h * 0.3f, w * 0.33f, h * 0.34f, w * 0.3f, h * 0.4f)
            close()
        }
        if (!bald) drawPath(cap, hairC)
        // face
        drawCircle(ink, w * 0.018f, Offset(w * 0.43f, h * 0.41f))
        drawCircle(ink, w * 0.018f, Offset(w * 0.57f, h * 0.41f))
        drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.5f, h * 0.43f), Offset(w * 0.49f, h * 0.49f), w * 0.016f, StrokeCap.Round)
        if (beard) {
            val b = Path().apply {
                moveTo(w * 0.33f, h * 0.47f)
                cubicTo(w * 0.34f, h * 0.7f, w * 0.66f, h * 0.7f, w * 0.67f, h * 0.47f)
                cubicTo(w * 0.6f, h * 0.53f, w * 0.4f, h * 0.53f, w * 0.33f, h * 0.47f)
                close()
            }
            drawPath(b, hairC)
        } else {
            drawArc(
                ink.copy(alpha = 0.7f), 20f, 140f, false,
                Offset(w * 0.44f, h * 0.49f), Size(w * 0.12f, h * 0.06f), style = Stroke(w * 0.016f, cap = StrokeCap.Round),
            )
        }
        if (hat) {
            val hatC = Color(
                red = clothC.red * 0.6f, green = clothC.green * 0.6f, blue = clothC.blue * 0.6f, alpha = 1f,
            )
            drawOval(hatC, Offset(w * 0.14f, h * 0.2f), Size(w * 0.72f, h * 0.12f))
            drawRoundRect(hatC, Offset(w * 0.32f, h * 0.07f), Size(w * 0.36f, h * 0.2f), CornerRadius(w * 0.08f))
            drawOval(ink.copy(alpha = 0.45f), Offset(w * 0.14f, h * 0.2f), Size(w * 0.72f, h * 0.12f), style = line)
        }
    }
}

private fun DrawScope.sword(color: Color, angle: Float) {
    val s = size.minDimension
    rotate(angle) {
        val cx = size.width / 2f
        drawLine(color, Offset(cx, s * 0.08f), Offset(cx, s * 0.7f), s * 0.085f, StrokeCap.Round)
        drawLine(color, Offset(cx - s * 0.16f, s * 0.7f), Offset(cx + s * 0.16f, s * 0.7f), s * 0.085f, StrokeCap.Round)
        drawLine(color, Offset(cx, s * 0.72f), Offset(cx, s * 0.9f), s * 0.075f, StrokeCap.Round)
    }
}

@Composable
fun SwordGlyph(tint: Color, modifier: Modifier = Modifier, crossed: Boolean = false) {
    Canvas(modifier) {
        if (crossed) {
            sword(tint, 40f)
            sword(tint, -40f)
        } else {
            sword(tint, 40f)
        }
    }
}

/** Icon for an item definition's `icon` key. Unknown keys from mods fall back to a neutral glyph. */
@Composable
fun ItemGlyph(icon: String, modifier: Modifier = Modifier, tint: Color? = null) {
    if (icon == "sword") {
        SwordGlyph(tint ?: Color(0xFF6F6A66), modifier)
        return
    }
    val (vector, natural) = when (icon) {
        "ingot" -> Icons.Filled.Inventory2 to Color(0xFF77736E)
        "potion" -> Icons.Filled.Science to Color(0xFF9B3B2E)
        "bedroll" -> Icons.Filled.Bed to Color(0xFF7A6A45)
        "pouch" -> Icons.Filled.ShoppingBag to Color(0xFF8B5A2B)
        "herb" -> Icons.Filled.Spa to Color(0xFF5B6B3A)
        "gem" -> Icons.Filled.Diamond to Color(0xFF7B5EA7)
        "book" -> Icons.AutoMirrored.Filled.MenuBook to Color(0xFF7A3F23)
        "rope" -> Icons.Filled.Cable to Color(0xFF9A7A45)
        "food" -> Icons.Filled.BakeryDining to Color(0xFFB07A32)
        "meat" -> Icons.Filled.KebabDining to Color(0xFF9B4A36)
        "pelt" -> Icons.Filled.Pets to Color(0xFF6B5540)
        "helmet" -> Icons.Filled.SportsMotorsports to Color(0xFF6F6A66)
        "armor" -> Icons.Filled.Checkroom to Color(0xFF6E4520)
        "shield" -> Icons.Filled.Shield to Color(0xFF5B6B3A)
        "ore" -> Icons.Filled.Landscape to Color(0xFF6F6A66)
        "fish" -> Icons.Filled.SetMeal to Color(0xFF4F7480)
        else -> Icons.Filled.Category to Color(0xFF6B5540)
    }
    Icon(vector, contentDescription = null, tint = tint ?: natural, modifier = modifier)
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
