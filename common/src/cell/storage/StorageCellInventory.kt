package allyouneed.cell.storage

import allyouneed.cell.ICellItem
import allyouneed.cell.buildPartitionList
import allyouneed.item.packet.AllPackets
import appeng.api.config.Actionable
import appeng.api.config.IncludeExclude
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.api.stacks.AEKeyType
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
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Storage inventory for [StorageCellItem]. A faithful port of vanilla
 * [appeng.me.cells.BasicCellInventory] where every byte/amount is `long`,
 * so tiers up to 256T (2^48 bytes, 2^51 items) fit without overflow.
 * Contents are stored on the item's NBT as `keys` + `amts`, like vanilla.
 *
 * Generic over the key space via the [keyType] parameter; the per-type capacity
 * limit comes from [StorageCellTypeLimits] (63 items / 18 fluids / ...).
 */
class StorageCellInventory(
    private val stack: ItemStack,
    private val container: ISaveProvider?,
    keyType: AEKeyType,
) : StorageCell {

    protected val cellItem: StorageCellItem = stack.item as StorageCellItem

    /** Data-side definition (size tier, bytes per type, idle drain). */
    protected val cell: ICellItem = cellItem.cell

    protected val keyType: AEKeyType = keyType
    private val amountPerByte: Long = keyType.amountPerByte.toLong()

    /** Total capacity in bytes (long, unlike vanilla's int). */
    private val totalBytes: Long = cell.size

    /** Bytes reserved per distinct item type (scales with tier, like vanilla). */
    private val bytesPerType: Long = cell.bytesPerType

    private val maxItemTypes: Int = StorageCellTypeLimits.of(keyType)

    private val partitionList: IPartitionList
    private val partitionListMode: IncludeExclude
    private val hasVoidUpgrade: Boolean
    private val maxItemsPerType: Long

    private var storedItemCount: Long = 0
    private val storedAmounts: Object2LongMap<AEKey> = Object2LongOpenHashMap()
    private var isPersisted = false

    init {
        val upgrades = getUpgradesInventory()
        val config = getConfigInventory()
        val isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD)
        val (list, mode) = buildPartitionList(stack, upgrades, config)
        partitionList = list
        partitionListMode = mode

        hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD)

        maxItemsPerType = if (upgrades.isInstalled(AEItems.EQUAL_DISTRIBUTION_CARD)) {
            val partitionKeyCount =
                if (!isFuzzy && partitionListMode == IncludeExclude.WHITELIST && !config.keySet().isEmpty()) {
                    config.keySet().size
                } else {
                    maxItemTypes
                }
            val n = minOf(partitionKeyCount.toLong(), maxItemTypes.toLong())
            val totalStorage = (totalBytes - bytesPerType * n) * amountPerByte
            maxOf(0L, (totalStorage + n - 1) / n)
        } else {
            Long.MAX_VALUE
        }

        loadCellItems()
    }

    override fun getAvailableStacks(out: KeyCounter) {
        for (entry in storedAmounts.object2LongEntrySet()) {
            out.add(entry.key, entry.longValue)
        }
    }

    override fun getIdleDrain(): Double = cell.idleDrain

    override fun getStatus(): CellState {
        if (getStoredItemTypes() == 0L) return CellState.EMPTY
        if (canHoldNewItem()) return CellState.NOT_EMPTY
        if (getRemainingItemCount() > 0) return CellState.TYPES_FULL
        return CellState.FULL
    }

    override fun canFitInsideCell(): Boolean = storedAmounts.isEmpty()

    override fun getDescription(): Component = stack.hoverName

    override fun persist() {
        if (isPersisted) return

        val amts = LongArrayList(storedAmounts.size)
        val keys = ListTag()
        var count = 0L
        for (entry in storedAmounts.object2LongEntrySet()) {
            val amount = entry.longValue
            if (amount > 0) {
                count += amount
                keys.add(entry.key.toTagGeneric())
                amts.add(amount)
            }
        }

        val tag = stack.getOrCreateTag()
        if (keys.isEmpty()) {
            tag.remove(TAG_STACK_KEYS)
            tag.remove(TAG_STACK_AMOUNTS)
        } else {
            tag.put(TAG_STACK_KEYS, keys)
            tag.putLongArray(TAG_STACK_AMOUNTS, amts.toLongArray())
        }

        storedItemCount = count
        isPersisted = true
    }

    override fun insert(
        what: AEKey,
        amount: Long,
        mode: Actionable,
        source: IActionSource?,
    ): Long {
        if (amount == 0L || !keyType.contains(what)) return 0
        if (what is AEItemKey && AllPackets.isPacket(what.toStack())) return 0
        if (!partitionList.matchesFilter(what, partitionListMode)) return 0

        val inserted = innerInsert(what, amount, mode)

        // A void card on a full unformatted cell must not void items it isn't even storing.
        if (!isPreformatted() && hasVoidUpgrade && !canHoldNewItem()) {
            return if (storedAmounts.containsKey(what)) amount else inserted
        }
        return if (hasVoidUpgrade) amount else inserted
    }

    override fun extract(
        what: AEKey,
        amount: Long,
        mode: Actionable,
        source: IActionSource?,
    ): Long {
        val current = storedAmounts.getLong(what)
        if (current > 0) {
            if (amount >= current) {
                if (mode == Actionable.MODULATE) {
                    storedAmounts.remove(what as Any, current)
                    saveChanges()
                }
                return current
            }
            if (mode == Actionable.MODULATE) {
                storedAmounts.put(what, current - amount)
                saveChanges()
            }
            return amount
        }
        return 0
    }

    private fun innerInsert(what: AEKey, amount: Long, mode: Actionable): Long {
        if (what is AEItemKey) {
            val innerCell = StorageCells.getCellInventory(what.toStack(), null)
            if (innerCell != null && !innerCell.canFitInsideCell()) {
                return 0
            }
        }

        val currentTotal = storedAmounts.getLong(what)
        var remaining = getRemainingItemCount()
        if (currentTotal <= 0) {
            if (!canHoldNewItem()) return 0
            remaining -= bytesPerType * amountPerByte
            if (remaining <= 0) return 0
        }
        remaining = maxOf(0L, minOf(remaining, maxItemsPerType - currentTotal))
        val usable = minOf(amount, remaining)

        if (mode == Actionable.MODULATE && usable > 0) {
            storedAmounts.put(what, currentTotal + usable)
            saveChanges()
        }
        return usable
    }

    private fun getTag(): CompoundTag = stack.getOrCreateTag()

    private fun loadCellItems() {
        storedAmounts.clear()
        storedItemCount = 0

        val tag = getTag()
        val amts = tag.getLongArray(TAG_STACK_AMOUNTS)
        val keys = tag.getList(TAG_STACK_KEYS, Tag.TAG_COMPOUND.toInt())
        if (amts.size != keys.size) {
            return
        }

        for (i in amts.indices) {
            val amount = amts[i]
            if (amount <= 0) continue
            val key = AEKey.fromTagGeneric(keys.getCompound(i)) ?: continue
            storedAmounts.put(key, amount)
            storedItemCount += amount
        }
    }

    private fun saveChanges() {
        var count = 0L
        for (entry in storedAmounts.object2LongEntrySet()) {
            count += entry.longValue
        }
        storedItemCount = count
        isPersisted = false
        if (container != null) {
            container.saveChanges()
        } else {
            persist()
        }
    }

    // ---- Cell workbench / metadata accessors (mirrors BasicCellInventory) ----

    fun getUpgradesInventory(): IUpgradeInventory = cellItem.getUpgrades(stack)

    fun getConfigInventory(): ConfigInventory = cellItem.getConfigInventory(stack)

    fun getCellItems(): Object2LongMap<AEKey> = storedAmounts

    fun getStoredItemCount(): Long = storedItemCount

    fun getStoredItemTypes(): Long = storedAmounts.size.toLong()

    fun getTotalItemTypes(): Long = maxItemTypes.toLong()

    fun getTotalBytes(): Long = totalBytes

    fun getBytesPerType(): Long = bytesPerType

    fun getUsedBytes(): Long =
        (storedItemCount + getUnusedItemCount()) / amountPerByte + getStoredItemTypes() * bytesPerType

    fun getFreeBytes(): Long = totalBytes - getUsedBytes()

    fun getUnusedItemCount(): Int {
        val remainder = (storedItemCount % amountPerByte).toInt()
        return if (remainder == 0) 0 else amountPerByte.toInt() - remainder
    }

    fun getRemainingItemCount(): Long =
        maxOf(0L, getFreeBytes() * amountPerByte + getUnusedItemCount())

    fun getRemainingItemTypes(): Long =
        minOf(getFreeBytes() / bytesPerType, getTotalItemTypes() - getStoredItemTypes())

    fun canHoldNewItem(): Boolean =
        (getFreeBytes() > bytesPerType || (getFreeBytes() == bytesPerType && getUnusedItemCount() > 0)) &&
                getRemainingItemTypes() > 0

    fun isPreformatted(): Boolean = !partitionList.isEmpty

    fun getPartitionListMode(): IncludeExclude = partitionListMode

    fun isFuzzy(): Boolean = partitionList is FuzzyPriorityList

    companion object {
        private const val TAG_STACK_KEYS = "keys"
        private const val TAG_STACK_AMOUNTS = "amts"
    }
}
