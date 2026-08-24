package allyouneed.cell.storage

import appeng.api.config.IncludeExclude
import appeng.api.stacks.GenericStack
import net.minecraft.world.item.ItemStack

/**
 * 共用视图，消除 [StorageCellItem.appendHoverText] 与 [StorageCellHandler.getTooltipImage]
 * 中对 `StorageCellInventory / BigIntegerStorageCellInventory` 的重复 `when` 分支。
 */
interface StorageCellView {
    fun getUsedBytes(): Long
    fun getTotalBytes(): Long
    fun getStoredItemTypes(): Long
    fun getTotalItemTypes(): Long
    fun isPreformatted(): Boolean
    fun getPartitionListMode(): IncludeExclude
    fun isFuzzy(): Boolean
    fun getUpgradeStacks(): List<ItemStack>
    fun getTooltipStacks(): List<GenericStack>
}
