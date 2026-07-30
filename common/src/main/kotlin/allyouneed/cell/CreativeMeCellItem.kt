package allyouneed.cell

import appeng.api.client.AEKeyRendering
import appeng.api.config.FuzzyMode
import appeng.api.storage.StorageCells
import appeng.api.storage.cells.ICellWorkbenchItem
import appeng.core.localization.GuiText
import appeng.core.localization.Tooltips
import appeng.items.AEBaseItem
import appeng.items.contents.CellConfig
import appeng.util.ConfigInventory
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import java.util.Optional

/**
 * Creative ME storage cell that accepts any [appeng.api.stacks.AEKey] type
 * and reports [Long.MAX_VALUE] of each configured resource.
 */
class CreativeMeCellItem(properties: Properties) : AEBaseItem(properties), ICellWorkbenchItem {

    override fun getConfigInventory(stack: ItemStack): ConfigInventory =
        CellConfig.create(stack)

    override fun getFuzzyMode(stack: ItemStack): FuzzyMode = FuzzyMode.IGNORE_ALL

    override fun setFuzzyMode(stack: ItemStack, fzMode: FuzzyMode) {}

    override fun appendHoverText(
        stack: ItemStack,
        level: Level?,
        lines: MutableList<Component>,
        advancedTooltips: TooltipFlag,
    ) {
        if (StorageCells.getCellInventory(stack, null) == null) return
        val cc = getConfigInventory(stack)
        if (cc.isEmpty) return

        if (Screen.hasShiftDown()) {
            for (key in cc.keySet()) {
                lines.add(Tooltips.of(AEKeyRendering.getDisplayName(key)))
            }
        } else {
            lines.add(Tooltips.of(GuiText.PressShiftForFullList))
        }
    }

    override fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> =
        CreativeMeCellHandler.getTooltipImage(stack)

    companion object {
        fun create(): CreativeMeCellItem =
            CreativeMeCellItem(Item.Properties().stacksTo(1))
    }
}
