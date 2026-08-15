package minecraftx.compose.demo

import minecraftx.compose.material.ItemSlot
import minecraftx.compose.material.McCloseButton
import minecraftx.compose.foundation.McLine
import minecraftx.compose.material.McPanel
import minecraftx.compose.material.McScrollbar
import minecraftx.compose.material.McText
import minecraftx.compose.foundation.McVirtualColumn
import allyouneed.client.compose.platform.ComposeLayer
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * 嵌入式 Compose 演示:一个**普通 vanilla Screen** 在右下角内嵌一块 [ComposeLayer] 面板,
 * 证明框架组件可以原样嵌入任何现有 Minecraft 屏幕,与 vanilla 渲染/输入共存。
 *
 * 面板位置以逻辑坐标给出(px / uiScale),因此与全屏 Compose 屏共用同一套坐标契约;
 * 鼠标回调以 raw px 透传给 layer,由 layer 内部换算。滚轮/点击/拖拽滚动条都由面板内的
 * Compose 节点处理,未消费的输入照常回落到 vanilla `super`。
 */
class EmbeddedComposeDemoScreen : Screen(Component.literal("Embedded Compose Demo")) {

    private val panel = ComposeLayer()

    init {
        panel.setContent { PanelContent() }
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        panel.onScreenResize()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val scale = panel.uiScale
        val left = (width - PANEL_W - MARGIN) / scale
        val top = (height - PANEL_H - MARGIN) / scale
        panel.render(graphics, mouseX, mouseY, partialTick, Rect(left, top, left + PANEL_W, top + PANEL_H))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (panel.onMouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (panel.onMouseReleased(mouseX, mouseY, button)) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (panel.onMouseScrolled(mouseX, mouseY, delta)) return true
        return super.mouseScrolled(mouseX, mouseY, delta)
    }

    override fun onClose() {
        panel.dispose()
        super.onClose()
    }

    override fun isPauseScreen() = false

    @Composable
    private fun PanelContent() {
        val state = rememberScrollState().also { it.maxScroll = 60f }
        McPanel(width = PANEL_W.dp, height = PANEL_H.dp) {
            ItemSlot(
                stack = ItemStack(Items.GOLD_INGOT),
                modifier = Modifier.offset(PADDING.dp, PADDING.dp),
            )
            McText(
                text = Component.literal("嵌入式 Compose 面板"),
                modifier = Modifier.offset((PADDING + 20).dp, (PADDING + 2).dp),
                maxWidth = PANEL_W - PADDING - 20 - 14,
            )
            McCloseButton(
                onClose = { Minecraft.getInstance().setScreen(null) },
                modifier = Modifier.offset((PANEL_W - PADDING - 14).dp, PADDING.dp),
            )
            McVirtualColumn(
                lines = lines,
                state = state,
                modifier = Modifier.offset(PADDING.dp, (PADDING + 22).dp).size((PANEL_W - PADDING * 2 - 6).dp, (PANEL_H - PADDING - 22 - PADDING).dp),
                viewportWidth = PANEL_W - PADDING * 2 - 6,
                viewportHeight = PANEL_H - PADDING - 22 - PADDING,
                lineHeight = 10,
            )
            McScrollbar(
                state = state,
                modifier = Modifier
                    .offset((PANEL_W - PADDING - 4).dp, (PADDING + 22).dp)
                    .size(4.dp, (PANEL_H - PADDING - 22 - PADDING).dp),
            )
        }
    }

    private companion object {
        const val PANEL_W = 220
        const val PANEL_H = 150
        const val PADDING = 4
        const val MARGIN = 10

        val lines = buildList {
            for (i in 0 until 12) {
                add(McLine(Component.literal("嵌入式行 %d - 由 ComposeLayer 渲染".format(i)), 4, i * 10))
            }
        }
    }
}
