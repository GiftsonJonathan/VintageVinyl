package com.vintagevinyl.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vintagevinyl.ui.theme.VV
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A rotary control. Drag up or to the right to turn it up, down or left to turn it down; a full
 * sweep is about 200 dp of finger travel. Gives a small haptic "detent" every 5 %.
 */
@Composable
fun RotaryKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    knobSize: Dp = 68.dp,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    var lastDetent by remember { mutableIntStateOf((value * 20f).roundToInt()) }
    val percent = (value * 100f).roundToInt()

    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .size(knobSize)
                .semantics {
                    contentDescription = "$label $percent percent"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                    setProgress { v -> currentOnChange(v.coerceIn(0f, 1f)); true }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val travel = 200.dp.toPx()
                    detectDragGestures { change, drag ->
                        change.consume()
                        val next = (currentValue + (-drag.y + drag.x) / travel).coerceIn(0f, 1f)
                        val detent = (next * 20f).roundToInt()
                        if (detent != lastDetent) {
                            lastDetent = detent
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        currentOnChange(next)
                    }
                }
        ) {
            val r = size.minDimension / 2f
            val c = center
            val startAngle = 135f
            val sweep = 270f

            // Scale markings and value arc.
            val arcR = r * 0.94f
            val arcTopLeft = Offset(c.x - arcR, c.y - arcR)
            drawArc(VV.CreamDim.copy(alpha = 0.22f), startAngle, sweep, false, arcTopLeft, Size(arcR * 2, arcR * 2), style = Stroke(r * 0.07f))
            drawArc(
                if (enabled) VV.Gold else VV.GoldDim, startAngle, sweep * value, false,
                arcTopLeft, Size(arcR * 2, arcR * 2), style = Stroke(r * 0.07f),
            )

            // Knob body: dark skirt, brushed cap, knurled rim.
            val body = r * 0.74f
            drawCircle(Color.Black.copy(alpha = 0.45f), body * 1.06f, c + Offset(0f, r * 0.05f))
            drawCircle(VV.CharcoalLight, body, c)
            for (i in 0 until 28) {
                val a = (i / 28f) * 2f * PI.toFloat()
                drawLine(
                    VV.Charcoal, c + Offset(cos(a), sin(a)) * (body * 0.9f), c + Offset(cos(a), sin(a)) * body,
                    strokeWidth = r * 0.05f,
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(VV.Gold, VV.GoldDim), center = c - Offset(body * 0.2f, body * 0.25f), radius = body * 1.1f),
                body * 0.78f, c,
            )
            drawCircle(VV.Charcoal.copy(alpha = 0.25f), body * 0.78f, c, style = Stroke(r * 0.03f))

            // Pointer.
            val ang = Math.toRadians((startAngle + sweep * value).toDouble()).toFloat()
            val dir = Offset(cos(ang), sin(ang))
            drawLine(VV.Charcoal, c + dir * (body * 0.2f), c + dir * (body * 0.72f), strokeWidth = r * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = VV.Cream, textAlign = TextAlign.Center, maxLines = 1)
        Text("$percent", style = MaterialTheme.typography.labelSmall, color = VV.Gold)
    }
}

/** A horizontal channel fader with a cream cap. */
@Composable
fun HorizontalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    contentLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onValueChange)
    var lastDetent by remember { mutableIntStateOf((value * 20f).roundToInt()) }
    val percent = (value * 100f).roundToInt()

    fun fraction(x: Float, widthPx: Float, padPx: Float): Float =
        ((x - padPx) / (widthPx - 2f * padPx)).coerceIn(0f, 1f)

    fun commit(f: Float) {
        val detent = (f * 20f).roundToInt()
        if (detent != lastDetent) {
            lastDetent = detent
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        currentOnChange(f)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics {
                contentDescription = "$contentLabel $percent percent"
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> currentOnChange(v.coerceIn(0f, 1f)); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 16.dp.toPx()
                detectTapGestures { commit(fraction(it.x, this.size.width.toFloat(), pad)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 16.dp.toPx()
                detectDragGestures { change, _ ->
                    change.consume()
                    commit(fraction(change.position.x, this.size.width.toFloat(), pad))
                }
            }
    ) {
        val pad = 16.dp.toPx()
        val cy = size.height / 2f
        val trackH = 6.dp.toPx()
        val usable = size.width - 2 * pad

        // Printed scale under the groove.
        for (i in 0..10) {
            val x = pad + usable * i / 10f
            val len = if (i % 5 == 0) 7.dp.toPx() else 4.dp.toPx()
            drawLine(VV.CreamDim.copy(alpha = 0.5f), Offset(x, cy + trackH + 2.dp.toPx()), Offset(x, cy + trackH + 2.dp.toPx() + len), strokeWidth = 1.dp.toPx())
        }
        // Groove and filled part.
        drawRoundRect(VV.Charcoal, Offset(pad, cy - trackH / 2), Size(usable, trackH), CornerRadius(trackH / 2))
        drawRoundRect(VV.Copper, Offset(pad, cy - trackH / 2), Size(usable * value, trackH), CornerRadius(trackH / 2))
        // Cap.
        val capW = 22.dp.toPx()
        val capH = 30.dp.toPx()
        val x = pad + usable * value
        drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(x - capW / 2 + 1.5.dp.toPx(), cy - capH / 2 + 2.dp.toPx()), Size(capW, capH), CornerRadius(4.dp.toPx()))
        drawRoundRect(VV.Cream, Offset(x - capW / 2, cy - capH / 2), Size(capW, capH), CornerRadius(4.dp.toPx()))
        drawRoundRect(VV.CharcoalLight, Offset(x - 1.dp.toPx(), cy - capH / 2 + 4.dp.toPx()), Size(2.dp.toPx(), capH - 8.dp.toPx()), CornerRadius(1.dp.toPx()))
        drawRoundRect(VV.GoldDim, Offset(x - capW / 2, cy - capH / 2), Size(capW, capH), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

/** A toggle with a brass lever and a lamp that glows amber when switched on. */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentLabel: String,
    modifier: Modifier = Modifier,
    width: Dp = 52.dp,
) {
    val haptics = LocalHapticFeedback.current
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "toggle")
    val height = width * 0.55f

    Canvas(
        modifier
            .size(width, height)
            .semantics { contentDescription = contentLabel; role = Role.Switch }
            .toggleable(value = checked, role = Role.Switch, onValueChange = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            })
    ) {
        val corner = CornerRadius(size.height / 2)
        drawRoundRect(VV.Charcoal, Offset.Zero, size, corner)
        if (pos > 0.01f) {
            drawRoundRect(VV.Lamp.copy(alpha = 0.28f * pos), Offset.Zero, size, corner)
        }
        drawRoundRect(if (checked) VV.Lamp.copy(alpha = 0.8f) else VV.GoldDim, Offset.Zero, size, corner, style = Stroke(1.5.dp.toPx()))
        val knobR = size.height * 0.36f
        val x = size.height / 2f + (size.width - size.height) * pos
        drawCircle(Color.Black.copy(alpha = 0.4f), knobR, Offset(x + 1.dp.toPx(), size.height / 2f + 1.5.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(VV.Cream, VV.Gold), Offset(x - knobR * 0.3f, size.height / 2f - knobR * 0.3f), knobR * 1.6f), knobR, Offset(x, size.height / 2f))
        drawCircle(VV.GoldDim, knobR, Offset(x, size.height / 2f), style = Stroke(1.dp.toPx()))
    }
}

/** A round brass button used for transport controls; shrinks slightly while pressed. */
@Composable
fun BrassButton(
    onClick: () -> Unit,
    contentLabel: String,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 56.dp,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = 900f), label = "press")
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (primary) VV.Gold else VV.WalnutMid)
            .border(BorderStroke(1.5.dp, if (primary) VV.Cream.copy(alpha = 0.5f) else VV.GoldDim), CircleShape)
            .semantics { contentDescription = contentLabel; role = Role.Button }
            .clickable(interactionSource = source, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Play / pause button that cross-fades its glyph. */
@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, buttonSize: Dp = 72.dp) {
    BrassButton(onClick, if (isPlaying) "Pause" else "Play", modifier, buttonSize, primary = true) {
        Crossfade(isPlaying, label = "playpause") { playing ->
            GlyphIcon(if (playing) Glyph.PAUSE else Glyph.PLAY, Modifier.size(buttonSize * 0.45f), VV.Charcoal)
        }
    }
}

/** A selectable pill in a settings row. */
@Composable
fun SelectorChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) VV.WalnutLight else VV.WalnutMid.copy(alpha = 0.6f))
            .border(1.dp, if (selected) VV.Gold else VV.GoldDim.copy(alpha = 0.5f), shape)
            .semantics { role = Role.Button; contentDescription = if (selected) "$text, selected" else text }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) VV.Gold else VV.Cream, maxLines = 1)
        trailing?.invoke()
    }
}

/** Horizontally scrolling row of items, used for the chip selectors. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** An engraved section caption with a gold rule. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = VV.Gold)
        Box(
            Modifier
                .padding(start = 10.dp)
                .weight(1f)
                .height(1.dp)
                .background(VV.GoldDim.copy(alpha = 0.5f))
        )
    }
}

/** "3:07" or "1:02:09". */
fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The progress bar: a brass rail with a marker. Tap or drag to seek; the position is committed
 * when the finger lifts, so scrubbing does not make the player stutter.
 */
@Composable
fun SeekSection(
    positionProvider: () -> Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val position = positionProvider()
    val fraction = dragFraction ?: if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = if (dragFraction != null) (durationMs * fraction).toLong() else position
    var widthPx by remember { mutableFloatStateOf(1f) }

    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .semantics {
                    contentDescription = "Playback position ${formatTime(shownMs)} of ${formatTime(durationMs)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    if (enabled && durationMs > 0) setProgress { v -> currentOnSeek((v * durationMs).toLong()); true }
                }
                .pointerInput(enabled, durationMs) {
                    if (!enabled || durationMs <= 0) return@pointerInput
                    val pad = 10.dp.toPx()
                    fun frac(x: Float) = ((x - pad) / (this.size.width - 2 * pad)).coerceIn(0f, 1f)
                    detectTapGestures { currentOnSeek((frac(it.x) * durationMs).toLong()) }
                }
                .pointerInput(enabled, durationMs) {
                    if (!enabled || durationMs <= 0) return@pointerInput
                    val pad = 10.dp.toPx()
                    fun frac(x: Float) = ((x - pad) / (this.size.width - 2 * pad)).coerceIn(0f, 1f)
                    detectDragGestures(
                        onDragStart = { dragFraction = frac(it.x) },
                        onDragEnd = {
                            dragFraction?.let { currentOnSeek((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = frac(change.position.x)
                    }
                }
        ) {
            val pad = 10.dp.toPx()
            val cy = size.height / 2f
            val usable = size.width - 2 * pad
            drawRoundRect(VV.Charcoal, Offset(pad, cy - 2.dp.toPx()), Size(usable, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawRoundRect(VV.Gold, Offset(pad, cy - 2.dp.toPx()), Size(usable * fraction, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            for (i in 0..20) {
                val x = pad + usable * i / 20f
                drawLine(VV.CreamDim.copy(alpha = 0.35f), Offset(x, cy + 6.dp.toPx()), Offset(x, cy + (if (i % 5 == 0) 11.dp.toPx() else 8.dp.toPx())), strokeWidth = 1.dp.toPx())
            }
            val x = pad + usable * fraction
            drawCircle(Color.Black.copy(alpha = 0.4f), 8.dp.toPx(), Offset(x + 1.dp.toPx(), cy + 1.5.dp.toPx()))
            drawCircle(VV.Cream, 8.dp.toPx(), Offset(x, cy))
            drawCircle(VV.Copper, 3.dp.toPx(), Offset(x, cy))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shownMs), style = MaterialTheme.typography.labelMedium, color = VV.CreamDim)
            Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium, color = VV.CreamDim)
        }
    }
}
