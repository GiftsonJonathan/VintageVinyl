package com.vintagevinyl.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vintagevinyl.ui.theme.VV
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val EaseInQuad = Easing { it * it }
private val EaseOutQuad = Easing { 1f - (1f - it) * (1f - it) }

private const val RECORD_RADIUS = 0.43f // of the deck width
private const val ARM_REST_DEG = 0f
private const val ARM_START_DEG = 20f   // outer groove
private const val ARM_END_DEG = 40f     // inner groove
private const val ARM_LENGTH = 0.78f

/**
 * The turntable: plinth, platter, spinning record with a printed (or artwork) label, and a
 * tonearm that swings onto the record while playing and lifts back to its rest when paused.
 *
 *  * The rotation is an [Animatable] read inside `graphicsLayer`, so spinning only re-composites
 *    the GPU layer. It costs no recomposition and nothing on the audio thread.
 *  * The record accelerates over 0.9 s when playback starts and coasts to a stop over 1.4 s
 *    when it stops; both ramps are matched to the steady speed so the motion is continuous.
 *  * The arm creeps from the outer to the inner groove as the track progresses.
 *
 * @param progressPercent playback progress as a whole percent (changes at most 100 times per
 *        track, which keeps the arm animation cheap)
 * @param rpm the turntable speed chosen in the sound settings (33 / 45 / 78)
 */
@Composable
fun TurntableDeck(
    isPlaying: Boolean,
    hasTrack: Boolean,
    progressPercent: Int,
    rpm: Double,
    speedLabel: String,
    artwork: ImageBitmap?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val angle = remember { Animatable(0f) }
    var hasSpun by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, rpm) {
        val revMs = (60_000.0 / rpm).toFloat()
        if (isPlaying) {
            hasSpun = true
            val upMs = 900
            angle.animateTo(angle.value + 180f * upMs / revMs, tween(upMs, easing = EaseInQuad))
            while (true) {
                angle.snapTo(angle.value % 360f)
                angle.animateTo(angle.value + 360f, tween(revMs.roundToInt(), easing = LinearEasing))
            }
        } else if (hasSpun) {
            val downMs = 1400
            angle.animateTo(angle.value + 180f * downMs / revMs, tween(downMs, easing = EaseOutQuad))
            hasSpun = false
        }
    }

    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying && hasTrack) ARM_START_DEG + (ARM_END_DEG - ARM_START_DEG) * progressPercent / 100f else ARM_REST_DEG,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 45f),
        label = "tonearm",
    )
    val armLifted = !(isPlaying && hasTrack)
    val measurer = rememberTextMeasurer()

    val click = if (onClick != null) {
        Modifier
            .semantics { contentDescription = "Open the now playing screen"; role = Role.Button }
            .clickable(onClick = onClick)
    } else Modifier

    Box(modifier.aspectRatio(1f).then(click)) {
        Canvas(Modifier.fillMaxSize()) { drawPlinth(isPlaying) }

        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle.value }) {
            drawRecord(artwork, measurer, speedLabel)
        }

        // Light reflections stay put while the record turns beneath them.
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * RECORD_RADIUS
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.05f to Color.White.copy(alpha = 0.10f), 0.11f to Color.Transparent,
                    0.5f to Color.Transparent, 0.55f to Color.White.copy(alpha = 0.10f), 0.61f to Color.Transparent,
                    1f to Color.Transparent, center = center,
                ),
                r, center,
            )
        }

        Canvas(Modifier.fillMaxSize()) { drawTonearm(armAngle, armLifted) }
    }
}

private fun DrawScope.drawPlinth(playing: Boolean) {
    val w = size.width
    val corner = CornerRadius(w * 0.045f)
    drawRoundRect(VV.WalnutMid, Offset.Zero, size, corner)
    drawRoundRect(VV.WalnutLight.copy(alpha = 0.5f), Offset(w * 0.012f, w * 0.012f), Size(w * 0.976f, w * 0.976f), corner, style = Stroke(w * 0.004f))
    drawRoundRect(VV.Gold.copy(alpha = 0.55f), Offset(w * 0.028f, w * 0.028f), Size(w * 0.944f, w * 0.944f), CornerRadius(w * 0.035f), style = Stroke(w * 0.003f))

    // Platter: brushed ring a little larger than the record.
    val c = center
    val platter = w * (RECORD_RADIUS + 0.018f)
    drawCircle(Color.Black.copy(alpha = 0.4f), platter, c + Offset(0f, w * 0.008f))
    drawCircle(VV.CharcoalLight, platter, c)
    drawCircle(VV.GoldDim.copy(alpha = 0.6f), platter, c, style = Stroke(w * 0.003f))

    // Power lamp.
    val lamp = Offset(w * 0.075f, w * 0.075f)
    if (playing) drawCircle(VV.Lamp.copy(alpha = 0.25f), w * 0.03f, lamp)
    drawCircle(if (playing) VV.Lamp else VV.CopperDim, w * 0.012f, lamp)

    // Arm rest cradle at the bottom right.
    val rest = Offset(w * 0.9f, w * (0.12f + ARM_LENGTH))
    drawCircle(VV.Charcoal, w * 0.022f, rest)
    drawCircle(VV.GoldDim, w * 0.022f, rest, style = Stroke(w * 0.003f))
}

private fun DrawScope.drawRecord(art: ImageBitmap?, measurer: TextMeasurer, speedLabel: String) {
    val c = center
    val radius = size.minDimension * RECORD_RADIUS
    val hairline = size.minDimension * 0.0015f

    drawCircle(VV.Charcoal, radius, c)

    // Grooves: dozens of fine concentric rings, every few brighter, plus lighter "track gap" bands.
    var r = radius * 0.38f
    var i = 0
    while (r < radius * 0.985f) {
        drawCircle(Color.White.copy(alpha = if (i % 7 == 0) 0.085f else 0.03f), r, c, style = Stroke(hairline))
        r += radius * 0.0125f
        i++
    }
    for (k in 1..4) {
        drawCircle(Color.White.copy(alpha = 0.09f), radius * (0.47f + 0.11f * k), c, style = Stroke(radius * 0.011f))
    }
    drawCircle(Color.White.copy(alpha = 0.14f), radius * 0.985f, c, style = Stroke(hairline * 2f))

    // Label.
    val labelR = radius * 0.36f
    drawCircle(VV.Copper, labelR, c)
    val inner = labelR * 0.93f
    if (art != null) {
        val side = minOf(art.width, art.height)
        val dst = (inner * 2f).roundToInt()
        clipPath(Path().apply { addOval(Rect(c, inner)) }) {
            drawImage(
                art,
                srcOffset = IntOffset((art.width - side) / 2, (art.height - side) / 2),
                srcSize = IntSize(side, side),
                dstOffset = IntOffset((c.x - dst / 2f).roundToInt(), (c.y - dst / 2f).roundToInt()),
                dstSize = IntSize(dst, dst),
            )
        }
        drawCircle(VV.Cream.copy(alpha = 0.85f), inner, c, style = Stroke(radius * 0.012f))
    } else {
        drawCircle(VV.Cream, inner, c)
        drawCircle(VV.Copper, inner * 0.97f, c, style = Stroke(radius * 0.008f))
        drawCircle(VV.Copper.copy(alpha = 0.6f), inner * 0.62f, c, style = Stroke(radius * 0.006f))

        fun label(text: String, sizeFactor: Float, color: Color, weight: FontWeight, yOffset: Float) {
            val layout = measurer.measure(
                text,
                TextStyle(
                    color = color,
                    fontFamily = FontFamily.Serif,
                    fontWeight = weight,
                    fontSize = (inner * sizeFactor).toSp(),
                ),
            )
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y + yOffset - layout.size.height / 2f))
        }
        label("VINTAGE", 0.22f, VV.WalnutDeep, FontWeight.Bold, -inner * 0.32f)
        label("VINYL", 0.22f, VV.WalnutDeep, FontWeight.Bold, inner * 0.28f)
        label(speedLabel, 0.13f, VV.CopperDim, FontWeight.Medium, inner * 0.68f)
    }

    // Spindle.
    drawCircle(VV.Gold, radius * 0.045f, c)
    drawCircle(VV.WalnutDeep, radius * 0.02f, c)
}

private fun DrawScope.drawTonearm(angleDeg: Float, lifted: Boolean) {
    val w = size.width
    val pivot = Offset(0.9f * w, 0.12f * w)
    val a = Math.toRadians(angleDeg.toDouble()).toFloat()
    val dir = Offset(-sin(a), cos(a))
    val tip = pivot + dir * (ARM_LENGTH * w)
    val back = pivot - dir * (0.13f * w)
    val shadow = if (lifted) Offset(w * 0.018f, w * 0.03f) else Offset(w * 0.008f, w * 0.012f)
    val shadowColor = Color.Black.copy(alpha = if (lifted) 0.22f else 0.4f)

    drawLine(shadowColor, back + shadow, tip + shadow, strokeWidth = w * 0.014f, cap = StrokeCap.Round)
    drawCircle(shadowColor, w * 0.062f, pivot + shadow)

    // Counterweight, arm tube, headshell, stylus.
    drawLine(VV.Charcoal, back, back + dir * (0.055f * w), strokeWidth = w * 0.05f, cap = StrokeCap.Butt)
    drawLine(VV.CreamDim, pivot, tip - dir * (0.06f * w), strokeWidth = w * 0.012f, cap = StrokeCap.Round)
    val shellStart = tip - dir * (0.085f * w)
    drawLine(VV.Charcoal, shellStart, tip, strokeWidth = w * 0.034f, cap = StrokeCap.Butt)
    drawLine(VV.Gold, shellStart, shellStart + dir * (0.012f * w), strokeWidth = w * 0.034f, cap = StrokeCap.Butt)
    drawCircle(VV.Copper, w * 0.01f, tip)

    // Pivot housing on top.
    drawCircle(VV.CharcoalLight, w * 0.06f, pivot)
    drawCircle(VV.Copper, w * 0.042f, pivot)
    drawCircle(VV.Gold, w * 0.02f, pivot)
}
