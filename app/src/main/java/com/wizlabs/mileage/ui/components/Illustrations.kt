package com.wizlabs.mileage.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.ui.motion.rememberAmbientClock
import com.wizlabs.mileage.ui.theme.Wiz
import kotlin.math.sin

enum class EmptyArt { PUMP, CHART, RECEIPT }

/**
 * Small, original empty-state art drawn in code: a fuel pump with a hovering drop, a rising bar chart, or a
 * receipt. Shapes are cached; only a translation/scale is animated (24 fps ambient clock, layer phase).
 */
@Composable
fun EmptyIllustration(art: EmptyArt, modifier: Modifier = Modifier) {
    val clock = rememberAmbientClock()
    val accent = Wiz.c.accent
    val accent2 = Wiz.c.accent2
    val surface = Wiz.c.surface2
    val line = Wiz.c.line
    val ink = Wiz.c.muted
    Box(modifier) {
        // Halo
        Spacer(
            Modifier.fillMaxSize().graphicsLayer {
                val s = 0.94f + 0.06f * sin(clock.value * 1.4f); scaleX = s; scaleY = s
            }.drawWithCache {
                val b = Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))
                onDrawBehind { drawCircle(b) }
            },
        )
        // Base art (static)
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val w = size.width; val h = size.height
                val st = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    when (art) {
                        EmptyArt.PUMP -> {
                            val bw = w * 0.34f; val bh = h * 0.46f; val left = w * 0.26f; val top = h * 0.34f
                            drawRoundRect(surface, Offset(left, top), Size(bw, bh), CornerRadius(10.dp.toPx()))
                            drawRoundRect(line, Offset(left, top), Size(bw, bh), CornerRadius(10.dp.toPx()), style = st)
                            drawRoundRect(accent, Offset(left + bw * 0.18f, top + bh * 0.14f), Size(bw * 0.64f, bh * 0.26f), CornerRadius(5.dp.toPx()))
                            drawRoundRect(line, Offset(left - bw * 0.1f, top + bh - 2), Size(bw * 1.2f, 7.dp.toPx()), CornerRadius(4.dp.toPx()))
                            // Hose
                            val hose = Path().apply {
                                moveTo(left + bw, top + bh * 0.3f)
                                cubicTo(left + bw * 1.5f, top + bh * 0.3f, left + bw * 1.5f, top + bh * 0.9f, left + bw * 1.28f, top + bh * 0.95f)
                            }
                            drawPath(hose, ink, style = st)
                        }
                        EmptyArt.CHART -> {
                            val base = h * 0.78f
                            drawLine(line, Offset(w * 0.18f, base), Offset(w * 0.82f, base), 3.dp.toPx(), StrokeCap.Round)
                            val hs = floatArrayOf(0.22f, 0.36f, 0.28f, 0.48f)
                            for (i in hs.indices) {
                                val bx = w * (0.24f + i * 0.14f)
                                drawRoundRect(if (i == 3) accent else surface, Offset(bx, base - h * hs[i]), Size(w * 0.09f, h * hs[i]), CornerRadius(5.dp.toPx()))
                            }
                        }
                        EmptyArt.RECEIPT -> {
                            val left = w * 0.3f; val top = h * 0.2f; val rw = w * 0.4f; val rh = h * 0.58f
                            drawRoundRect(surface, Offset(left, top), Size(rw, rh), CornerRadius(8.dp.toPx()))
                            drawRoundRect(line, Offset(left, top), Size(rw, rh), CornerRadius(8.dp.toPx()), style = st)
                            for (i in 0..3) {
                                val y = top + rh * (0.2f + i * 0.17f)
                                drawLine(if (i == 0) accent else line, Offset(left + rw * 0.18f, y), Offset(left + rw * (if (i % 2 == 0) 0.82f else 0.6f), y), 3.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                }
            },
        )
        // Floating accent: a drop for the pump, a sparkle for the chart, a coin for the receipt.
        Spacer(
            Modifier.fillMaxSize().graphicsLayer {
                translationY = sin(clock.value * 1.8f) * 4.dp.toPx()
            }.drawWithCache {
                val w = size.width; val h = size.height
                val drop = Path().apply {
                    val cx = w * 0.74f; val r = w * 0.075f; val cy = h * 0.3f
                    moveTo(cx, cy - r * 2.1f)
                    cubicTo(cx + r * 0.3f, cy - r * 1.2f, cx + r, cy - r * 0.6f, cx + r, cy)
                    arcTo(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r), 0f, 180f, false)
                    cubicTo(cx - r, cy - r * 0.6f, cx - r * 0.3f, cy - r * 1.2f, cx, cy - r * 2.1f)
                    close()
                }
                val g = Brush.verticalGradient(listOf(accent, accent2))
                onDrawBehind {
                    when (art) {
                        EmptyArt.PUMP -> drawPath(drop, g)
                        EmptyArt.CHART -> {
                            val c = Offset(w * 0.74f, h * 0.24f); val r = w * 0.07f
                            drawLine(accent, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 3.dp.toPx(), StrokeCap.Round)
                            drawLine(accent, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 3.dp.toPx(), StrokeCap.Round)
                        }
                        EmptyArt.RECEIPT -> {
                            val c = Offset(w * 0.72f, h * 0.24f)
                            drawCircle(g, w * 0.08f, c)
                            drawCircle(Color.White, w * 0.03f, c, alpha = 0.5f)
                        }
                    }
                }
            },
        )
    }
}
