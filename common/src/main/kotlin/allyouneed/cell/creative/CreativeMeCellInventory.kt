package allyouneed.cell.creative

import appeng.api.config.Actionable
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import appeng.api.storage.cells.CellState
import appeng.api.storage.cells.StorageCell
import appeng.items.contents.CellConfig
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Infinite creative inventory: configured keys report [Long.MAX_VALUE],
 * and insert/extract of those keys always succeeds without changing the cell.
 */
class CreativeMeCellInventory(private val stack: ItemStack) : StorageCell {
    private val configured: Set<AEKey> = CellConfig.create(stack).keySet().toSet()

    override fun insert(what: AEKey, amount: Long, mode: Actionable, source: IActionSource): Long =
        if (configured.contains(what)) amount else 0L

    override fun extract(what: AEKey, amount: Long, mode: Actionable, source: IActionSource): Long =
        if (configured.contains(what)) amount else 0L

    override fun getAvailableStacks(out: KeyCounter) {
        for (key in configured) {
            out.add(key, Long.MAX_VALUE)
        }
    }

    override fun isPreferredStorageFor(input: AEKey, source: IActionSource): Boolean =
        configured.contains(input)

    override fun getStatus(): CellState =
        if (configured.isEmpty()) CellState.EMPTY else CellState.TYPES_FULL

    override fun getIdleDrain(): Double = 0.0

    override fun canFitInsideCell(): Boolean = configured.isEmpty()

    override fun getDescription(): Component = stack.hoverName

    override fun persist() {}
}
