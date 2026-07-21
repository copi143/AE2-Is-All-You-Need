package allyouneed.compose.ui.draw

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack

class McDrawScope(
    val graphics: GuiGraphics,
) {
    private val font = Minecraft.getInstance().font

    var currentWidth: Int = 0
    var currentHeight: Int = 0

    fun fillRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        graphics.fill(x, y, x + width, y + height, color)
    }

    fun drawBehind(color: Int) {
        fillRect(0, 0, currentWidth, currentHeight, color)
    }

    fun drawText(text: String, x: Int, y: Int, color: Int) {
        graphics.drawString(font, text, x, y, color)
    }

    fun drawItem(item: ItemStack, x: Int, y: Int) {
        graphics.renderFakeItem(item, x, y)
    }

    fun textWidth(text: String): Int = font.width(text)

    fun pushPose() = graphics.pose().pushPose()
    fun popPose() = graphics.pose().popPose()

    fun translate(x: Int, y: Int) {
        graphics.pose().translate(x.toFloat(), y.toFloat(), 0f)
    }
}
