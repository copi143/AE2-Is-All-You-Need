package allyouneed.cell.dimensional

import allyouneed.api.BigStackSource
import allyouneed.util.BigKeyCounter
import allyouneed.util.SiAmountFormat
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
import appeng.items.contents.CellConfig
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

    private fun ensureData(): DimensionalCellData? {
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

        val store = ensureData() ?: return 0
        if (mode == Actionable.MODULATE) {
            store.add(what, BigInteger.valueOf(amount))
            saveChanges()
        }
        return amount
    }

    override fun extract(what: AEKey, amount: Long, mode: Actionable, source: IActionSource): Long {
        if (amount <= 0) return 0
        val store = ensureData() ?: return 0
        val available = store.get(what)
        if (available.signum() <= 0) return 0

        val want = BigInteger.valueOf(amount)
        val taken = if (available <= want) available else want
        if (mode == Actionable.MODULATE) {
            store.set(what, available.subtract(taken))
            saveChanges()
        }
        return SiAmountFormat.saturateToLong(taken)
    }

    override fun getAvailableStacks(out: KeyCounter) {
        val store = ensureData() ?: return
        for ((key, amount) in store.amounts) {
            out.add(key, SiAmountFormat.saturateToLong(amount))
        }
    }

    override fun getBigAvailableStacks(out: BigKeyCounter) {
        val store = ensureData() ?: return
        for ((key, amount) in store.amounts) {
            out.add(key, amount)
        }
    }

    override fun isPreferredStorageFor(input: AEKey, source: IActionSource): Boolean {
        val store = data ?: ensureData() ?: return false
        return store.get(input).signum() > 0
    }

    override fun getStatus(): CellState {
        val store = data ?: ensureData()
        return if (store == null || store.isEmpty()) CellState.EMPTY else CellState.NOT_EMPTY
    }

    override fun getIdleDrain(): Double = 1.0

    override fun canFitInsideCell(): Boolean {
        val store = data ?: ensureData()
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

    fun getTypeCount(): Int = (data ?: ensureData())?.amounts?.size ?: 0
}
