package allyouneed.cell.storage

import allyouneed.api.BigStackSource
import allyouneed.cell.ICellItem
import allyouneed.cell.buildPartitionList
import allyouneed.item.packet.AllPackets
import allyouneed.util.bigint.BigKeyCounter
import allyouneed.util.saturateToLong
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
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.math.BigInteger

/**
 * BigInteger variant of [StorageCellInventory] for cells whose max amount
 * (`size * amountPerByte`) overflows `Long` (e.g. 256T Energy/Mana = 2^64).
 * Logic mirrors `BasicCellInventory` but all amount arithmetic is `BigInteger`.
 * NBT uses `keys` + `amts_big` (ListTag of byte[]) to hold >Long values;
 * falls back to reading legacy `amts` LongArray for migration.
 */
class BigIntegerStorageCellInventory(
    private val stack: ItemStack,
    private val container: ISaveProvider?,
    keyType: AEKeyType,
) : StorageCell, BigStackSource {

    protected val cellItem: StorageCellItem = stack.item as StorageCellItem
    protected val cell: ICellItem = cellItem.cell
    protected val keyType: AEKeyType = keyType
    private val amountPerByte: Long = keyType.amountPerByte.toLong()
    private val amountPerByteBig: BigInteger = BigInteger.valueOf(amountPerByte)

    private val totalBytes: Long = cell.size
    private val bytesPerType: Long = cell.bytesPerType
    private val maxItemTypes: Int = StorageCellTypeLimits.of(keyType)

    private val partitionList: IPartitionList
    private val partitionListMode: IncludeExclude
    private val hasVoidUpgrade: Boolean
    private val maxItemsPerType: BigInteger

    private var storedItemCount: BigInteger = BigInteger.ZERO
    private val storedAmounts: MutableMap<AEKey, BigInteger> = Object2ObjectOpenHashMap()
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
            if (n == 0L) BigInteger.ZERO
            else {
                val totalStorage = BigInteger.valueOf(totalBytes)
                    .subtract(BigInteger.valueOf(bytesPerType).multiply(BigInteger.valueOf(n)))
                    .multiply(amountPerByteBig)
                if (totalStorage.signum() <= 0) BigInteger.ZERO
                else totalStorage.add(BigInteger.valueOf(n).subtract(BigInteger.ONE))
                    .divide(BigInteger.valueOf(n))
            }
        } else {
            // Effectively infinite per type
            BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(Long.MAX_VALUE))
        }

        loadCellItems()
    }

    override fun getAvailableStacks(out: KeyCounter) {
        for ((key, amt) in storedAmounts) {
            out.add(key, amt.saturateToLong())
        }
    }

    override fun getBigAvailableStacks(out: BigKeyCounter) {
        for ((key, amt) in storedAmounts) {
            out.add(key, amt)
        }
    }

    override fun getIdleDrain(): Double = cell.idleDrain

    override fun getStatus(): CellState {
        if (getStoredItemTypes() == 0L) return CellState.EMPTY
        if (canHoldNewItem()) return CellState.NOT_EMPTY
        if (getRemainingItemCount() > BigInteger.ZERO) return CellState.TYPES_FULL
        return CellState.FULL
    }

    override fun canFitInsideCell(): Boolean = storedAmounts.isEmpty()

    override fun getDescription(): Component = stack.hoverName

    override fun persist() {
        if (isPersisted) return
        val keys = ListTag()
        val amtsBig = ListTag()
        var count = BigInteger.ZERO
        for ((key, amount) in storedAmounts) {
            if (amount.signum() <= 0) continue
            count = count.add(amount)
            keys.add(key.toTagGeneric())
            val amtTag = CompoundTag()
            amtTag.putByteArray("v", amount.toByteArray())
            amtsBig.add(amtTag)
        }
        val tag = stack.getOrCreateTag()
        if (keys.isEmpty()) {
            tag.remove(TAG_STACK_KEYS)
            tag.remove(TAG_STACK_AMOUNTS)
            tag.remove(TAG_STACK_AMOUNTS_BIG)
        } else {
            tag.put(TAG_STACK_KEYS, keys)
            tag.put(TAG_STACK_AMOUNTS_BIG, amtsBig)
            tag.remove(TAG_STACK_AMOUNTS)
        }
        storedItemCount = count
        isPersisted = true
    }

    override fun insert(what: AEKey, amount: Long, mode: Actionable, source: IActionSource?): Long {
        if (amount == 0L || !keyType.contains(what)) return 0
        if (what is AEItemKey && AllPackets.isPacket(what.toStack())) return 0
        if (!partitionList.matchesFilter(what, partitionListMode)) return 0
        if (cell.isBlackListed(stack, what)) return 0

        val inserted = innerInsert(what, amount, mode)
        if (!isPreformatted() && hasVoidUpgrade && !canHoldNewItem()) {
            return if (storedAmounts.containsKey(what)) amount else inserted
        }
        return if (hasVoidUpgrade) amount else inserted
    }

    override fun extract(what: AEKey, amount: Long, mode: Actionable, source: IActionSource?): Long {
        val current = storedAmounts[what] ?: return 0
        if (current.signum() <= 0) return 0
        val want = BigInteger.valueOf(amount)
        return if (want >= current) {
            if (mode == Actionable.MODULATE) {
                storedAmounts.remove(what)
                saveChanges()
            }
            current.saturateToLong()
        } else {
            if (mode == Actionable.MODULATE) {
                storedAmounts[what] = current.subtract(want)
                saveChanges()
            }
            amount
        }
    }

    private fun innerInsert(what: AEKey, amount: Long, mode: Actionable): Long {
        if (what is AEItemKey) {
            val innerCell = StorageCells.getCellInventory(what.toStack(), null)
            if (innerCell != null && !innerCell.canFitInsideCell()) return 0
        }
        val currentTotal = storedAmounts[what] ?: BigInteger.ZERO
        var remaining = getRemainingItemCountBig()
        if (currentTotal.signum() <= 0) {
            if (!canHoldNewItem()) return 0
            remaining = remaining.subtract(amountPerByteBig.multiply(BigInteger.valueOf(bytesPerType)))
            if (remaining.signum() <= 0) return 0
        }
        val maxForType = maxItemsPerType.subtract(currentTotal)
        if (maxForType.signum() <= 0) return 0
        if (remaining > maxForType) remaining = maxForType
        val want = BigInteger.valueOf(amount)
        val usable = if (want > remaining) remaining else want
        if (usable.signum() <= 0) return 0
        if (mode == Actionable.MODULATE) {
            storedAmounts[what] = currentTotal.add(usable)
            saveChanges()
        }
        return usable.saturateToLong()
    }

    private fun getTag(): CompoundTag = stack.getOrCreateTag()

    private fun loadCellItems() {
        storedAmounts.clear()
        storedItemCount = BigInteger.ZERO
        val tag = getTag()
        val keys = tag.getList(TAG_STACK_KEYS, Tag.TAG_COMPOUND.toInt())
        if (keys.isEmpty()) return

        // Prefer big format
        if (tag.contains(TAG_STACK_AMOUNTS_BIG)) {
            val amtsBig = tag.getList(TAG_STACK_AMOUNTS_BIG, Tag.TAG_COMPOUND.toInt())
            if (amtsBig.size != keys.size) return
            for (i in keys.indices) {
                val key = AEKey.fromTagGeneric(keys.getCompound(i)) ?: continue
                val amtTag = amtsBig.getCompound(i)
                val bytes = amtTag.getByteArray("v")
                if (bytes.isEmpty()) continue
                val amount = try { BigInteger(bytes) } catch (_: Exception) { continue }
                if (amount.signum() <= 0) continue
                storedAmounts[key] = amount
                storedItemCount = storedItemCount.add(amount)
            }
            return
        }

        // Fallback: legacy LongArray
        val amts = tag.getLongArray(TAG_STACK_AMOUNTS)
        if (amts.size != keys.size) return
        for (i in amts.indices) {
            val amount = amts[i]
            if (amount <= 0) continue
            val key = AEKey.fromTagGeneric(keys.getCompound(i)) ?: continue
            val big = BigInteger.valueOf(amount)
            storedAmounts[key] = big
            storedItemCount = storedItemCount.add(big)
        }
    }

    private fun saveChanges() {
        var count = BigInteger.ZERO
        for (amt in storedAmounts.values) count = count.add(amt)
        storedItemCount = count
        isPersisted = false
        if (container != null) container.saveChanges() else persist()
    }

    // ---- Accessors ----
    fun getUpgradesInventory(): IUpgradeInventory = cellItem.getUpgrades(stack)
    fun getConfigInventory(): ConfigInventory = cellItem.getConfigInventory(stack)
    fun getCellItems(): Map<AEKey, BigInteger> = storedAmounts
    fun getStoredItemCountBig(): BigInteger = storedItemCount
    fun getStoredItemCount(): Long = storedItemCount.saturateToLong()
    fun getStoredItemTypes(): Long = storedAmounts.size.toLong()
    fun getTotalItemTypes(): Long = maxItemTypes.toLong()
    fun getTotalBytes(): Long = totalBytes
    fun getBytesPerType(): Long = bytesPerType

    fun getUsedBytes(): Long {
        // (stored + unused)/amountPerByte + types*bytesPerType
        val unused = getUnusedItemCountBig()
        val bytesForCount = storedItemCount.add(unused).divide(amountPerByteBig)
        val total = bytesForCount.add(BigInteger.valueOf(getStoredItemTypes()).multiply(BigInteger.valueOf(bytesPerType)))
        return total.saturateToLong()
    }

    fun getFreeBytes(): Long = maxOf(0L, totalBytes - getUsedBytes())

    fun getUnusedItemCountBig(): BigInteger {
        val rem = storedItemCount.mod(amountPerByteBig)
        return if (rem == BigInteger.ZERO) BigInteger.ZERO else amountPerByteBig.subtract(rem)
    }

    fun getUnusedItemCount(): Int = getUnusedItemCountBig().toInt()

    fun getRemainingItemCountBig(): BigInteger {
        val free = BigInteger.valueOf(getFreeBytes())
        return free.multiply(amountPerByteBig).add(getUnusedItemCountBig()).max(BigInteger.ZERO)
    }

    fun getRemainingItemCount(): BigInteger = getRemainingItemCountBig()

    fun getRemainingItemTypes(): Long = minOf(getFreeBytes() / bytesPerType, getTotalItemTypes() - getStoredItemTypes())

    fun canHoldNewItem(): Boolean =
        (getFreeBytes() > bytesPerType || (getFreeBytes() == bytesPerType && getUnusedItemCountBig() > BigInteger.ZERO)) &&
            getRemainingItemTypes() > 0

    fun isPreformatted(): Boolean = !partitionList.isEmpty
    fun getPartitionListMode(): IncludeExclude = partitionListMode
    fun isFuzzy(): Boolean = partitionList is FuzzyPriorityList

    companion object {
        private const val TAG_STACK_KEYS = "keys"
        private const val TAG_STACK_AMOUNTS = "amts"
        private const val TAG_STACK_AMOUNTS_BIG = "amts_big"
    }
}
