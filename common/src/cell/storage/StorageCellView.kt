package allyouneed.cell.storage

import appeng.api.config.IncludeExclude
import appeng.api.stacks.GenericStack
import net.minecraft.world.item.ItemStack

/**
 * 共用视图，消除 [StorageCellItem.appendHoverText] 与 [StorageCellHandler.getTooltipImage]
 * 中对 `StorageCellInventory / BigIntegerStorageCellInventory` 的重复 `when` 分支。
 */
interface StorageCellView {
    val usedBytes: Long
    val totalBytes: Long
    val storedItemTypes: Long
    val totalItemTypes: Long
    val isPreformatted: Boolean
    val partitionListMode: IncludeExclude
    val isFuzzy: Boolean
    val upgradeStacks: List<ItemStack>
    val tooltipStacks: List<GenericStack>
}
