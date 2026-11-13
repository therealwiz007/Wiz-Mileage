package com.wizlabs.mileage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizlabs.mileage.ui.motion.LocalMotion
import com.wizlabs.mileage.ui.motion.rememberAmbientClock
import com.wizlabs.mileage.ui.theme.Numeric
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val START = 150f
private const val SWEEP = 240f

/**
 * The hero: a speedometer-style mileage gauge. The needle springs to the value, a glint runs along the lit
 * arc and the needle tip breathes. All geometry, brushes and labels are built once in [drawWithCache];
 * per frame we only read two floats in the draw phase, so nothing recomposes or allocates.
 */
@Composable
fun MileageGauge(
    value: Double?,
    max: Double,
    unitLabel: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val frac = ((value ?: 0.0) / max).coerceIn(0.0, 1.0).toFloat()
    val needle = remember { Animatable(0f) }
    LaunchedEffect(frac, motion) {
        if (motion) {
            // Rev past the value like a real dial waking up, then settle on it with a spring.
            if (needle.value == 0f && frac > 0f) needle.animateTo((frac + 0.18f).coerceAtMost(1f), tween(520, easing = FastOutSlowInEasing))
            needle.animateTo(frac, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessVeryLow))
        } else needle.snapTo(frac)
    }
    val clock = rememberAmbientClock()
    val shown = animatedValue(value ?: 0.0)
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(fontSize = 11.sp, color = Cockpit.muted, fontFeatureSettings = "tnum")
    val labels = remember(max) { (0..4).map { measurer.measure(Math.round(max * it / 4).toString(), tickStyle) } }
    val hasValue = value != null

    Box(modifier.aspectRatio(1.18f), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val stroke = 16.dp.toPx()
                val r = min(size.width / 2f, size.height / 1.62f) - stroke
                val c = Offset(size.width / 2f, r + stroke + 4.dp.toPx())
                val tl = Offset(c.x - r, c.y - r)
                val arcSize = Size(r * 2, r * 2)
                val arcStroke = Stroke(stroke, cap = StrokeCap.Round)
                val glowStroke = Stroke(stroke * 2.1f, cap = StrokeCap.Round)
                val glintStroke = Stroke(stroke * 0.42f, cap = StrokeCap.Round)
                val brush = Brush.sweepGradient(
                    0f to Cockpit.red, 0.25f to Cockpit.orange, 0.45f to Cockpit.yellow, 0.667f to Cockpit.green,
                    0.83f to Cockpit.green, 1f to Cockpit.red, center = c,
                )
                // Tick geometry, precomputed: x1, y1, x2, y2 per tick.
                val ticks = FloatArray(13 * 4)
                val labelPos = FloatArray(5 * 2)
                for (i in 0..12) {
                    val a = Math.toRadians((START + SWEEP * i / 12f).toDouble())
                    val major = i % 3 == 0
                    val r1 = r - stroke / 2 - 6.dp.toPx()
                    val r2 = r1 - (if (major) 10.dp.toPx() else 5.dp.toPx())
                    ticks[i * 4] = c.x + r1 * cos(a).toFloat(); ticks[i * 4 + 1] = c.y + r1 * sin(a).toFloat()
                    ticks[i * 4 + 2] = c.x + r2 * cos(a).toFloat(); ticks[i * 4 + 3] = c.y + r2 * sin(a).toFloat()
                    if (major) {
                        val l = labels[i / 3]; val rl = r2 - 12.dp.toPx()
                        labelPos[(i / 3) * 2] = c.x + rl * cos(a).toFloat() - l.size.width / 2f
                        labelPos[(i / 3) * 2 + 1] = c.y + rl * sin(a).toFloat() - l.size.height / 2f
                    }
                }
                val majorW = 2.dp.toPx(); val minorW = 1.5.dp.toPx()
                val len = r - stroke - 2.dp.toPx(); val backLen = 14.dp.toPx()
                val hubR = 13.dp.toPx(); val hubStroke = Stroke(3.dp.toPx())
                val glowColor = Cockpit.yellow

                onDrawBehind {
                    val n = needle.value.coerceIn(0f, 1f)
                    val t = clock.value
                    drawArc(Cockpit.track, START, SWEEP, false, tl, arcSize, style = arcStroke)
                    rotate(START, c) {
                        // Soft under-glow, then the coloured arc.
                        drawArc(brush, 0f, SWEEP * n, false, tl, arcSize, alpha = 0.16f, style = glowStroke)
                        drawArc(brush, 0f, SWEEP * n, false, tl, arcSize, style = arcStroke)
                    }
                    // A glint that runs along the lit arc every ~3.5 s.
                    if (hasValue && n > 0.04f) {
                        val g = (t * 0.32f) % 1.15f
                        if (g < 1f) {
                            val alpha = sin(PI * g).toFloat() * 0.55f
                            drawArc(Color.White, START + SWEEP * n * g - 3f, 6f, false, tl, arcSize, alpha = alpha, style = glintStroke)
                        }
                    }
                    for (i in 0..12) {
                        val major = i % 3 == 0
                        drawLine(
                            if (major) Cockpit.muted else Cockpit.track,
                            Offset(ticks[i * 4], ticks[i * 4 + 1]), Offset(ticks[i * 4 + 2], ticks[i * 4 + 3]),
                            strokeWidth = if (major) majorW else minorW, cap = StrokeCap.Round,
                        )
                    }
                    for (k in 0..4) drawText(labels[k], topLeft = Offset(labelPos[k * 2], labelPos[k * 2 + 1]))

                    if (hasValue) {
                        val na = Math.toRadians((START + SWEEP * n).toDouble())
                        val ca = cos(na).toFloat(); val sa = sin(na).toFloat()
                        val tip = Offset(c.x + len * ca, c.y + len * sa)
                        val back = Offset(c.x - backLen * ca, c.y - backLen * sa)
                        val breathe = 0.5f + 0.5f * sin(t * 2.2f)
                        drawLine(glowColor, back, tip, strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round, alpha = 0.16f + 0.14f * breathe)
                        drawLine(Cockpit.text, back, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(glowColor, 4.dp.toPx() + 5.dp.toPx() * breathe, tip, alpha = 0.18f * (1f - breathe) + 0.05f)
                        drawCircle(glowColor, 4.dp.toPx(), tip)
                    }
                    drawCircle(Cockpit.top, hubR, c)
                    drawCircle(Cockpit.yellow, hubR, c, style = hubStroke)
                    drawCircle(Cockpit.yellow, 5.dp.toPx(), c)
                }
            },
        )
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

/**
 * A fuel drop that fills to [level] (0..1) with a gently rolling surface and a few rising bubbles.
 * The drop outline and gradients are cached; the wave reuses one [Path] (rewound each frame).
 */
@Composable
fun FuelDrop(level: Float, modifier: Modifier = Modifier, empty: Boolean = false) {
    val motion = LocalMotion.current
    val fill = remember { Animatable(0f) }
    LaunchedEffect(level, motion) {
        val target = level.coerceIn(0f, 1f)
        if (motion) fill.animateTo(target, spring(dampingRatio = 0.55f, stiffness = 60f)) else fill.snapTo(target)
    }
    val clock = rememberAmbientClock()
    Spacer(
        modifier.aspectRatio(0.78f).drawWithCache {
            val w = size.width; val h = size.height
            val rad = w / 2f; val cx = w / 2f; val cy = h - rad
            val drop = Path().apply {
                moveTo(cx, 0f)
                cubicTo(cx + rad * 0.22f, rad * 0.9f, cx + rad, cy - rad * 0.8f, cx + rad, cy)
                arcTo(Rect(Offset(cx, cy), rad), 0f, 180f, false)
                cubicTo(cx - rad, cy - rad * 0.8f, cx - rad * 0.22f, rad * 0.9f, cx, 0f)
                close()
            }
            val liquid = Brush.verticalGradient(listOf(Cockpit.yellow, Cockpit.orange), startY = h * 0.2f, endY = h)
            val wave = Path()
            val back = Path()
            val outline = Stroke(2.dp.toPx())
            val amp = 2.6.dp.toPx()
            val bubbleR = 1.8.dp.toPx()
            fun buildWave(p: Path, y: Float, phase: Float, a: Float) {
                p.rewind()
                p.moveTo(0f, y)
                val steps = 18
                for (i in 0..steps) {
                    val x = w * i / steps
                    p.lineTo(x, y + a * sin(x / w * 2f * PI.toFloat() * 1.15f + phase))
                }
                p.lineTo(w, h); p.lineTo(0f, h); p.close()
            }
            onDrawBehind {
                val t = clock.value
                val lv = fill.value.coerceIn(0f, 1.05f)
                drawPath(drop, Color.White, alpha = 0.06f)
                if (!empty && lv > 0.001f) {
                    val surface = h - lv * h * 0.94f
                    clipPath(drop) {
                        buildWave(back, surface - amp * 0.6f, -t * 2.1f + 1.3f, amp * 0.8f)
                        drawPath(back, Cockpit.orange, alpha = 0.45f)
                        buildWave(wave, surface, t * 2.6f, amp)
                        drawPath(wave, liquid)
                        // Bubbles rising through the fuel.
                        for (b in 0..2) {
                            val life = ((t * 0.45f + b * 0.37f) % 1f)
                            val by = h - life * (h - surface)
                            val bx = cx + rad * 0.45f * sin(b * 2.1f + t * 1.3f)
                            if (by > surface + bubbleR * 2) drawCircle(Color.White, bubbleR * (0.7f + b * 0.2f), Offset(bx, by), alpha = 0.35f * (1f - life))
                        }
                    }
                }
                drawPath(drop, Cockpit.yellow, alpha = 0.75f, style = outline)
                // Shine
                translate(left = cx - rad * 0.5f, top = cy - rad * 0.35f) {
                    drawCircle(Color.White, rad * 0.16f, Offset.Zero, alpha = 0.22f)
                }
            }
        },
    )
}

/** Count-up number: animates from the previous value and lands exactly on [value]. */
@Composable
fun CountUp(
    value: Double,
    format: (Double) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    durationMs: Int = 1100,
) {
    // Rolls only the changed digits (odometer) instead of re-formatting and recomposing every frame.
    val motion = com.wizlabs.mileage.ui.motion.LocalMotion.current
    var shown by remember { mutableStateOf(if (motion) 0.0 else value) }
    LaunchedEffect(value) { if (motion) androidx.compose.runtime.withFrameNanos { }; shown = value }
    com.wizlabs.mileage.ui.motion.RollingNumber(
        format(shown), style.merge(Numeric).let { if (fontWeight != null) it.copy(fontWeight = fontWeight) else it }, modifier, color,
    )
}

/** Gentle float for small illustrations; layer-phase only. */
@Composable
fun Modifier.bob(amplitudeDp: Float = 4f): Modifier {
    val clock = rememberAmbientClock()
    return this.graphicsLayer { translationY = sin(clock.value * 1.8f) * amplitudeDp * density }
}
