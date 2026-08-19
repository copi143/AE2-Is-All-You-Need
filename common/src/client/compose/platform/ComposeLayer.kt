package allyouneed.client.compose.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

/**
 * An embeddable Compose rendering layer for Minecraft GUIs, decoupled from any
 * [net.minecraft.client.gui.screens.Screen]. This is the framework's primary embedding surface:
 *
 *  - **Full-screen UI** — a [ComposeScreen] subclass is just a thin `Screen` adapter over a layer
 *    driven with [fullScreenRect].
 *  - **Embedded sub-region** — any existing screen (container GUI, menu, HUD...) can host a layer
 *    and render a Compose subtree inside a sub-rectangle of its own frame, mixing vanilla widgets
 *    and Compose content freely.
 *
 * Usage (embedded): create one `ComposeLayer`, call [setContent] once, then forward the host
 * screen's render and mouse callbacks to this layer with the target rect:
 *
 * ```
 * class MyScreen : Screen(...) {
 *     private val panel = ComposeLayer().apply { setContent { McPanel(240.dp, 160.dp) { ... } } }
 *     override fun render(g, mx, my, pt) {
 *         super.render(g, mx, my, pt)
 *         val scale = panel.uiScale
 *         panel.render(g, mx, my, pt, Rect(...))   // rect in logical space
 *     }
 *     override fun mouseClicked(mx, my, button) =
 *         panel.onMouseClicked(mx.toDouble(), my.toDouble(), button) || super.mouseClicked(mx, my, button)
 *     override fun onClose() { panel.dispose(); super.onClose() }
 * }
 * ```
 *
 * Coordinate contract: the rect and [origin] are in **logical** units (GUI pixels divided by
 * [uiScale]); mouse callbacks accept raw GUI pixel coordinates and are converted internally. Inside
 * the layer, pointer events are in layer-local logical space, while [mousePosition] /
 * `positionInWindow()` are global logical (logical window space) so hover hit-testing stays
 * consistent regardless of the embed rect.
 */
class ComposeLayer {

    private var logicalSize = IntSize(1, 1)
    private val owner by lazy { ComposeOwner { logicalSize } }

    /** Whole-UI zoom factor applied around every render pass. */
    val uiScale: Float get() = owner.uiScale

    /** Logical-space origin of this layer inside the window (set from the last [render] rect). */
    val origin: Offset get() = owner.uiOrigin

    /** Floating tooltip host; tooltips registered by composables draw on top of the tree. */
    val tooltipHost: TooltipHost get() = owner.tooltipHost

    /** Current global-logical mouse position, updated every render / input event. */
    val mousePosition: MousePosition get() = owner.mousePosition

    fun setUiScaleFactor(scale: Float) = owner.setUiScaleFactor(scale)

    /** Attaches [content] as the layer's root; safe to call only once per layer. */
    fun setContent(content: @Composable () -> Unit) = owner.setContent(content)

    /** Forces a re-measure after the host window / layout size changed. */
    fun onScreenResize() = owner.onScreenResize()

    /** Convenience full-window rect (logical units) for hosts that occupy the whole screen. */
    fun fullScreenRect(width: Int, height: Int): Rect =
        Rect(0f, 0f, width / uiScale, height / uiScale)

    /**
     * Renders the layer inside [rect] (logical units, usually in logical window space). [mouseX] /
     * [mouseY] are raw GUI pixels. Call from the host screen's `render`. Floating tooltips
     * registered by composables are drawn on top of the tree afterwards.
     */
    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, rect: Rect) {
        logicalSize = IntSize(
            rect.width.roundToInt().coerceAtLeast(0),
            rect.height.roundToInt().coerceAtLeast(0),
        )
        owner.uiOrigin = Offset(rect.left, rect.top)
        owner.render(graphics, mouseX, mouseY, partialTick)
        McGraphics.current = graphics
        try {
            owner.tooltipHost.render(graphics)
        } finally {
            McGraphics.current = null
        }
    }

    fun containsRaw(mouseX: Double, mouseY: Double): Boolean {
        val local = toLocal(mouseX, mouseY)
        return local.x >= 0f && local.y >= 0f &&
            local.x < logicalSize.width && local.y < logicalSize.height
    }

    /** Forwards a raw-pixel click; returns true when a Compose node consumed it. */
    fun onMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val local = toLocal(mouseX, mouseY)
        return owner.onMouseClicked(local.x, local.y, button)
    }

    /** Forwards a raw-pixel release; returns true when a Compose node consumed it. */
    fun onMouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val local = toLocal(mouseX, mouseY)
        return owner.onMouseReleased(local.x, local.y, button)
    }

    /** Forwards a raw-pixel wheel event (routes to the scrollable node under the cursor). */
    fun onMouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val local = toLocal(mouseX, mouseY)
        return owner.onMouseScrolled(local.x, local.y, delta)
    }

    /** Forwards a key-press (GLFW keyCode/scanCode/modifiers); true when a text field consumed it. */
    fun onKeyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        owner.onKeyPressed(keyCode, scanCode, modifiers)

    /** Forwards a key-release (GLFW keyCode/scanCode/modifiers). */
    fun onKeyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        owner.onKeyReleased(keyCode, scanCode, modifiers)

    /** Forwards a committed character (direct key or IME); true when a text field consumed it. */
    fun onCharTyped(codePoint: Int, modifiers: Int): Boolean =
        owner.onCharTyped(codePoint, modifiers)

    private fun toLocal(x: Double, y: Double): Offset {
        val scale = uiScale
        return Offset((x / scale - origin.x).toFloat(), (y / scale - origin.y).toFloat())
    }

    /** Releases the composition and coroutine scopes; call from the host's `onClose`. */
    fun dispose() = owner.dispose()
}
