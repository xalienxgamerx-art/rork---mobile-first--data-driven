package app.rork.p7buzvrzcw3frddi8y50ou.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rork.p7buzvrzcw3frddi8y50ou.R

object Ink {
    val Parchment = Color(0xFFEADCC0)
    val ParchmentLight = Color(0xFFF1E6CE)
    val Surface = Color(0xFFD8C39A)
    val Text = Color(0xFF2B1D12)
    val Muted = Color(0xFF6B5540)
    val Leather = Color(0xFF8B5A2B)
    val LeatherDark = Color(0xFF6E4520)
    val Ember = Color(0xFFD9772B)
    val Moss = Color(0xFF5B6B3A)
    val Magic = Color(0xFF7B5EA7)
    val Blood = Color(0xFF9B3B2E)
    val Track = Color(0xFFCDB68C)
    val Line = Color(0xFFB8996A)
}

val Crimson = FontFamily(
    Font(R.font.crimson_regular, FontWeight.Normal),
    Font(R.font.crimson_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.crimson_semibold, FontWeight.SemiBold),
    Font(R.font.crimson_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.25f).toInt()) =
    TextStyle(fontFamily = Crimson, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, color = Ink.Text)

private val AppTypography = Typography(
    displaySmall = style(34, FontWeight.Bold),
    headlineLarge = style(30, FontWeight.Bold),
    headlineMedium = style(26, FontWeight.Bold),
    headlineSmall = style(23, FontWeight.SemiBold),
    titleLarge = style(22, FontWeight.SemiBold),
    titleMedium = style(19, FontWeight.SemiBold),
    titleSmall = style(17, FontWeight.SemiBold),
    bodyLarge = style(18),
    bodyMedium = style(16),
    bodySmall = style(14),
    labelLarge = style(17, FontWeight.SemiBold),
    labelMedium = style(15, FontWeight.SemiBold),
    labelSmall = style(13, FontWeight.SemiBold),
)

private val Scheme = lightColorScheme(
    primary = Ink.Leather,
    onPrimary = Ink.ParchmentLight,
    primaryContainer = Ink.Surface,
    onPrimaryContainer = Ink.Text,
    secondary = Ink.Moss,
    onSecondary = Ink.ParchmentLight,
    secondaryContainer = Ink.Leather,
    onSecondaryContainer = Ink.ParchmentLight,
    tertiary = Ink.Ember,
    onTertiary = Ink.Text,
    background = Ink.Parchment,
    onBackground = Ink.Text,
    surface = Ink.Parchment,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Surface,
    onSurfaceVariant = Ink.Muted,
    surfaceContainer = Ink.ParchmentLight,
    surfaceContainerLow = Ink.ParchmentLight,
    surfaceContainerHigh = Ink.Surface,
    surfaceContainerHighest = Ink.Surface,
    outline = Ink.Leather,
    outlineVariant = Ink.Line,
    error = Ink.Blood,
    onError = Ink.ParchmentLight,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
