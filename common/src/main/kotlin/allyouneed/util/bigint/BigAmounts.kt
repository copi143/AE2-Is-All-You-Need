package allyouneed.util.bigint

import appeng.api.stacks.AEKey
import appeng.menu.me.common.GridInventoryEntry
import java.math.BigInteger
import java.util.Collections
import java.util.WeakHashMap

/**
 * Holds BigInteger stored amounts for [GridInventoryEntry] and a thread-local
 * snapshot used while building ME inventory update packets.
 */
object BigAmounts {
    private val ENTRY_AMOUNTS: MutableMap<GridInventoryEntry, BigInteger> =
        Collections.synchronizedMap(WeakHashMap())

    private val CURRENT = ThreadLocal<BigKeyCounter>()

    @JvmStatic
    fun setEntryAmount(entry: GridInventoryEntry?, amount: BigInteger?) {
        if (entry == null) return
        if (amount == null) {
            ENTRY_AMOUNTS.remove(entry)
        } else {
            ENTRY_AMOUNTS[entry] = amount
        }
    }

    @JvmStatic
    fun getEntryAmount(entry: GridInventoryEntry?): BigInteger {
        if (entry == null) return BigInteger.ZERO
        return ENTRY_AMOUNTS[entry] ?: BigInteger.valueOf(maxOf(0L, entry.storedAmount))
    }

    @JvmStatic
    fun hasEntryAmount(entry: GridInventoryEntry?): Boolean =
        entry != null && ENTRY_AMOUNTS.containsKey(entry)

    @JvmStatic
    fun copyEntryAmount(from: GridInventoryEntry?, to: GridInventoryEntry?) {
        if (from == null || to == null) return
        val big = ENTRY_AMOUNTS[from] ?: return
        ENTRY_AMOUNTS[to] = big
    }

    @JvmStatic
    fun getCurrent(): BigKeyCounter? = CURRENT.get()

    @JvmStatic
    fun setCurrent(counter: BigKeyCounter?) {
        if (counter == null) {
            CURRENT.remove()
        } else {
            CURRENT.set(counter)
        }
    }

    @JvmStatic
    fun clearCurrent() {
        CURRENT.remove()
    }

    /** Amount from the current snapshot, or null if no snapshot is active. */
    @JvmStatic
    fun getCurrentAmount(key: AEKey?): BigInteger? {
        val current = CURRENT.get()
        if (current == null || key == null) return null
        return current.get(key)
    }
}
