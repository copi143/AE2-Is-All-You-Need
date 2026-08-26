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
import appeng.api.stacks.GenericStack
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongMaps
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
 * Optimized: lazy NBT parsing + fastIterable to avoid per-tick allocation.
 */
class StorageCellInventory(
    private val stack: ItemStack,
    private val container: ISaveProvider?,
    keyType: AEKeyType,
) : StorageCell, StorageCellView {

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

    // Lazy-loaded storage
    private var storedItemCount: Long = 0
    private var storedAmounts: Object2LongMap<AEKey>? = null
    private var isPersisted = true
    private var isLoaded = false

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
                if (!isFuzzy && partitionListMode == IncludeExclude.WHITELIST && config.keySet().isNotEmpty()) {
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
        // lazy: do NOT loadCellItems() here
        // isPersisted stays true until mutation
    }

    /** Lazily parse NBT into map, mimicking BasicCellInventory.getCellItems(). */
    private fun getCellItemsInternal(): Object2LongMap<AEKey> {
        var map = storedAmounts
        if (map != null) return map
        if (isLoaded) {
            // already loaded but empty
            map = Object2LongOpenHashMap()
            storedAmounts = map
            return map
        }
        map = Object2LongOpenHashMap()
        storedAmounts = map
        isLoaded = true
        // Fill from NBT - 不创建空标签，避免空盘 NBT 污染
        val tag = stack.tag
        if (tag == null) {
            storedItemCount = 0
            return map
        }
        val amts = tag.getLongArray(TAG_STACK_AMOUNTS)
        val keys = tag.getList(TAG_STACK_KEYS, Tag.TAG_COMPOUND.toInt())
        if (amts.isEmpty() && keys.isEmpty()) {
            storedItemCount = 0
            return map
        }
        if (amts.size != keys.size) {
            // corrupted: keep empty but mark loaded
            return map
        }
        var count = 0L
        for (i in amts.indices) {
            val amount = amts[i]
            if (amount <= 0) continue
            val key = AEKey.fromTagGeneric(keys.getCompound(i)) ?: continue
            map.put(key, amount)
            count += amount
        }
        storedItemCount = count
        return map
    }

    /** Public accessor for workbench/tooltip paths; ensures loaded. */
    fun getCellItemsPublic(): Object2LongMap<AEKey> = getCellItemsInternal()

    override fun getAvailableStacks(out: KeyCounter) {
        val map = storedAmounts ?: run {
            // fast path: if not loaded, peek NBT without full parse for empty check?
            // still need to load to enumerate
            getCellItemsInternal()
        }
        for (entry in Object2LongMaps.fastIterable(map)) {
            out.add(entry.key, entry.longValue)
        }
    }

    override fun getIdleDrain(): Double = cell.idleDrain

    override fun getStatus(): CellState {
        // fast path without full load: check NBT size
        if (!isLoaded && storedAmounts == null) {
            val tag = stack.tag
            val count = tag?.getList(TAG_STACK_KEYS, Tag.TAG_COMPOUND.toInt())?.size ?: 0
            if (count == 0) return CellState.EMPTY
            // need full state
        }
        if (getStoredItemTypes() == 0L) return CellState.EMPTY
        if (canHoldNewItem()) return CellState.NOT_EMPTY
        if (getRemainingItemCount() > 0) return CellState.TYPES_FULL
        return CellState.FULL
    }

    override fun canFitInsideCell(): Boolean {
        val map = storedAmounts
        if (map != null) return map.isEmpty()
        if (!isLoaded) {
            val tag = stack.tag ?: return true
            val keys = tag.getList(TAG_STACK_KEYS, Tag.TAG_COMPOUND.toInt())
            return keys.isEmpty()
        }
        return getCellItemsInternal().isEmpty()
    }

    override fun getDescription(): Component = stack.hoverName

    override fun persist() {
        if (isPersisted) return
        val map = storedAmounts
        // if never loaded and no mutation, nothing to persist
        if (map == null && !isLoaded) {
            isPersisted = true
            return
        }
        val actualMap = map ?: getCellItemsInternal()
        if (actualMap.isEmpty()) {
            // 空盘删除 NBT：移除存储标签，若 CompoundTag 变空则清除整标签避免遗留 {}
            stack.tag?.let { tag ->
                tag.remove(TAG_STACK_KEYS)
                tag.remove(TAG_STACK_AMOUNTS)
                if (tag.isEmpty) stack.tag = null
            }
            storedItemCount = 0
            isPersisted = true
            return
        }
        val keys = ListTag()
        // Count as we iterate
        var count = 0L
        // Use fastIterable for speed
        // Need parallel LongArray for amts
        val amtsArray = LongArray(actualMap.size)
        var idx = 0
        for (entry in Object2LongMaps.fastIterable(actualMap)) {
            val amount = entry.longValue
            if (amount <= 0) continue
            keys.add(entry.key.toTagGeneric())
            amtsArray[idx++] = amount
            count += amount
        }
        // Trim if filtered
        val trimmed = if (idx != amtsArray.size) amtsArray.copyOf(idx) else amtsArray
        // If filtered out all, remove keys
        val tag = stack.getOrCreateTag()
        if (keys.isEmpty()) {
            tag.remove(TAG_STACK_KEYS)
            tag.remove(TAG_STACK_AMOUNTS)
            if (tag.isEmpty) stack.tag = null
        } else {
            tag.put(TAG_STACK_KEYS, keys)
            tag.putLongArray(TAG_STACK_AMOUNTS, trimmed)
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
        if (cell.isBlackListed(stack, what)) return 0

        val inserted = innerInsert(what, amount, mode)

        // A void card on a full unformatted cell must not void items it isn't even storing.
        if (!isPreformatted() && hasVoidUpgrade && !canHoldNewItem()) {
            return if (getCellItemsInternal().containsKey(what)) amount else inserted
        }
        return if (hasVoidUpgrade) amount else inserted
    }

    override fun extract(
        what: AEKey,
        amount: Long,
        mode: Actionable,
        source: IActionSource?,
    ): Long {
        val map = getCellItemsInternal()
        val current = map.getLong(what)
        if (current <= 0) return 0
        if (amount >= current) {
            if (mode == Actionable.MODULATE) {
                map.remove(what as Any, current)
                saveChanges()
            }
            return current
        }
        if (mode == Actionable.MODULATE) {
            map.put(what, current - amount)
            saveChanges()
        }
        return amount
    }

    private fun innerInsert(what: AEKey, amount: Long, mode: Actionable): Long {
        if (what is AEItemKey) {
            val innerCell = StorageCells.getCellInventory(what.toStack(), null)
            if (innerCell != null && !innerCell.canFitInsideCell()) {
                return 0
            }
        }

        val map = getCellItemsInternal()
        val currentTotal = map.getLong(what)
        var remaining = getRemainingItemCount()
        if (currentTotal <= 0) {
            if (!canHoldNewItem()) return 0
            remaining -= bytesPerType * amountPerByte
            if (remaining <= 0) return 0
        }
        remaining = maxOf(0L, minOf(remaining, maxItemsPerType - currentTotal))
        val usable = minOf(amount, remaining)

        if (mode == Actionable.MODULATE && usable > 0) {
            map.put(what, currentTotal + usable)
            saveChanges()
        }
        return usable
    }

    private fun getTag(): CompoundTag = stack.getOrCreateTag()

    private fun saveChanges() {
        val map = storedAmounts
        var count = 0L
        if (map != null) {
            for (entry in Object2LongMaps.fastIterable(map)) {
                count += entry.longValue
            }
        } else if (isLoaded) {
            // empty
        } else {
            // if not loaded, we mutated via getCellItemsInternal, so map != null
            for (entry in Object2LongMaps.fastIterable(getCellItemsInternal())) {
                count += entry.longValue
            }
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

    fun getCellItems(): Object2LongMap<AEKey> = getCellItemsInternal()

    fun getStoredItemCount(): Long {
        // ensure loaded to have accurate count
        getCellItemsInternal()
        return storedItemCount
    }

    override fun getStoredItemTypes(): Long = getCellItemsInternal().size.toLong()

    override fun getTotalItemTypes(): Long = maxItemTypes.toLong()

    override fun getTotalBytes(): Long = totalBytes

    fun getBytesPerType(): Long = bytesPerType

    override fun getUsedBytes(): Long {
        // ensure count is up to date
        getCellItemsInternal()
        return (storedItemCount + getUnusedItemCount()) / amountPerByte + getStoredItemTypes() * bytesPerType
    }

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

    override fun isPreformatted(): Boolean = !partitionList.isEmpty

    override fun getPartitionListMode(): IncludeExclude = partitionListMode

    override fun isFuzzy(): Boolean = partitionList is FuzzyPriorityList

    // ---- StorageCellView ----
    override fun getUpgradeStacks(): List<ItemStack> = getUpgradesInventory().toList()

    override fun getTooltipStacks(): List<GenericStack> =
        getCellItemsInternal().let { map ->
            ArrayList<GenericStack>(map.size).also { out ->
                for (e in Object2LongMaps.fastIterable(map)) {
                    out.add(GenericStack(e.key, e.longValue))
                }
            }
        }

    companion object {
        private const val TAG_STACK_KEYS = "keys"
        private const val TAG_STACK_AMOUNTS = "amts"
    }
}
