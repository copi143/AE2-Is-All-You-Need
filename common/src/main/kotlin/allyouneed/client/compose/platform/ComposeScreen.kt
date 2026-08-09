package allyouneed.client.compose.platform

import androidx.compose.runtime.Composable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * A Minecraft [Screen] that renders a full-window [ComposeLayer]. Subclass and implement [Content]
 * to build your UI with the framework's material components (McText, McPanel, ItemSlot...); the
 * layer drives the official androidx.compose.ui layout / input engine, the recomposer frame clock,
 * the floating tooltip host and Ctrl+wheel whole-UI zoom.
 *
 * Any Minecraft mod can embed Compose this way — and for panels inside existing non-Compose screens
 * use [ComposeLayer] directly.
 */
abstract class ComposeScreen(title: Component) : Screen(title) {

    protected val layer = ComposeLayer()

    @Composable
    abstract fun Content()

    override fun init() {
        super.init()
        // init() also runs on window resize; ComposeLayer.setContent ignores repeat calls.
        layer.setContent { Content() }
    }

    /** Current whole-UI zoom factor; reading it inside a composable subscribes to changes. */
    @Composable
    protected fun currentUiScale(): Float = layer.uiScale

    /** Current whole-UI zoom factor for use outside composition (e.g. event handlers). */
    protected fun uiScaleFactor(): Float = layer.uiScale

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        // Force the tree to re-measure against the new window size so layout follows GUI scale /
        // window changes (the root constraints are also refreshed every render pass).
        layer.onScreenResize()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, float: Float) {
        layer.render(graphics, mouseX, mouseY, float, layer.fullScreenRect(width, height))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (layer.onMouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (hasControlDown()) {
            // Ctrl+wheel zooms the whole Compose UI (0.5x..4x).
            layer.setUiScaleFactor(layer.uiScale + (delta * UI_SCALE_STEP).toFloat())
            return true
        }
        if (layer.onMouseScrolled(mouseX, mouseY, delta)) return true
        return super.mouseScrolled(mouseX, mouseY, delta)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (layer.onMouseReleased(mouseX, mouseY, button)) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun onClose() {
        layer.dispose()
        super.onClose()
    }

    private companion object {
        const val UI_SCALE_STEP = 0.1f
    }
}
