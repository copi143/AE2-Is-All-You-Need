package allyouneed.client.compose.platform

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

abstract class ComposeScreen(title: Component) : Screen(title) {
    private val owner by lazy { ComposeOwner(this) }

    @Composable
    abstract fun Content()

    override fun init() {
        super.init()
        owner.setContent { Content() }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, float: Float) {
        owner.render(graphics, mouseX, mouseY, float)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (owner.onMouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
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
}
