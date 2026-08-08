package allyouneed.client.itemdetail

import allyouneed.client.itemdetail.styling.DetailsStyling.COLOR_SECTION
import allyouneed.client.itemdetail.styling.DetailsStyling.COLOR_VALUE
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import kotlin.math.max

/**
 * Base item-details screen. It draws the fixed panel, the header (item + title),
 * the content frame and a slim scrollbar, and exposes two open hooks that the
 * renderer-specific subclasses override:
 *
 *  - [renderHeaderItem] to draw the stack preview (EMI/JEI use their own widgets),
 *  - [renderContent] to render the sections with their own widget sets.
 *
 * The default implementation is a pure vanilla renderer, which is used as the
 * fallback when neither EMI nor JEI is present.
 */
open class ItemDetailsScreen(
    protected val details: ItemDetails,
) : Screen(Component.literal("Block Details")) {

    companion object {
        private var returnScreen: Screen? = null

        /**
         * Remembers the screen that should come back when a item-details screen
         * closes. Screens opened while a details screen is already open (refresh)
         * are ignored so the original screen is preserved.
         */
        fun prepareReturn(screen: Screen?) {
            if (screen !is ItemDetailsScreen) {
                returnScreen = screen
            }
        }

        internal fun consumeReturnScreen(): Screen? {
            val s = returnScreen
            returnScreen = null
            return s
        }
    }

    protected var left = 0
    protected var top = 0

    protected val contentLeft get() = left + ItemDetailsLayout.PADDING
    protected val contentTop get() = top + ItemDetailsLayout.CONTENT_TOP
    protected val contentRight get() = contentLeft + ItemDetailsLayout.CONTENT_WIDTH
    protected val contentBottom get() = contentTop + ItemDetailsLayout.CONTENT_HEIGHT

    protected var scroll = 0
    protected var maxScroll = 0
    protected var contentHeightPx = 0

    override fun init() {
        left = (width - ItemDetailsLayout.WIDTH) / 2
        top = (height - ItemDetailsLayout.HEIGHT) / 2
        addRenderableWidget(
            Button.builder(Component.literal("✕"), { onClose() }).bounds(
                left + ItemDetailsLayout.WIDTH - ItemDetailsLayout.PADDING - 14, top + ItemDetailsLayout.PADDING, 14, 14
            ).build()
        )
        recomputeMaxScroll()
    }

    protected open fun recomputeMaxScroll() {
        contentHeightPx =
            details.sections.sumOf { (1 + it.lines.size) * ItemDetailsLayout.LINE_HEIGHT } + (details.sections.size - 1) * ItemDetailsLayout.SECTION_GAP
        maxScroll = max(0, contentHeightPx - ItemDetailsLayout.CONTENT_HEIGHT)
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        super.onClose()
        val previous = consumeReturnScreen() ?: return
        if (previous === this) return
        if (previous is AbstractContainerScreen<*>) {
            val player = Minecraft.getInstance().player ?: return
            if (previous.getMenu() !== player.containerMenu) return
        }
        Minecraft.getInstance().setScreen(previous)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics)

        guiGraphics.fill(left, top, left + ItemDetailsLayout.WIDTH, top + ItemDetailsLayout.HEIGHT, 0xC0101010.toInt())
        guiGraphics.renderOutline(left, top, ItemDetailsLayout.WIDTH, ItemDetailsLayout.HEIGHT, 0xFFFFFFFF.toInt())

        renderHeaderItem(guiGraphics, mouseX, mouseY, left + ItemDetailsLayout.PADDING, top + ItemDetailsLayout.PADDING)
        guiGraphics.drawString(
            font,
            details.title,
            left + ItemDetailsLayout.TITLE_X,
            top + ItemDetailsLayout.PADDING + 4,
            COLOR_VALUE,
            true
        )

        guiGraphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0x66000000)
        guiGraphics.renderOutline(
            contentLeft,
            contentTop,
            ItemDetailsLayout.CONTENT_WIDTH,
            ItemDetailsLayout.CONTENT_HEIGHT,
            0xFF333333.toInt()
        )

        renderContent(guiGraphics, mouseX, mouseY, partialTick)

        drawScrollbar(guiGraphics)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    /** Draws the stack preview at (x, y). Default: vanilla item render. */
    open fun renderHeaderItem(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int) {
        guiGraphics.renderItem(details.stack, x, y)
        guiGraphics.renderItemDecorations(font, details.stack, x, y)
    }

    /** Renders all sections clipped to the content frame. */
    open fun renderContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0, -scroll.toDouble(), 0.0)
        var y = contentTop
        for (section in details.sections) {
            guiGraphics.drawString(font, section.title, contentLeft + 4, y, COLOR_SECTION, false)
            y += ItemDetailsLayout.LINE_HEIGHT
            for (entry in section.lines) {
                guiGraphics.drawString(font, entry, contentLeft + 8, y, COLOR_VALUE, false)
                y += ItemDetailsLayout.LINE_HEIGHT
            }
            y += ItemDetailsLayout.SECTION_GAP
        }
        guiGraphics.pose().popPose()
        guiGraphics.disableScissor()
    }

    private fun drawScrollbar(guiGraphics: GuiGraphics) {
        if (maxScroll <= 0) return
        val trackHeight = ItemDetailsLayout.CONTENT_HEIGHT
        val barHeight = max(16, (trackHeight * trackHeight / (trackHeight + maxScroll)))
        val travel = trackHeight - barHeight
        val barY = contentTop + if (maxScroll == 0) 0 else (travel * scroll / maxScroll)
        guiGraphics.fill(
            contentLeft + ItemDetailsLayout.CONTENT_WIDTH - 4,
            contentTop,
            contentRight,
            contentBottom,
            0xAA444444.toInt()
        )
        guiGraphics.fill(
            contentLeft + ItemDetailsLayout.CONTENT_WIDTH - 3,
            barY,
            contentRight - 1,
            barY + barHeight,
            0xFFAAAAAA.toInt()
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (maxScroll > 0 && mouseX in contentLeft.toDouble()..contentRight.toDouble() && mouseY in contentTop.toDouble()..contentBottom.toDouble()) {
            scroll = (scroll - (delta * 4).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, delta)
    }
}
