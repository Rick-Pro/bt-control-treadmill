package io.github.rickpro.stride.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.rickpro.stride.core.history.SpeedSample
import io.github.rickpro.stride.core.units.MeasureUnit
import io.github.rickpro.stride.core.units.Units
import io.github.rickpro.stride.ui.theme.StrideColors
import kotlin.math.max

/** Speed over belt time for one walk: a filled line on a few faint grid lines. Drawn with Canvas, no library. */
@Composable
fun SpeedChart(samples: List<SpeedSample>, unit: MeasureUnit, modifier: Modifier = Modifier) {
    val maxKmh = max(samples.maxOfOrNull { it.kmh } ?: 0.0, 1.0)
    val lastSecond = max(samples.lastOrNull()?.atSeconds ?: 0L, 1L)
    val stroke = StrideColors.Accent
    val fill = Brush.verticalGradient(listOf(StrideColors.Accent.copy(alpha = 0.35f), StrideColors.Accent.copy(alpha = 0.0f)))
    val grid = StrideColors.Outline

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val w = size.width
            val h = size.height
            // Three horizontal grid lines.
            for (i in 1..3) {
                val y = h * i / 4f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            if (samples.size < 2) return@Canvas
            fun x(s: SpeedSample) = (s.atSeconds.toFloat() / lastSecond.toFloat()) * w
            fun y(s: SpeedSample) = h - (s.kmh / maxKmh).toFloat() * (h * 0.92f)

            val line = Path()
            val area = Path()
            samples.forEachIndexed { i, s ->
                val px = x(s)
                val py = y(s)
                if (i == 0) {
                    line.moveTo(px, py)
                    area.moveTo(px, h)
                    area.lineTo(px, py)
                } else {
                    line.lineTo(px, py)
                    area.lineTo(px, py)
                }
            }
            area.lineTo(x(samples.last()), h)
            area.close()
            drawPath(area, fill)
            drawPath(line, stroke, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = StrideColors.Muted, modifier = Modifier.weight(1f))
            Text(
                "${Units.speedText(maxKmh, unit)} ${Units.speedUnitLabel(unit)} max",
                style = MaterialTheme.typography.labelSmall,
                color = StrideColors.Muted,
            )
            Text(
                Units.durationText(lastSecond),
                style = MaterialTheme.typography.labelSmall,
                color = StrideColors.Muted,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}
