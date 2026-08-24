package allyouneed.cell.storage

import allyouneed.logic.aekey.EnergyKey
import allyouneed.logic.aekey.HpKey
import allyouneed.logic.aekey.ManaKey
import allyouneed.logic.aekey.StaKey
import allyouneed.logic.aekey.XpKey
import allyouneed.util.saturateToLong
import appeng.api.stacks.AEKeyType
import appeng.api.stacks.GenericStack
import appeng.api.storage.cells.ICellHandler
import appeng.api.storage.cells.ISaveProvider
import appeng.api.storage.cells.StorageCell
import appeng.core.AEConfig
import appeng.items.storage.StorageCellTooltipComponent
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import java.util.*

/**
 * Max distinct keys a storage cell can hold, per key space. Item/fluid limits follow AE2
 * vanilla ([appeng.me.cells.BasicCellInventory]); the mod's own key spaces use tighter caps.
 */
object StorageCellTypeLimits {
    private val LIMITS = linkedMapOf(
        AEKeyType.items() to 63,
        AEKeyType.fluids() to 18,
        EnergyKey.Type to 6,
        ManaKey.Type to 6,
        HpKey.Type to 3,
        StaKey.Type to 3,
        XpKey.Type to 3,
    )

    fun of(keyType: AEKeyType): Int = LIMITS[keyType] ?: LIMITS.getValue(AEKeyType.items())
}

/**
 * Single cell handler for all [StorageCellItem]s: the inventory is built from the item data's
 * [ICellItem.keyType], so one handler serves every key space (item / mana / energy / ...).
 */
object StorageCellHandler : ICellHandler {
    override fun isCell(stack: ItemStack): Boolean = stack.item is StorageCellItem

    override fun getCellInventory(stack: ItemStack, container: ISaveProvider?): StorageCell? {
        val item = stack.item as? StorageCellItem ?: return null
        val typed = item.cell as? TypedStorageCell
        return if (typed != null && typed.requiresBigInt) {
            BigIntegerStorageCellInventory(stack, container, typed.keyType)
        } else {
            StorageCellInventory(stack, container, item.cell.keyType)
        }
    }

    fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> {
        val inv = getCellInventory(stack, null) as? StorageCellView ?: return Optional.empty()

        val upgrades: List<ItemStack> = if (AEConfig.instance().isTooltipShowCellUpgrades) {
            inv.getUpgradeStacks()
        } else {
            emptyList()
        }

        val isPreformatted = inv.isPreformatted()

        val content: List<GenericStack>
        val hasMoreContent: Boolean
        if (AEConfig.instance().isTooltipShowCellContent) {
            val maxCountShown = AEConfig.instance().tooltipMaxCellContentShown
            val all = inv.getTooltipStacks()
            hasMoreContent = all.size > maxCountShown
            content = if (all.size > maxCountShown) all.subList(0, maxCountShown) else all
        } else {
            hasMoreContent = false
            content = emptyList()
        }

        return Optional.of(
            StorageCellTooltipComponent(
                upgrades,
                content,
                hasMoreContent,
                isPreformatted,
            ),
        )
    }
}
