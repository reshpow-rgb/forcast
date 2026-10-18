package com.forcast.asxvalue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.forcast.asxvalue.core.model.PricePoint
import com.forcast.asxvalue.core.valuation.PriceStats
import com.forcast.asxvalue.ui.Fmt
import com.forcast.asxvalue.ui.theme.ValueColors

/**
 * Line chart of closing prices with an optional moving average and a dashed fair-value line.
 * Drag across the chart to read the price on a given date.
 */
@Composable
fun PriceChart(
    points: List<PricePoint>,
    fairValue: Double?,
    maWindow: Int?,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("No price history", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val closes = remember(points) { points.map { it.close } }
    val ma = remember(points, maWindow) { maWindow?.let { PriceStats.movingAverage(closes, it) } }
    val up = closes.last() >= closes.first()
    val lineColor = if (up) ValueColors.Positive else ValueColors.Negative
    val maColor = MaterialTheme.colorScheme.tertiary
    val fairColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    Column(modifier) {
        val sel = selected
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sel != null) {
                Text(
                    "${Fmt.date(points[sel].epochSec)}  ${Fmt.money(points[sel].close)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                Text("Drag to inspect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Stretch the axis to show the fair-value line unless it is far outside the price range.
        val fvInRange = fairValue?.takeIf { it >= closes.min() * 0.6 && it <= closes.max() * 1.6 }
        val lo = minOf(closes.min(), fvInRange ?: closes.min())
        val hi = maxOf(closes.max(), fvInRange ?: closes.max())
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(points) {
                    fun indexAt(x: Float) = ((x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    detectDragGestures(
                        onDragStart = { selected = indexAt(it.x) },
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                        onDrag = { change, _ -> selected = indexAt(change.position.x) },
                    )
                }
                .pointerInput(points) {
                    detectTapGestures(onPress = {
                        selected = ((it.x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                        tryAwaitRelease()
                        selected = null
                    })
                },
        ) {
            val range = (hi - lo).takeIf { it > 0 } ?: 1.0
            val padTop = 8f
            val padBottom = 8f
            val h = size.height - padTop - padBottom
            fun x(i: Int) = i.toFloat() / (points.size - 1) * size.width
            fun y(v: Double) = padTop + ((hi - v) / range * h).toFloat()

            for (k in 0..3) {
                val gy = padTop + h * k / 3f
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }

            val line = Path()
            closes.forEachIndexed { i, c -> if (i == 0) line.moveTo(x(i), y(c)) else line.lineTo(x(i), y(c)) }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(line, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            ma?.let { series ->
                val p = Path()
                var started = false
                series.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    if (!started) { p.moveTo(x(i), y(v)); started = true } else p.lineTo(x(i), y(v))
                }
                if (started) drawPath(p, maColor, style = Stroke(width = 2f))
            }

            fairValue?.takeIf { it in lo..hi }?.let { fv ->
                drawLine(
                    fairColor, Offset(0f, y(fv)), Offset(size.width, y(fv)), strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                )
            }

            sel?.let { i ->
                drawLine(cursorColor.copy(alpha = 0.5f), Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = 1.5f)
                drawCircle(lineColor, radius = 7f, center = Offset(x(i), y(closes[i])))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.date(points.first().epochSec), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("Low ${Fmt.money(closes.min())} · High ${Fmt.money(closes.max())}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(Fmt.date(points.last().epochSec), style = MaterialTheme.typography.labelSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(lineColor); Text(" Price  ", style = MaterialTheme.typography.labelSmall)
            if (ma != null) { LegendDot(maColor); Text(" ${maWindow}-period average  ", style = MaterialTheme.typography.labelSmall) }
            if (fairValue != null && fairValue in lo..hi) { LegendDot(fairColor); Text(" Fair value", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
    Spacer(Modifier.width(2.dp))
}
