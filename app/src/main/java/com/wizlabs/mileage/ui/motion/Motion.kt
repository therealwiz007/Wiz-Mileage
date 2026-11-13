package com.wizlabs.mileage.ui.motion

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Motion toolkit. Everything here is built to stay light on the phone:
 * - one-shot animations run on the draw/layer phase (graphicsLayer), never per-frame recomposition;
 * - the only continuous clock is [rememberAmbientClock], capped at 24 fps and stopped whenever the app
 *   isn't resumed and focused, Battery Saver is on, or the user has turned animations off.
 */

/** False under Battery Saver or when system animations are off; then everything shows its final frame. */
val LocalMotion = compositionLocalOf { true }

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(motionAllowed(context)) }
    DisposableEffect(context, owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) enabled = motionAllowed(context) }
        owner.lifecycle.addObserver(obs)
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { enabled = motionAllowed(context) }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, rx, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { owner.lifecycle.removeObserver(obs); runCatching { context.unregisterReceiver(rx) } }
    }
    return enabled
}

private fun motionAllowed(c: Context): Boolean {
    val pm = c.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isPowerSaveMode != true && ValueAnimator.areAnimatorsEnabled()
}

/**
 * ONE shared ambient clock for the whole app (provided by [ProvideAmbientClock] in MainActivity):
 * - ticks only while something on screen subscribes, at ~24 fps, vsync-aligned;
 * - stops when the activity isn't resumed and focused, Battery Saver is on, or animations are off;
 * - settles on a still frame [AmbientIdle.SETTLE_NS] after the last touch; a touch wakes it.
 */
@Stable
class AmbientClock {
    internal val time = mutableFloatStateOf(0f)
    internal var subs by mutableIntStateOf(0)
}

val LocalAmbientClock = staticCompositionLocalOf<AmbientClock?> { null }

/** Touch counter fed by MainActivity.dispatchTouchEvent (throttled to one bump per second). */
object AmbientIdle {
    const val SETTLE_NS = 9_000_000_000L
    var touches by mutableIntStateOf(0)
        private set
    private var lastBump = 0L
    fun onTouch() {
        val now = System.nanoTime()
        if (now - lastBump > 1_000_000_000L) { lastBump = now; touches++ }
    }
}

@Composable
fun ProvideAmbientClock(content: @Composable () -> Unit) {
    val clock = remember { AmbientClock() }
    val motion = LocalMotion.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val active = motion && focused && resumed
    LaunchedEffect(clock, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { (clock.subs > 0) to AmbientIdle.touches }.collectLatest { (any, _) ->
            if (!any) return@collectLatest
            val start = System.nanoTime()
            var last = -1L
            while (System.nanoTime() - start < AmbientIdle.SETTLE_NS) {
                androidx.compose.runtime.withFrameNanos { now ->
                    // Advance by real elapsed time (clamped) so a resumed clock continues smoothly.
                    if (last >= 0) clock.time.floatValue += ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = now
                }
                delay(26) // + vsync wait ≈ 24 fps
            }
        }
    }
    CompositionLocalProvider(LocalAmbientClock provides clock, content = content)
}

/**
 * Seconds on the shared ambient clock. Read `.value` **inside draw or graphicsLayer lambdas only**, so a tick
 * re-draws one small node instead of recomposing anything. [fps] is kept for source compatibility (always ~24).
 */
@Composable
fun rememberAmbientClock(@Suppress("UNUSED_PARAMETER") fps: Int = 24): State<Float> {
    val clock = LocalAmbientClock.current ?: return remember { mutableFloatStateOf(0f) }
    DisposableEffect(clock) {
        clock.subs++
        onDispose { clock.subs-- }
    }
    return clock.time
}

/**
 * Text whose characters roll vertically when they change (odometer style). Characters are matched
 * from the right, so units stay units when the length changes. Only changed characters animate.
 */
@Composable
fun RollingNumber(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, up: Boolean = true) {
    if (!LocalMotion.current) { Text(text, modifier = modifier, style = style, color = color, maxLines = 1); return }
    Row(modifier) {
        val n = text.length
        for (i in 0 until n) {
            key(n - i) {
                AnimatedContent(
                    text[i],
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { dir * it / 2 } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(tween(160)) { -dir * it / 2 } + fadeOut(tween(120))) using SizeTransform(clip = false)
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color, maxLines = 1) }
            }
        }
    }
}

/** Springy press feedback: shrinks while held and bounces back on release. Reads the scale in the layer phase. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val s = animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = if (isPressed) 1f else 0.42f, stiffness = if (isPressed) Spring.StiffnessHigh else 520f),
        label = "press",
    )
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** Remembers which items already played their entrance, so scrolling back doesn't replay it. */
class EntranceState { val played = HashSet<Any>() }

@Composable
fun rememberEntrance(vararg keys: Any?): EntranceState = remember(*keys) { EntranceState() }

/**
 * Staggered entrance: rise + fade with a soft spring, delayed by [index]. Layer-phase only, so it never
 * re-measures. Plays once per [state] and [key].
 */
fun Modifier.enter(state: EntranceState?, key: Any, index: Int): Modifier = composed {
    val motion = LocalMotion.current
    val already = state?.played?.contains(key) == true
    val p = remember(state, key) { Animatable(if (!motion || already) 1f else 0f) }
    val born = remember(state, key) { System.nanoTime() }
    LaunchedEffect(state, key) {
        if (p.value < 1f) {
            // If the phone is busy (cold start) and frames arrive late, show content at once:
            // never leave the screen blank just to play an entrance.
            androidx.compose.runtime.withFrameNanos { }
            if (System.nanoTime() - born > 450_000_000L) p.snapTo(1f)
            delay(index.coerceIn(0, 9) * 45L)
            p.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow))
        }
        state?.played?.add(key)
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 34.dp.toPx()
        val s = 0.96f + 0.04f * v
        scaleX = s; scaleY = s
    }
}

/** A light "tick" for primary actions; CONFIRM on Android 11+. */
@Composable
fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
    }
}

/** A LazyColumn item that plays a staggered entrance once. */
fun androidx.compose.foundation.lazy.LazyListScope.enterItem(
    state: EntranceState,
    key: String,
    index: Int,
    content: @Composable () -> Unit,
) = item(key = key) {
    // animateItem: rows glide to new places and fade in/out when fill-ups are added, edited or removed.
    androidx.compose.foundation.layout.Box(
        Modifier
            .animateItem(fadeInSpec = tween(180), placementSpec = spring(dampingRatio = 1f, stiffness = 400f, visibilityThreshold = IntOffset.VisibilityThreshold), fadeOutSpec = tween(120))
            .enter(state, key, index),
    ) { content() }
}
