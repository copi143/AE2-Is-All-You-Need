package allyouneed.client.integration.emi

import allyouneed.client.itemdetail.ItemDetails
import allyouneed.client.itemdetail.ItemDetailsLayout
import allyouneed.client.itemdetail.ItemDetailsScreen
import allyouneed.client.itemdetail.styling.DetailsStyling.COLOR_SECTION
import allyouneed.client.itemdetail.styling.DetailsStyling.COLOR_VALUE
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.SlotWidget
import dev.emi.emi.api.widget.TextWidget
import dev.emi.emi.api.widget.Widget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.TooltipFlag
import kotlin.math.max

/**
 * Block-details screen rendered with EMI widgets: the header stack is an EMI
 * slot (tooltip, highlight and all) and the sections are EMI text widgets that
 * scroll inside a scissored content frame.
 */
class EmiItemDetailsScreen(details: ItemDetails) : ItemDetailsScreen(details) {

    private val headerWidgets = mutableListOf<Widget>()
    private val contentWidgets = mutableListOf<Widget>()

    private lateinit var headerSlot: SlotWidget

    override fun init() {
        super.init()
        headerWidgets.clear()
        contentWidgets.clear()

        headerSlot = SlotWidget(EmiStack.of(details.stack), left + ItemDetailsLayout.PADDING, top + ItemDetailsLayout.PADDING)
        headerWidgets += headerSlot

        var y = contentTop
        for (section in details.sections) {
            contentWidgets += TextWidget(section.title.getVisualOrderText(), contentLeft + 4, y, COLOR_SECTION, false)
            y += ItemDetailsLayout.LINE_HEIGHT
            for (entry in section.lines) {
                contentWidgets += TextWidget(entry.getVisualOrderText(), contentLeft + 8, y, COLOR_VALUE, false)
                y += ItemDetailsLayout.LINE_HEIGHT
            }
            y += ItemDetailsLayout.SECTION_GAP
        }
        contentHeightPx = y - contentTop
        maxScroll = max(0, contentHeightPx - ItemDetailsLayout.CONTENT_HEIGHT)
    }

    override fun renderHeaderItem(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int) {
        headerWidgets.forEach { it.render(guiGraphics, mouseX, mouseY, 0f) }
    }

    override fun renderContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0, -scroll.toDouble(), 0.0)
        contentWidgets.forEach { it.render(guiGraphics, mouseX, mouseY + scroll, partialTick) }
        guiGraphics.pose().popPose()
        guiGraphics.disableScissor()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        if (headerSlot.bounds.contains(mouseX, mouseY)) {
            val player = Minecraft.getInstance().player
            val flag = if (Minecraft.getInstance().options.advancedItemTooltips) TooltipFlag.ADVANCED else TooltipFlag.NORMAL
            guiGraphics.renderComponentTooltip(font, details.stack.getTooltipLines(player, flag), mouseX, mouseY)
        }
    }
}
