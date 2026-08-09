package allyouneed.client.compose.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp

/**
 * Smooth-scroll state for a vertical region, shared by wheel input, scrollbar drag / track clicks
 * and the virtualized text column.
 *
 * Scrolling has two values like the classic "target + animated display" split:
 *
 *  - [target] is the destination, written by [scrollBy] (wheel) and [seek] (scrollbar).
 *  - [display] is what layout reads; it converges to [target] exponentially in [advance], which is
 *    driven once per rendered frame by the framework's [FrameCallbackHost] — no per-screen render
 *    override, no coroutine / recomposition lag.
 *
 * Changing [target] mid-animation never restarts it (the step just re-targets), so fast wheel input
 * stays smooth; [seek] writes [display] directly for an immediate 1:1 drag. When already at rest
 * [advance] writes no state, so an idle screen costs nothing.
 */
class ScrollState(
    var maxScroll: Float = 0f,
    val smoothingTime: Float = SCROLL_SMOOTHING_TIME,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    var target by mutableFloatStateOf(0f)
        private set

    var display by mutableFloatStateOf(0f)
        private set

    private var lastFrameNanos = -1L

    val isAnimating: Boolean get() = abs(target - display) > 0.01f

    /** Wheel / button scroll: moves [target] by [delta] logical pixels, clamped to the range. */
    fun scrollBy(delta: Float) {
        if (maxScroll <= 0f) return
        target = (target + delta).coerceIn(0f, maxScroll)
    }

    /** Immediate scrub (scrollbar drag / track click): writes [display] and [target] at once. */
    fun seek(position: Float) {
        val value = position.coerceIn(0f, maxScroll)
        display = value
        target = value
    }

    /**
     * Steps [display] toward [target] by the frame delta. Called by the framework once per rendered
     * frame via [FrameCallbackHost]; callers should never invoke it manually.
     */
    fun advance() {
        val now = nanoTime()
        if (lastFrameNanos >= 0L) {
            val dt = (now - lastFrameNanos) / 1_000_000_000f
            val diff = target - display
            if (abs(diff) > 0.01f) {
                val factor = 1f - exp(-dt / smoothingTime)
                display += diff * factor
                if (abs(display - target) < 0.5f) display = target
            }
        }
        lastFrameNanos = now
    }

    companion object {
        /** Exponential-smoothing time constant for wheel scroll (seconds). Lower = snappier. */
        const val SCROLL_SMOOTHING_TIME = 0.06f

        /** Wheel notch -> scroll pixels (about 2 lines per notch). */
        const val WHEEL_STEP = 20f
    }
}

/**
 * Creates a [ScrollState] and registers its [ScrollState.advance] with the enclosing layer's
 * [FrameCallbackHost], so the wheel-smoothing steps automatically once per game frame for as long
 * as this composable is composed.
 */
@Composable
fun rememberScrollState(maxScroll: Float = 0f): ScrollState {
    val state = remember { ScrollState(maxScroll) }
    rememberFrameCallback(state::advance)
    return state
}
