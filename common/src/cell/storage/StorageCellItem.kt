package allyouneed.cell.storage

import allyouneed.cell.ICellItem
import allyouneed.cell.appendPartitionInfo
import allyouneed.cell.getFuzzyMode
import allyouneed.cell.setFuzzyMode
import appeng.api.config.FuzzyMode
import appeng.api.storage.StorageCells
import appeng.api.storage.cells.CellState
import appeng.api.storage.cells.ICellWorkbenchItem
import appeng.api.upgrades.IUpgradeInventory
import appeng.api.upgrades.UpgradeInventories
import appeng.core.localization.GuiText
import appeng.core.localization.PlayerMessages
import appeng.core.localization.Tooltips
import appeng.hooks.AEToolItem
import appeng.items.AEBaseItem
import appeng.items.contents.CellConfig
import appeng.util.ConfigInventory
import appeng.util.InteractionUtil
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import java.util.*

/**
 * Generic item for resource storage cells (item / mana / energy / ...). Holds the data-side
 * [ICellItem] definition (whose [ICellItem.keyType] selects the stored key space) and provides
 * the shared cell-workbench plumbing (config, upgrades, fuzzy mode, capacity tooltip).
 */
open class StorageCellItem(
    properties: Properties,
    /** Data-side definition providing size tier, key space, bytes per type and idle drain. */
    val cell: ICellItem,
) : AEBaseItem(properties), ICellWorkbenchItem, AEToolItem {

    override fun getConfigInventory(stack: ItemStack): ConfigInventory =
        CellConfig.create(cell.keyType.filter(), stack)

    override fun getUpgrades(stack: ItemStack): IUpgradeInventory =
        UpgradeInventories.forItem(stack, if (cell.keyType == appeng.api.stacks.AEKeyType.items()) 4 else 3)

    override fun getFuzzyMode(stack: ItemStack): FuzzyMode = stack.getFuzzyMode()

    override fun setFuzzyMode(stack: ItemStack, fzMode: FuzzyMode) {
        stack.setFuzzyMode(fzMode)
    }

    override fun appendHoverText(
        stack: ItemStack,
        level: Level?,
        lines: MutableList<Component>,
        advancedTooltips: TooltipFlag,
    ) {
        val inv = StorageCells.getCellInventory(stack, null) ?: return
        when (inv) {
            is StorageCellInventory -> {
                lines.add(Tooltips.bytesUsed(inv.getUsedBytes(), inv.getTotalBytes()))
                lines.add(Tooltips.typesUsed(inv.getStoredItemTypes(), inv.getTotalItemTypes()))
                if (inv.isPreformatted()) {
                    val line = GuiText.Partitioned.withSuffix(" - ").appendPartitionInfo(
                        inv.getPartitionListMode(),
                        inv.isFuzzy(),
                    )
                    lines.add(Tooltips.of(line))
                }
            }
            is BigIntegerStorageCellInventory -> {
                lines.add(Tooltips.bytesUsed(inv.getUsedBytes(), inv.getTotalBytes()))
                lines.add(Tooltips.typesUsed(inv.getStoredItemTypes(), inv.getTotalItemTypes()))
                if (inv.isPreformatted()) {
                    val line = GuiText.Partitioned.withSuffix(" - ").appendPartitionInfo(
                        inv.getPartitionListMode(),
                        inv.isFuzzy(),
                    )
                    lines.add(Tooltips.of(line))
                }
            }
            else -> return
        }
    }

    override fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> =
        StorageCellHandler.getTooltipImage(stack)

    // ---- AE2 parity: Shift + right-click to disassemble empty cells (like BasicStorageCell) ----

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        disassemble(player.getItemInHand(hand), level, player)
        return InteractionResultHolder(
            InteractionResult.sidedSuccess(level.isClientSide),
            player.getItemInHand(hand),
        )
    }

    override fun useOn(context: UseOnContext): InteractionResult =
        if (disassemble(context.itemInHand, context.level, context.player)) {
            InteractionResult.sidedSuccess(context.level.isClientSide)
        } else {
            super.useOn(context)
        }

    private fun disassemble(stack: ItemStack, level: Level?, player: Player?): Boolean {
        if (player == null || level == null) return false
        if (!InteractionUtil.isInAlternateUseMode(player)) return false
        if (level.isClientSide) return false
        // Creative cells have no component/housing - not disassemblable
        if (cell.isCreative) return false
        val typed = cell as? TypedStorageCell ?: return false
        val housing = typed.housingItem
        val component = typed.componentItem ?: return false

        val inv = StorageCells.getCellInventory(stack, null) ?: return false
        // Must be selected in hotbar (like AE2) and empty
        if (player.inventory.getSelected() !== stack) return false
        if (!inv.availableStacks.isEmpty) {
            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true)
            return false
        }
        // Remove cell, drop component + housing + upgrades
        player.inventory.setItem(player.inventory.selected, ItemStack.EMPTY)
        player.inventory.placeItemBackInInventory(ItemStack(component))
        for (upgrade in getUpgrades(stack)) {
            player.inventory.placeItemBackInInventory(upgrade)
        }
        player.inventory.placeItemBackInInventory(ItemStack(housing))
        return true
    }

    companion object {
        const val UPGRADE_SLOTS = 4

        /** Item tint: layer1 is the status LED, matching vanilla storage cells. */
        fun getColor(stack: ItemStack, tintIndex: Int): Int {
            if (tintIndex == 1) {
                val inv = StorageCells.getCellInventory(stack, null)
                val state = inv?.status ?: CellState.EMPTY
                return state.stateColor
            }
            return 0xFFFFFF
        }
    }
}
