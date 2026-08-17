package ae2x.compose

import allyouneed.client.compose.platform.ComposeLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import appeng.client.gui.AEBaseScreen
import appeng.client.gui.style.ScreenStyle
import appeng.menu.AEBaseMenu
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.Rect2i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import kotlin.math.roundToInt

abstract class AeComposeScreen<M : AEBaseMenu>(
    menu: M,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle = AeComposeStyles.blank(),
) : AEBaseScreen<M>(menu, playerInventory, title, style), AeComposeHost {

    protected val layer = ComposeLayer()
    private val extraExclusion = mutableListOf<Rect2i>()

    @Composable
    abstract fun Content()

    override val uiScale: Float get() = layer.uiScale

    override fun init() {
        super.init()
        clearWidgets()
        for (slot in menu.slots) {
            slot.x = HIDDEN
            slot.y = HIDDEN
        }
        layer.setContent {
            CompositionLocalProvider(LocalAeHost provides this) {
                McTheme { Content() }
            }
        }
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        layer.onScreenResize()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        updateBeforeRender()
        widgets.updateBeforeRender()
        renderBackground(graphics)
        layer.render(graphics, mouseX, mouseY, partialTick, layer.fullScreenRect(width, height))
        hoveredSlot = findSlot(mouseX.toDouble(), mouseY.toDouble())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (layer.onMouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (layer.onMouseReleased(mouseX, mouseY, button)) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (hasControlDown()) {
            layer.setUiScaleFactor(layer.uiScale + (delta * 0.1f).toFloat())
            return true
        }
        layer.onMouseScrolled(mouseX, mouseY, delta)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (layer.onKeyPressed(keyCode, scanCode, modifiers)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (layer.onKeyReleased(keyCode, scanCode, modifiers)) return true
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (layer.onCharTyped(codePoint.code, modifiers)) return true
        return super.charTyped(codePoint, modifiers)
    }

    override fun onClose() {
        layer.dispose()
        super.onClose()
    }

    override fun hideSlot(slot: Slot) {
        slot.x = HIDDEN
        slot.y = HIDDEN
    }

    override fun bindSlot(slot: Slot, coordinates: LayoutCoordinates) {
        val pos = coordinates.positionInWindow()
        val scale = layer.uiScale
        slot.x = (pos.x * scale).roundToInt() + 1 - leftPos
        slot.y = (pos.y * scale).roundToInt() + 1 - topPos
    }

    override fun reportPanel(left: Int, top: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        imageWidth = width
        imageHeight = height
        leftPos = left
        topPos = top
    }

    override fun reportExclusion(zones: List<Rect2i>) {
        extraExclusion.clear()
        extraExclusion.addAll(zones)
    }

    override fun getExclusionZones(): List<Rect2i> = extraExclusion.toList()

    companion object {
        private const val HIDDEN = -9999
    }
}
