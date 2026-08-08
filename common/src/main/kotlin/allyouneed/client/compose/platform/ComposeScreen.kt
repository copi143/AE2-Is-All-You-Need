package allyouneed.client.compose.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

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
 * Logical Compose-space position of the mouse for the current frame, in the coordinate space of the
 * root of the currently-rendering [ComposeScreen] (i.e. the screen window's local space: unscaled by
 * [LocalUiScale]). Updated by [ComposeOwner] on every mouse move / frame before the tree draws, so it
 * can be read safely from pointer handlers without racing a separate coordinate source.
 */
class MousePosition(var position: IntOffset) {
    /** Translates the logical root-space position into [density] space (multiplies by [Density.density]). */
    fun inDensity(density: Density): IntOffset = IntOffset(
        (position.x * density.density).roundToInt(),
        (position.y * density.density).roundToInt(),
    )
}

val LocalMousePosition = compositionLocalOf<MousePosition> { error("No MousePosition provided") }

abstract class ComposeScreen(title: Component) : Screen(title) {
    private val owner by lazy { ComposeOwner(this) }

    @Composable
    abstract fun Content()

    override fun init() {
        super.init()
        owner.setContent { Content() }
    }

    /** Current whole-UI zoom factor; reading it inside a composable subscribes to changes. */
    @Composable
    protected fun currentUiScale(): Float = owner.uiScale

    /** Current whole-UI zoom factor for use outside composition (e.g. event handlers). */
    protected fun uiScaleFactor(): Float = owner.uiScale

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        // Screen.resize() re-runs init() which setContent() ignores; force the tree to re-measure
        // against the new window size so the layout follows GUI scale / window changes.
        owner.onScreenResize()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, float: Float) {
        owner.render(graphics, mouseX, mouseY, float)
        McGraphics.current = graphics
        try {
            owner.tooltipHost.render(graphics)
        } finally {
            McGraphics.current = null
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (owner.onMouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (hasControlDown()) {
            // Ctrl+wheel zooms the whole Compose UI (0.5x..4x).
            owner.setUiScaleFactor(owner.uiScale + (delta * UI_SCALE_STEP).toFloat())
            return true
        }
        if (owner.onMouseScrolled(mouseX, mouseY, delta)) return true
        return super.mouseScrolled(mouseX, mouseY, delta)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (owner.onMouseReleased(mouseX, mouseY, button)) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun onClose() {
        owner.dispose()
        super.onClose()
    }

    private companion object {
        const val UI_SCALE_STEP = 0.1f
    }
}
