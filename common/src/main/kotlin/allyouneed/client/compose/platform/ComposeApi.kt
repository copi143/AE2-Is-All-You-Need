package allyouneed.client.compose.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

/**
 * Collects floating tooltip renderers registered by composables. Registered callbacks run after the
 * Compose tree has been drawn (so they paint on top of everything) and render straight onto the
 * active [GuiGraphics] via [McGraphics].
 */
class TooltipHost {
    private val renderers = mutableListOf<() -> Unit>()

    fun register(renderer: () -> Unit): () -> Unit {
        renderers += renderer
        return { renderers.remove(renderer) }
    }

    fun render(graphics: GuiGraphics) {
        for (renderer in renderers.toList()) renderer()
    }
}

val LocalTooltipHost = compositionLocalOf<TooltipHost> { error("No TooltipHost provided") }

/** Whole-UI zoom factor; provided state-backed so composables recompose and re-align when it changes. */
val LocalUiScale = compositionLocalOf { 1f }

/**
 * Collects per-frame callbacks registered by composables. [advance] runs at the start of every
 * [ComposeOwner.render] pass (before the snapshot apply / measure / draw of the same frame), which
 * is how smooth-scroll states step with the game frame rate without any per-screen render override.
 */
class FrameCallbackHost {
    private val callbacks = mutableListOf<() -> Unit>()

    fun register(callback: () -> Unit): () -> Unit {
        callbacks += callback
        return { callbacks.remove(callback) }
    }

    fun advance() {
        if (callbacks.isEmpty()) return
        for (callback in callbacks.toList()) callback()
    }
}

val LocalFrameCallbacks = compositionLocalOf<FrameCallbackHost> { error("No FrameCallbacks provided") }

/**
 * Logical Compose-space position of the mouse for the current frame, in the **global** logical
 * space of the root of the currently-rendering layer (i.e. the screen window's local space:
 * unscaled by [LocalUiScale] and including the layer origin). Updated by [ComposeOwner] on every
 * mouse move / frame before the tree draws, so it can be read safely from pointer handlers and draw
 * scopes without racing a separate coordinate source.
 */
class MousePosition(var position: IntOffset) {
    /** Translates the logical root-space position into [density] space (multiplies by [Density.density]). */
    fun inDensity(density: Density): IntOffset = IntOffset(
        (position.x * density.density).roundToInt(),
        (position.y * density.density).roundToInt(),
    )
}

val LocalMousePosition = compositionLocalOf<MousePosition> { error("No MousePosition provided") }

/** Reads the [FrameCallbackHost] of the enclosing layer (registering a per-frame callback). */
@Composable
fun rememberFrameCallback(callback: () -> Unit) {
    val host = LocalFrameCallbacks.current
    androidx.compose.runtime.DisposableEffect(host) {
        val unregister = host.register(callback)
        onDispose { unregister() }
    }
}
