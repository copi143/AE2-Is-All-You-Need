package minecraftx.compose.itemdetail

import allyouneed.client.compose.platform.ComposeContainerScreen
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import minecraftx.compose.foundation.McLine
import minecraftx.compose.foundation.McVirtualColumn
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.material.McCloseButton
import minecraftx.compose.material.McPanel
import minecraftx.compose.material.McScrollbar
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import kotlin.math.min

/**
 * Compose 渲染的 item-details 屏幕,用作无 EMI/JEI 时的默认渲染器。
 *
 * 本屏已完全建立在框架组件之上,自身只保留领域逻辑:从 [ItemDetails] 构建行模型([buildRows]),
 * 并把行交给 [McVirtualColumn](虚拟化可见行 + scissor 像素裁切)、滚动交给 [McScrollbar] 与
 * 平滑 [rememberScrollState](由框架的 FrameCallbackHost 每帧自动推进,无需 render 钩子);
 * 面板 chrome 用 [McPanel]/[McCloseButton]。滚轮在内容区滚动由 [McVirtualColumn] 内置的
 * scroll 处理器按官方命中测试路由,不再需要 `mouseScrolled` 几何判定。
 *
 * 几何统一以"逻辑画布坐标"表达:ComposeOwner 把根约束设为屏幕/缩放,再用 pose.scale 画到屏幕,
 * 因此面板用 `Alignment.Center` 居中即可在任何缩放系数下保持屏幕居中。只有 header 的物品堆叠
 * 预览需要 MC 方块渲染管线,由 [ItemSlot] 的内部渲染器(EMI/vanilla)旁路绘制。
 */
class ComposeItemDetailsScreen(
    private val details: ItemDetails,
) : ComposeContainerScreen<ComposeContainerScreen.EmptyMenu>(
    ComposeContainerScreen.EmptyMenu(),
    ComposeContainerScreen.playerInventory(),
    Component.literal("Block Details"),
) {

    private fun buildRows(valueColor: Int): List<McLine> {
        val out = ArrayList<McLine>()
        var y = 0
        for (section in details.sections) {
            out += McLine(section.title, 4, y, valueColor)
            y += ItemDetailsLayout.LINE_HEIGHT
            for (entry in section.lines) {
                out += McLine(entry, 8, y, valueColor)
                y += ItemDetailsLayout.LINE_HEIGHT
            }
            y += ItemDetailsLayout.SECTION_GAP
        }
        return out
    }

    @Composable
    override fun Content() {
        val colors = McTheme.colors
        val rows = remember(colors) { buildRows(colors.textPrimary.value.toInt()) }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 面板在可用逻辑尺寸内收缩,小屏/缩放窗口时不再被切掉底部(见
            // McPanel 内容区内嵌的 McVirtualColumn:内容高度随之变化,自动重算 maxScroll)。
            BoxWithConstraints {
                val panelW = min(ItemDetailsLayout.WIDTH, constraints.maxWidth)
                val panelH = min(ItemDetailsLayout.HEIGHT, constraints.maxHeight)
                val contentW = (panelW - ItemDetailsLayout.PADDING * 2).coerceAtLeast(0)
                val contentH = (panelH - ItemDetailsLayout.CONTENT_TOP - ItemDetailsLayout.PADDING).coerceAtLeast(0)
                val state = rememberScrollState().also {
                    val lastY = rows.lastOrNull()?.y ?: 0
                    it.maxScroll = (lastY + ItemDetailsLayout.LINE_HEIGHT - contentH).coerceAtLeast(0).toFloat()
                    if (it.display > it.maxScroll) it.seek(it.maxScroll)
                }

                McPanel(width = panelW.dp, height = panelH.dp) {
                    // Header:物品 slot(不可交互,EMI/vanilla 渲染),标题,关闭按钮
                    ItemSlot(
                        stack = details.stack,
                        modifier = Modifier.offset(ItemDetailsLayout.PADDING.dp, ItemDetailsLayout.PADDING.dp),
                    )
                    McText(
                        text = details.title,
                        modifier = Modifier.offset(ItemDetailsLayout.TITLE_X.dp, (ItemDetailsLayout.PADDING + 4).dp),
                        maxWidth = (contentW - ItemDetailsLayout.TITLE_X).coerceAtLeast(0),
                    )
                    McCloseButton(
                        onClose = { onClose() },
                        modifier = Modifier.offset(
                            (panelW - ItemDetailsLayout.PADDING - 14).dp,
                            ItemDetailsLayout.PADDING.dp,
                        ),
                    )

                    // 内容框:背景 + 虚拟化可滚动文本列 + 滚动条
                    Box(
                        modifier = Modifier
                            .offset(ItemDetailsLayout.PADDING.dp, ItemDetailsLayout.CONTENT_TOP.dp)
                            .size(contentW.dp, contentH.dp),
                    ) {
                        Box(Modifier.matchParentSize().background(colors.contentBackground))
                        Box(Modifier.matchParentSize().drawBehind { drawRect(color = colors.contentBorder, style = Stroke(1f)) })
                        McVirtualColumn(
                            lines = rows,
                            state = state,
                            modifier = Modifier.matchParentSize(),
                            viewportWidth = contentW,
                            viewportHeight = contentH,
                            lineHeight = ItemDetailsLayout.LINE_HEIGHT,
                        )
                        McScrollbar(
                            state = state,
                            modifier = Modifier
                                .offset((contentW - 4).dp, 0.dp)
                                .size(4.dp, contentH.dp),
                        )
                    }
                }
            }
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun onClose() {
        super.onClose()
        val previous = ItemDetailsScreen.consumeReturnScreen() ?: return
        if (previous === this) return
        if (previous is AbstractContainerScreen<*> && previous !is ComposeContainerScreen<*>) {
            val player = Minecraft.getInstance().player ?: return
            if (previous.getMenu() !== player.containerMenu) return
        }
        Minecraft.getInstance().setScreen(previous)
    }

    override fun isPauseScreen() = false
}
