package com.vintagevinyl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** The palette: deep walnut, warm cream, antique gold, charcoal black and muted copper. */
object VV {
    val WalnutDeep = Color(0xFF1E120C)
    val Walnut = Color(0xFF2B1A12)
    val WalnutMid = Color(0xFF3B2416)
    val WalnutLight = Color(0xFF55341F)

    val Cream = Color(0xFFF1E5CC)
    val CreamDim = Color(0xFFC4B799)

    val Gold = Color(0xFFC9A24B)
    val GoldDim = Color(0xFF8E7433)

    val Charcoal = Color(0xFF1B1918)
    val CharcoalLight = Color(0xFF2B2826)

    val Copper = Color(0xFFB06A45)
    val CopperDim = Color(0xFF7C4A30)

    /** The amber pilot lamp. */
    val Lamp = Color(0xFFFFB347)
}

private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif

/** Serif for names and titles, spaced small caps for the engraved control labels. */
val VintageTypography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Serif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Serif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.4.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.6.sp),
)

private val VintageColors = darkColorScheme(
    primary = VV.Gold,
    onPrimary = VV.Charcoal,
    primaryContainer = VV.WalnutLight,
    onPrimaryContainer = VV.Cream,
    secondary = VV.Copper,
    onSecondary = VV.Cream,
    background = VV.WalnutDeep,
    onBackground = VV.Cream,
    surface = VV.Walnut,
    onSurface = VV.Cream,
    surfaceVariant = VV.WalnutMid,
    onSurfaceVariant = VV.CreamDim,
    outline = VV.GoldDim,
    error = Color(0xFFD9826B),
)

/** The app is always "lights down": a vintage hi-fi has one look, so it ignores the system theme. */
@Composable
fun VintageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VintageColors, typography = VintageTypography, content = content)
}

/** Dark walnut with faint vertical grain; drawn once per size and cached. */
fun Modifier.woodGrain(base: Color = VV.WalnutDeep, seed: Int = 7): Modifier = drawWithCache {
    val rnd = Random(seed)
    val w = size.width
    val h = size.height
    val grains = List(90) { Triple(rnd.nextFloat() * w, rnd.nextFloat(), rnd.nextFloat()) }
    onDrawBehind {
        drawRect(base)
        for ((x, thickness, shade) in grains) {
            val sway = (shade - 0.5f) * 60f
            drawLine(
                color = Color.Black.copy(alpha = 0.04f + 0.08f * shade),
                start = Offset(x, 0f),
                end = Offset(x + sway, h),
                strokeWidth = 1f + thickness * 3f,
            )
        }
        for (i in 0 until 14) {
            val x = grains[i].first
            drawLine(
                color = VV.WalnutLight.copy(alpha = 0.07f),
                start = Offset(x + 4f, 0f),
                end = Offset(x + 4f + (grains[i].third - 0.5f) * 60f, h),
                strokeWidth = 1.5f,
            )
        }
    }
}
