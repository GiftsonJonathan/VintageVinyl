package com.vintagevinyl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.vintagevinyl.ui.theme.VV

/**
 * The icon set, drawn as vector paths on a 24 x 24 grid. Drawing them here (instead of using
 * the Material icon packs) keeps the dependency list short and lets them share the engraved,
 * slightly heavy line weight of the rest of the interface.
 */
enum class Glyph {
    PLAY, PAUSE, NEXT, PREV, SHUFFLE, REPEAT, REPEAT_ONE, QUEUE, PLUS, SEARCH, CLOSE, MORE,
    VOLUME, IMPORT, CHEVRON_LEFT, CHEVRON_UP, CHEVRON_DOWN, TRASH, NOTE, POWER, DISC, LIBRARY, SLIDERS,
}

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = VV.Cream) {
    Canvas(modifier.size(24.dp)) { drawGlyph(glyph, tint, size.minDimension / 24f) }
}

private fun DrawScope.drawGlyph(g: Glyph, c: Color, s: Float) {
    val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun poly(close: Boolean, vararg pts: Float): Path {
        val path = Path()
        path.moveTo(pts[0] * s, pts[1] * s)
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i] * s, pts[i + 1] * s)
            i += 2
        }
        if (close) path.close()
        return path
    }

    fun line(vararg pts: Float) = drawPath(poly(false, *pts), c, style = stroke)
    fun fill(vararg pts: Float) = drawPath(poly(true, *pts), c)
    fun rect(x: Float, y: Float, w: Float, h: Float) =
        drawRect(c, Offset(x * s, y * s), Size(w * s, h * s))

    when (g) {
        Glyph.PLAY -> fill(8f, 5f, 19f, 12f, 8f, 19f)
        Glyph.PAUSE -> { rect(6f, 5f, 4f, 14f); rect(14f, 5f, 4f, 14f) }
        Glyph.NEXT -> { fill(6f, 5f, 16f, 12f, 6f, 19f); rect(16.5f, 5f, 2.5f, 14f) }
        Glyph.PREV -> { fill(18f, 5f, 8f, 12f, 18f, 19f); rect(5f, 5f, 2.5f, 14f) }
        Glyph.SHUFFLE -> {
            line(3f, 7f, 8f, 7f, 16f, 17f, 19f, 17f)
            line(3f, 17f, 8f, 17f, 10.5f, 14f)
            line(13.5f, 10f, 16f, 7f, 19f, 7f)
            fill(19f, 14f, 23f, 17f, 19f, 20f)
            fill(19f, 4f, 23f, 7f, 19f, 10f)
        }
        Glyph.REPEAT, Glyph.REPEAT_ONE -> {
            line(4f, 12f, 4f, 8f, 17f, 8f)
            fill(16f, 4.5f, 21f, 8f, 16f, 11.5f)
            line(20f, 12f, 20f, 16f, 7f, 16f)
            fill(8f, 12.5f, 3f, 16f, 8f, 19.5f)
            if (g == Glyph.REPEAT_ONE) line(11f, 11f, 12.4f, 10.2f, 12.4f, 14f)
        }
        Glyph.QUEUE -> {
            line(4f, 7f, 15f, 7f)
            line(4f, 12f, 15f, 12f)
            line(4f, 17f, 10f, 17f)
            fill(16f, 12f, 21f, 15f, 16f, 18f)
        }
        Glyph.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Glyph.SEARCH -> {
            drawCircle(c, radius = 6f * s, center = Offset(10f * s, 10f * s), style = stroke)
            line(14.5f, 14.5f, 20f, 20f)
        }
        Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Glyph.MORE -> for (y in listOf(5f, 12f, 19f)) drawCircle(c, 1.8f * s, Offset(12f * s, y * s))
        Glyph.VOLUME -> {
            fill(4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f)
            drawArc(c, -50f, 100f, false, Offset(9f * s, 8f * s), Size(8f * s, 8f * s), style = stroke)
            drawArc(c, -50f, 100f, false, Offset(6f * s, 5f * s), Size(14f * s, 14f * s), style = stroke)
        }
        Glyph.IMPORT -> {
            line(12f, 4f, 12f, 14f)
            line(7.5f, 10f, 12f, 14.5f, 16.5f, 10f)
            line(5f, 19f, 19f, 19f)
        }
        Glyph.CHEVRON_LEFT -> line(15f, 5f, 8f, 12f, 15f, 19f)
        Glyph.CHEVRON_UP -> line(5f, 15f, 12f, 8f, 19f, 15f)
        Glyph.CHEVRON_DOWN -> line(5f, 9f, 12f, 16f, 19f, 9f)
        Glyph.TRASH -> {
            line(5f, 7f, 19f, 7f)
            line(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
            line(7f, 7f, 8f, 20f, 16f, 20f, 17f, 7f)
        }
        Glyph.NOTE -> {
            drawCircle(c, 3f * s, Offset(8f * s, 18f * s))
            line(11f, 18f, 11f, 5f, 18f, 7f)
        }
        Glyph.POWER -> {
            drawArc(c, -60f, 300f, false, Offset(5f * s, 5f * s), Size(14f * s, 14f * s), style = stroke)
            line(12f, 3f, 12f, 12f)
        }
        Glyph.DISC -> {
            drawCircle(c, 9f * s, Offset(12f * s, 12f * s), style = stroke)
            drawCircle(c, 2.5f * s, Offset(12f * s, 12f * s))
        }
        Glyph.LIBRARY -> {
            rect(4f, 5f, 3.5f, 14f); rect(9.5f, 5f, 3.5f, 14f)
            line(16f, 19f, 20f, 6f)
        }
        Glyph.SLIDERS -> {
            line(6f, 4f, 6f, 20f); line(12f, 4f, 12f, 20f); line(18f, 4f, 18f, 20f)
            rect(3.5f, 14f, 5f, 3f); rect(9.5f, 7f, 5f, 3f); rect(15.5f, 11f, 5f, 3f)
        }
    }
}
