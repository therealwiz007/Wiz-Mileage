package com.wizlabs.mileage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Colours of the always-dark "cockpit" card, so the gauge looks the same in light and dark themes. */
object Cockpit {
    val top = Color(0xFF1C1C22)
    val bottom = Color(0xFF0B0B0E)
    val text = Color(0xFFF5F5F7)
    val muted = Color(0xFF9A9AA6)
    val track = Color(0xFF2A2A33)
    val red = Color(0xFFFF453A)
    val orange = Color(0xFFFF9F0A)
    val yellow = Color(0xFFFFD60A)
    val green = Color(0xFF30D158)
}

/** Animates from 0 to [target] whenever the target changes. */
@Composable
fun animatedValue(target: Double, durationMs: Int = 1100): Double {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.value.toDouble()
}

private const val START = 150f
private const val SWEEP = 240f

/**
 * Speedometer-style gauge: a red→green arc over [0, max], ticks, a sprung needle and the value in the centre.
 */
@Composable
fun MileageGauge(
    value: Double?,
    max: Double,
    unitLabel: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val frac = ((value ?: 0.0) / max).coerceIn(0.0, 1.0).toFloat()
    val needle = remember { Animatable(0f) }
    LaunchedEffect(frac) { needle.animateTo(frac, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessVeryLow)) }
    val shown = animatedValue(value ?: 0.0)
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(fontSize = 11.sp, color = Cockpit.muted, fontFeatureSettings = "tnum")

    Box(modifier.aspectRatio(1.18f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val r = min(size.width / 2f, size.height / 1.62f) - stroke
            val c = Offset(size.width / 2f, r + stroke + 4.dp.toPx())
            val tl = Offset(c.x - r, c.y - r)
            val arcSize = Size(r * 2, r * 2)

            drawArc(Cockpit.track, START, SWEEP, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val brush = Brush.sweepGradient(
                0f to Cockpit.red, 0.25f to Cockpit.orange, 0.45f to Cockpit.yellow, 0.667f to Cockpit.green,
                0.83f to Cockpit.green, 1f to Cockpit.red, center = c,
            )
            rotate(START, c) {
                drawArc(brush, 0f, SWEEP * needle.value.coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Ticks
            for (i in 0..12) {
                val a = Math.toRadians((START + SWEEP * i / 12f).toDouble())
                val major = i % 3 == 0
                val r1 = r - stroke / 2 - 6.dp.toPx()
                val r2 = r1 - (if (major) 10.dp.toPx() else 5.dp.toPx())
                drawLine(
                    if (major) Cockpit.muted else Cockpit.track,
                    Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                    Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                    strokeWidth = (if (major) 2.dp else 1.5.dp).toPx(), cap = StrokeCap.Round,
                )
                if (major) {
                    val label = measurer.measure(Math.round(max * i / 12).toString(), tickStyle)
                    val rl = r2 - 12.dp.toPx()
                    drawText(label, topLeft = Offset(
                        c.x + rl * cos(a).toFloat() - label.size.width / 2f,
                        c.y + rl * sin(a).toFloat() - label.size.height / 2f,
                    ))
                }
            }
            // Needle
            val na = Math.toRadians((START + SWEEP * needle.value).toDouble())
            val len = r - stroke - 2.dp.toPx()
            val tip = Offset(c.x + len * cos(na).toFloat(), c.y + len * sin(na).toFloat())
            val back = Offset(c.x - 14.dp.toPx() * cos(na).toFloat(), c.y - 14.dp.toPx() * sin(na).toFloat())
            if (value != null) {
                drawLine(Cockpit.yellow.copy(alpha = 0.25f), back, tip, strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Cockpit.text, back, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Cockpit.yellow, 4.dp.toPx(), tip)
            }
            drawCircle(Cockpit.top, 13.dp.toPx(), c)
            drawCircle(Cockpit.yellow, 13.dp.toPx(), c, style = Stroke(3.dp.toPx()))
            drawCircle(Cockpit.yellow, 5.dp.toPx(), c)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (value == null) "—" else String.format(Locale.US, "%.1f", shown),
                    style = MaterialTheme.typography.displayMedium.merge(Numeric), fontWeight = FontWeight.Black, color = Cockpit.text,
                )
                Text(" $unitLabel", style = MaterialTheme.typography.titleMedium, color = Cockpit.muted, modifier = Modifier.padding(bottom = 9.dp))
            }
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Cockpit.muted)
        }
    }
}

/** Progress ring with a centred label. [progress] may exceed 1 (drawn as a full ring plus an overflow arc). */
@Composable
fun Ring(progress: Float, color: Color, modifier: Modifier = Modifier, thickness: Int = 10, center: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(progress) { a.animateTo(progress, tween(1000, easing = FastOutSlowInEasing)) }
    val track = Wiz.c.surface2
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = thickness.dp.toPx()
            val tl = Offset(s / 2, s / 2); val sz = Size(size.width - s, size.height - s)
            drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(s))
            drawArc(color, -90f, 360f * min(a.value, 1f), false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
            if (a.value > 1f) drawArc(Cockpit.red, -90f, 360f * min(a.value - 1f, 1f), false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val a = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { a.animateTo(1f, tween(900)) }
    Canvas(modifier) {
        val lo = values.min(); val hi = values.max().let { if (it - lo < 1e-9) lo + 1 else it }
        val pts = values.mapIndexed { i, v ->
            Offset(i * size.width / (values.size - 1), (size.height * (1 - (v - lo) / (hi - lo))).toFloat().coerceIn(2f, size.height - 2f))
        }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        clipRect(right = size.width * a.value) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(color, 3.dp.toPx(), pts.last())
    }
}

/** GitHub-style calendar of the last [weeks] weeks; each cell is coloured by money spent that day. */
@Composable
fun ActivityHeatmap(spendByDay: Map<LocalDate, Double>, today: LocalDate, modifier: Modifier = Modifier, weeks: Int = 17) {
    val accent = Wiz.c.accent
    val empty = Wiz.c.surface2
    val muted = Wiz.c.muted
    val todayRing = Wiz.c.text.copy(alpha = 0.6f)
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = muted)
    val maxSpend = spendByDay.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val firstMonday = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(900)) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(weeks / 7f * 0.92f + 0.05f)) {
            val labelH = 14.dp.toPx()
            val gap = 3.dp.toPx()
            val cell = min((size.width - gap * (weeks - 1)) / weeks, (size.height - labelH - gap * 6) / 7)
            var lastMonth = -1
            for (w in 0 until weeks) {
                val x = w * (cell + gap)
                val monday = firstMonday.plusWeeks(w.toLong())
                if (monday.monthValue != lastMonth && monday.dayOfMonth <= 7) {
                    val t = measurer.measure(monday.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH), style)
                    if (x + t.size.width <= size.width) drawText(t, topLeft = Offset(x, 0f))
                }
                lastMonth = monday.monthValue
                for (d in 0 until 7) {
                    val day = monday.plusDays(d.toLong())
                    if (day.isAfter(today)) continue
                    val spend = spendByDay[day] ?: 0.0
                    val level = if (spend <= 0) 0f else 0.3f + 0.7f * (spend / maxSpend).toFloat()
                    val reveal = ((a.value * (weeks + 3)) - w).coerceIn(0f, 1f)
                    drawRoundRect(
                        if (level == 0f) empty else accent.copy(alpha = level * reveal + (1 - reveal) * 0.05f),
                        topLeft = Offset(x, labelH + d * (cell + gap)), size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.28f),
                    )
                    if (day == today) drawRoundRect(
                        todayRing, topLeft = Offset(x, labelH + d * (cell + gap)), size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.28f), style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.width(6.dp))
            listOf(0f, 0.35f, 0.6f, 0.8f, 1f).forEach { l ->
                Canvas(Modifier.size(11.dp)) {
                    drawRoundRect(if (l == 0f) empty else accent.copy(alpha = l), cornerRadius = CornerRadius(3.dp.toPx()))
                }
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(3.dp))
            Text("More ₹", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

data class Slice(val label: String, val value: Double, val color: Color)

@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier, thickness: Int = 16, center: @Composable () -> Unit) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    val a = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { a.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = thickness.dp.toPx()
            val tl = Offset(s / 2, s / 2); val sz = Size(size.width - s, size.height - s)
            val gap = if (slices.size > 1) 3f else 0f
            var start = -90f
            slices.forEach { sl ->
                val sweep = (360f * (sl.value / total).toFloat()) * a.value
                drawArc(sl.color, start + gap / 2, max(sweep - gap, 0.5f), false, tl, sz, style = Stroke(s, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}
