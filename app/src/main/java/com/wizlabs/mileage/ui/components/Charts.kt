package com.wizlabs.mileage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz
import kotlin.math.roundToInt

/**
 * Smooth line chart with a gradient fill. Drag or tap to read any point; the readout sits above the chart.
 * [labels] are per point (e.g. dates); only the first and last are drawn on the axis.
 */
@Composable
fun LineChart(
    values: List<Double>,
    labels: List<String>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    color: Color = Wiz.c.accentInk,
    average: Double? = null,
    height: Int = 180,
    showReadout: Boolean = true,
) {
    if (values.isEmpty()) return
    var sel by remember(values) { mutableStateOf<Int?>(null) }
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val measurer = rememberTextMeasurer()
    val muted = Wiz.c.muted
    val line = Wiz.c.line
    val surface = Wiz.c.surface
    val axisStyle = TextStyle(fontSize = 11.sp, color = muted)

    Column(modifier) {
        if (showReadout) {
            val i = sel ?: values.lastIndex
            Row {
                Text(format(values[i]), style = MaterialTheme.typography.titleMedium.merge(Numeric), color = Wiz.c.text)
                Spacer(Modifier.height(0.dp))
                Text(
                    "  " + labels.getOrElse(i) { "" } + if (sel == null) "  ·  latest" else "",
                    style = MaterialTheme.typography.bodySmall, color = muted,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Bottom),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Canvas(
            Modifier.fillMaxWidth().height(height.dp)
                .pointerInput(values) {
                    detectTapGestures { o -> sel = nearest(o.x, size.width.toFloat(), values.size) }
                }
                .pointerInput(values) {
                    detectHorizontalDragGestures(onDragEnd = {}) { change, _ ->
                        sel = nearest(change.position.x, size.width.toFloat(), values.size)
                    }
                },
        ) {
            val axisH = 18.dp.toPx()
            val top = 8.dp.toPx()
            val h = size.height - axisH - top
            val w = size.width
            val all = values + listOfNotNull(average)
            var min = all.min(); var max = all.max()
            if (max - min < 1e-9) { min -= 1; max += 1 }
            val pad = (max - min) * 0.15
            min -= pad; max += pad
            fun x(i: Int) = if (values.size == 1) w / 2 else i * w / (values.size - 1)
            fun y(v: Double) = top + (h * (1 - (v - min) / (max - min))).toFloat()

            for (g in 0..2) {
                val gy = top + h * g / 2f
                drawLine(line, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            }
            val pts = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val p0 = pts[i - 1]; val p1 = pts[i]
                    val cx = (p0.x + p1.x) / 2
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }
            clipRect(right = w * progress.value) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(pts.last().x, top + h); lineTo(pts.first().x, top + h); close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = top, endY = top + h))
                drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            average?.let {
                val ay = y(it)
                drawLine(muted, Offset(0f, ay), Offset(w, ay), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                val t = measurer.measure("avg " + format(it), axisStyle)
                drawText(t, topLeft = Offset(4.dp.toPx(), (ay - t.size.height - 2).coerceAtLeast(0f)))
            }
            if (values.size <= 24) pts.forEach { p ->
                drawCircle(surface, 4.dp.toPx(), p); drawCircle(color, 2.5.dp.toPx(), p)
            }
            val s = sel ?: values.lastIndex
            val sp = pts[s]
            drawLine(color.copy(alpha = 0.35f), Offset(sp.x, top), Offset(sp.x, top + h), strokeWidth = 1.dp.toPx())
            drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), sp)
            drawCircle(color, 5.dp.toPx(), sp)

            if (labels.isNotEmpty()) {
                val l0 = measurer.measure(labels.first(), axisStyle)
                drawText(l0, topLeft = Offset(0f, size.height - l0.size.height))
                if (labels.size > 1) {
                    val l1 = measurer.measure(labels.last(), axisStyle)
                    drawText(l1, topLeft = Offset(w - l1.size.width, size.height - l1.size.height))
                }
            }
        }
    }
}

private fun nearest(x: Float, width: Float, n: Int): Int =
    if (n <= 1) 0 else (x / width * (n - 1)).roundToInt().coerceIn(0, n - 1)

/** Rounded bar chart. Tap a bar to read it. The selected (default: last) bar is drawn in the accent colour. */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Int = 170,
    readoutLabels: List<String> = labels,
) {
    if (values.isEmpty()) return
    var sel by remember(values) { mutableStateOf<Int?>(null) }
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val measurer = rememberTextMeasurer()
    val accent = Wiz.c.accent
    val idle = Wiz.c.surface2
    val muted = Wiz.c.muted
    val axisStyle = TextStyle(fontSize = 10.sp, color = muted)
    val selStyle = TextStyle(fontSize = 10.sp, color = Wiz.c.text, fontWeight = FontWeight.Bold)

    Column(modifier) {
        val i = sel ?: values.lastIndex
        Row {
            Text(format(values[i]), style = MaterialTheme.typography.titleMedium.merge(Numeric))
            Text("  " + readoutLabels.getOrElse(i) { "" }, style = MaterialTheme.typography.bodySmall, color = muted,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Bottom))
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier.fillMaxWidth().height(height.dp).pointerInput(values) {
                detectTapGestures { o ->
                    val slot = size.width.toFloat() / values.size
                    sel = (o.x / slot).toInt().coerceIn(0, values.lastIndex)
                }
            },
        ) {
            val axisH = 18.dp.toPx()
            val h = size.height - axisH
            val slot = size.width / values.size
            val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val max = values.max().takeIf { it > 0 } ?: 1.0
            values.forEachIndexed { idx, v ->
                val bh = (h * (v / max) * progress.value).toFloat().coerceAtLeast(if (v > 0) 3.dp.toPx() else 0f)
                val left = idx * slot + (slot - bw) / 2
                drawRoundRect(
                    if (idx == i) accent else idle,
                    topLeft = Offset(left, h - bh), size = Size(bw, bh),
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                )
                val step = if (values.size > 8) 2 else 1
                if (idx == i || (values.size - 1 - idx) % step == 0) {
                    val t = measurer.measure(labels.getOrElse(idx) { "" }, if (idx == i) selStyle else axisStyle)
                    drawText(t, topLeft = Offset(idx * slot + (slot - t.size.width) / 2, size.height - t.size.height))
                }
            }
        }
    }
}
