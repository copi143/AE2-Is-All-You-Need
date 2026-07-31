package allyouneed.cell.dimensional

import allyouneed.api.BigStackSource
import allyouneed.util.bigint.BigKeyCounter
import allyouneed.util.saturateToLong
import appeng.api.config.Actionable
import appeng.api.config.FuzzyMode
import appeng.api.config.IncludeExclude
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import appeng.api.storage.StorageCells
import appeng.api.storage.cells.CellState
import appeng.api.storage.cells.ISaveProvider
import appeng.api.storage.cells.StorageCell
import appeng.api.upgrades.IUpgradeInventory
import appeng.core.definitions.AEItems
import appeng.util.ConfigInventory
import appeng.util.prioritylist.FuzzyPriorityList
import appeng.util.prioritylist.IPartitionList
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.math.BigInteger

class DimensionalCellInventory(
    private val stack: ItemStack,
    private val container: ISaveProvider?,
) : StorageCell, BigStackSource {

    private val cellItem: DimensionalCellItem = stack.item as DimensionalCellItem
    private val partitionList: IPartitionList
    private val partitionListMode: IncludeExclude
    private var cellId: Int = DimensionalCellItem.getCellId(stack)
    private var data: DimensionalCellData? = null

    init {
        val builder = IPartitionList.builder()
        val upgrades = getUpgradesInventory()
        val config = getConfigInventory()
        val isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD)
        if (isFuzzy) {
            builder.fuzzyMode(getFuzzyMode())
        }
        builder.addAll(config.keySet())
        val hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD)
        partitionListMode = if (hasInverter) IncludeExclude.BLACKLIST else IncludeExclude.WHITELIST
        partitionList = builder.build()
    }

    /**
     * Read-only access: never allocates a new cell id or mutates item NBT.
     */
    private fun peekData(): DimensionalCellData? {
        data?.let { return it }
        if (cellId == 0 || !DimensionalCellStore.isAttached()) return null
        val loaded = DimensionalCellStore.getOrLoad(cellId) ?: return null
        data = loaded
        return loaded
    }

    /**
     * Write path: allocate a fresh id on first real mutation.
     */
    private fun ensureDataForWrite(): DimensionalCellData? {
        data?.let { return it }
        if (!DimensionalCellStore.isAttached()) return null
        if (cellId == 0) {
            val id = DimensionalCellStore.allocateId()
            if (id == 0) return null
            cellId = id
            DimensionalCellItem.setCellId(stack, id)
        }
        val loaded = DimensionalCellStore.getOrLoad(cellId) ?: return null
        data = loaded
        return loaded
    }

    override fun insert(what: AEKey, amount: Long, mode: Actionable, source: IActionSource): Long {
        if (amount <= 0) return 0
        if (!partitionList.matchesFilter(what, partitionListMode)) return 0

        // Block recursive non-empty cells
        if (what is AEItemKey) {
            val nested = StorageCells.getCellInventory(what.toStack(), null)
            if (nested != null && !nested.canFitInsideCell()) return 0
        }

        // Infinite capacity: simulate always succeeds without binding an id
        if (mode == Actionable.SIMULATE) {
            if (!DimensionalCellStore.isAttached() && cellId == 0) {
                // Client / unbound: still report that insertion would work
                return amount
            }
            // Server: ensure we could allocate if needed
            if (cellId == 0 && !DimensionalCellStore.isAttached()) return 0
            return amount
        }

        val store = ensureDataForWrite() ?: return 0
        store.add(what, BigInteger.valueOf(amount))
        saveChanges()
        return amount
    }

    override fun extract(what: AEKey, amount: Long, mode: Actionable, source: IActionSource): Long {
        if (amount <= 0) return 0
        val store = if (mode == Actionable.MODULATE) ensureDataForWrite() else peekData()
        store ?: return 0
        val available = store.get(what)
        if (available.signum() <= 0) return 0

        val want = BigInteger.valueOf(amount)
        val taken = if (available <= want) available else want
        if (mode == Actionable.MODULATE) {
            store.set(what, available.subtract(taken))
            saveChanges()
        }
        return taken.saturateToLong()
    }

    override fun getAvailableStacks(out: KeyCounter) {
        val store = peekData() ?: return
        for ((key, amount) in store.amounts) {
            out.add(key, amount.saturateToLong())
        }
    }

    override fun getBigAvailableStacks(out: BigKeyCounter) {
        val store = peekData() ?: return
        for ((key, amount) in store.amounts) {
            out.add(key, amount)
        }
    }

    override fun isPreferredStorageFor(input: AEKey, source: IActionSource): Boolean {
        val store = peekData() ?: return false
        return store.get(input).signum() > 0
    }

    override fun getStatus(): CellState {
        val store = peekData()
        return if (store == null || store.isEmpty()) CellState.EMPTY else CellState.NOT_EMPTY
    }

    override fun getIdleDrain(): Double = 1.0

    override fun canFitInsideCell(): Boolean {
        val store = peekData()
        return store == null || store.isEmpty()
    }

    override fun getDescription(): Component = stack.hoverName

    override fun persist() {
        if (cellId != 0) {
            DimensionalCellStore.persist(cellId)
        }
    }

    private fun saveChanges() {
        if (cellId != 0) {
            DimensionalCellStore.markDirty(cellId)
        }
        if (container != null) {
            container.saveChanges()
        } else {
            persist()
        }
    }

    fun getConfigInventory(): ConfigInventory = cellItem.getConfigInventory(stack)

    fun getUpgradesInventory(): IUpgradeInventory = cellItem.getUpgrades(stack)

    fun getFuzzyMode(): FuzzyMode = cellItem.getFuzzyMode(stack)

    fun isPreformatted(): Boolean = !partitionList.isEmpty

    fun isFuzzy(): Boolean = partitionList is FuzzyPriorityList

    fun getTypeCount(): Int = peekData()?.amounts?.size ?: 0
}
