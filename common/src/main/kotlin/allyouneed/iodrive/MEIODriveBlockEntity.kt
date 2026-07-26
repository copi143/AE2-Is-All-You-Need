package allyouneed.iodrive

import appeng.api.config.Actionable
import appeng.api.implementations.blockentities.IChestOrDrive
import appeng.api.networking.GridFlags
import appeng.api.networking.IGrid
import appeng.api.networking.IGridNode
import appeng.api.networking.ticking.IGridTickable
import appeng.api.networking.ticking.TickRateModulation
import appeng.api.networking.ticking.TickingRequest
import appeng.api.stacks.AEKey
import appeng.api.storage.IStorageMounts
import appeng.api.storage.IStorageProvider
import appeng.api.storage.StorageCells
import appeng.api.storage.StorageHelper
import appeng.api.storage.cells.CellState
import appeng.api.storage.cells.StorageCell
import appeng.blockentity.grid.AENetworkInvBlockEntity
import appeng.blockentity.inventory.AppEngCellInventory
import appeng.api.inventories.InternalInventory
import appeng.me.helpers.MachineSource
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class MEIODriveBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : AENetworkInvBlockEntity(type, pos, state),
    IChestOrDrive,
    IStorageProvider,
    IGridTickable {

    private val cellInv = AppEngCellInventory(this, CELL_COUNT)
    private val watchers = arrayOfNulls<SelfFilteringDriveWatcher>(CELL_COUNT)
    private val mySrc = MachineSource(this)
    private var mode = MEIODriveMode.PAUSED
    private var isCached = false

    init {
        getMainNode()
            .addService(IStorageProvider::class.java, this)
            .addService(IGridTickable::class.java, this)
            .setFlags(GridFlags.REQUIRE_CHANNEL)

        cellInv.setFilter(CellFilter())
    }

    override fun getInternalInventory(): InternalInventory = cellInv

    // -- IChestOrDrive --

    override fun getCellCount() = CELL_COUNT

    override fun getCellStatus(slot: Int): CellState =
        watchers[slot]?.status ?: CellState.EMPTY

    override fun isPowered(): Boolean = mainNode.isOnline

    override fun isCellBlinking(slot: Int) = false

    override fun getCellItem(slot: Int): Item? {
        val stack = cellInv.getStackInSlot(slot)
        return if (stack.isEmpty) null else stack.item
    }

    override fun getCellInventory(slot: Int) = watchers[slot]

    override fun getOriginalCellInventory(slot: Int): StorageCell? = watchers[slot]?.cell

    // -- IStorageProvider --

    override fun mountInventories(storageMounts: IStorageMounts) {
        if (mainNode.isActive) {
            updateState()
            for (w in watchers) {
                if (w != null) storageMounts.mount(w, 0)
            }
        }
    }

    // -- IGridTickable --

    override fun getTickingRequest(node: IGridNode) = TickingRequest(1, 20, true, true)

    override fun tickingRequest(node: IGridNode, ticksSinceLastCall: Int): TickRateModulation {
        if (!mainNode.isActive || mode == MEIODriveMode.PAUSED) return TickRateModulation.IDLE

        val grid = mainNode.grid ?: return TickRateModulation.IDLE
        val remaining = when (mode) {
            MEIODriveMode.OUTPUT -> transferToNetwork(grid, 256)
            MEIODriveMode.INPUT -> transferFromNetwork(grid, 256)
            MEIODriveMode.PAUSED -> return TickRateModulation.IDLE
        }
        return if (remaining < 256) TickRateModulation.FASTER else TickRateModulation.SAME
    }

    // -- Transfer logic --

    private fun transferToNetwork(grid: IGrid, maxItems: Long): Long {
        var remaining = maxItems
        val networkInv = grid.storageService.inventory
        val energy = grid.energyService

        for (slot in 0 until CELL_COUNT) {
            val cell = watchers[slot]?.cell ?: continue
            val stacks = cell.availableStacks

            for (entry in stacks) {
                if (remaining <= 0) return 0
                val what: AEKey = entry.key
                val amount: Long = entry.longValue
                if (amount <= 0) continue

                val canInsert = networkInv.insert(what, amount, Actionable.SIMULATE, mySrc)
                if (canInsert > 0) {
                    val toMove = minOf(canInsert, remaining * what.amountPerOperation)
                    val extracted = cell.extract(what, toMove, Actionable.MODULATE, mySrc)
                    if (extracted > 0) {
                        val inserted = StorageHelper.poweredInsert(energy, networkInv, what, extracted, mySrc)
                        if (inserted < extracted) {
                            cell.insert(what, extracted - inserted, Actionable.MODULATE, mySrc)
                        }
                        remaining -= maxOf(1, extracted / what.amountPerOperation)
                    }
                    break
                }
            }
        }
        return remaining
    }

    private fun transferFromNetwork(grid: IGrid, maxItems: Long): Long {
        var remaining = maxItems
        val networkInv = grid.storageService.inventory
        val energy = grid.energyService

        val stacks = networkInv.availableStacks
        for (entry in stacks) {
            if (remaining <= 0) return 0
            val what: AEKey = entry.key
            val amount: Long = entry.longValue
            if (amount <= 0) continue

            for (slot in 0 until CELL_COUNT) {
                val cell = watchers[slot]?.cell ?: continue
                val canFit = cell.insert(what, amount, Actionable.SIMULATE, mySrc)
                if (canFit > 0) {
                    val toMove = minOf(canFit, remaining * what.amountPerOperation)
                    val extracted = networkInv.extract(what, toMove, Actionable.MODULATE, mySrc)
                    if (extracted > 0) {
                        val inserted = cell.insert(what, extracted, Actionable.MODULATE, mySrc)
                        if (inserted < extracted) {
                            StorageHelper.poweredInsert(energy, networkInv, what, extracted - inserted, mySrc)
                        }
                        remaining -= maxOf(1, extracted / what.amountPerOperation)
                    }
                    break
                }
            }
        }
        return remaining
    }

    // -- State management --

    private fun updateState() {
        if (!isCached) {
            var power = 2.0
            for (slot in 0 until CELL_COUNT) {
                power += updateStateForSlot(slot)
            }
            getMainNode().setIdlePowerUsage(power)
            isCached = true
        }
    }

    private fun updateStateForSlot(slot: Int): Double {
        watchers[slot] = null
        cellInv.setHandler(slot, null)

        val stack = cellInv.getStackInSlot(slot)
        if (!stack.isEmpty) {
            val cell = StorageCells.getCellInventory(stack) { onCellContentChanged() }
            if (cell != null) {
                cellInv.setHandler(slot, cell)
                watchers[slot] = SelfFilteringDriveWatcher(cell, { blinkCell(slot) }, mySrc)
                return cell.idleDrain
            }
        }
        return 0.0
    }

    override fun onChangeInventory(inv: InternalInventory, slot: Int) {
        if (isCached) {
            isCached = false
            updateState()
        }
        IStorageProvider.requestUpdate(getMainNode())
        markForUpdate()
    }

    private fun onCellContentChanged() {
        level?.blockEntityChanged(worldPosition)
    }

    private fun blinkCell(slot: Int) {
        level?.blockEntityChanged(worldPosition)
    }

    // -- NBT persistence --

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putInt("ioMode", mode.ordinal)
    }

    override fun loadTag(tag: CompoundTag) {
        super.loadTag(tag)
        mode = MEIODriveMode.entries.getOrElse(tag.getInt("ioMode")) { MEIODriveMode.PAUSED }
    }

    // -- Public API --

    fun getMode() = mode

    fun setMode(newMode: MEIODriveMode) {
        mode = newMode
        setChanged()
        markForUpdate()
    }

    // -- Inner classes --

    private inner class CellFilter : appeng.util.inv.filter.IAEItemFilter {
        override fun allowExtract(inv: InternalInventory, slot: Int, amount: Int) = true
        override fun allowInsert(inv: InternalInventory, slot: Int, stack: ItemStack) =
            !stack.isEmpty && StorageCells.isCellHandled(stack)
    }

    companion object {
        const val CELL_COUNT = 10
    }
}
