package allyouneed.util.bigint

import appeng.api.stacks.AEKey
import appeng.menu.me.common.GridInventoryEntry
import java.math.BigInteger

/**
 * Helpers for [BigAmountHolder] amounts on [GridInventoryEntry].
 *
 * Entry amounts live on the entry instance (mixin field). The only process-wide state is a
 * [ThreadLocal] build context while an ME inventory packet is being assembled.
 */
object BigAmounts {
    private val CURRENT = ThreadLocal<BigKeyCounter>()

    @JvmStatic
    fun setEntryAmount(entry: GridInventoryEntry?, amount: BigInteger?) {
        if (entry is BigAmountHolder) {
            entry.setBigAmount(amount)
        }
    }

    @JvmStatic
    fun getEntryAmount(entry: GridInventoryEntry?): BigInteger {
        if (entry is BigAmountHolder) {
            return entry.getBigAmount()
                ?: BigInteger.valueOf(maxOf(0L, entry.storedAmount))
        }
        if (entry == null) return BigInteger.ZERO
        return BigInteger.valueOf(maxOf(0L, entry.storedAmount))
    }

    @JvmStatic
    fun hasEntryAmount(entry: GridInventoryEntry?): Boolean =
        entry is BigAmountHolder && entry.getBigAmount() != null

    @JvmStatic
    fun copyEntryAmount(from: GridInventoryEntry?, to: GridInventoryEntry?) {
        if (from !is BigAmountHolder || to !is BigAmountHolder) return
        to.setBigAmount(from.getBigAmount())
    }

    @JvmStatic
    fun getCurrent(): BigKeyCounter? = CURRENT.get()

    @JvmStatic
    fun setCurrent(counter: BigKeyCounter?) {
        if (counter == null) CURRENT.remove() else CURRENT.set(counter)
    }

    @JvmStatic
    fun clearCurrent() {
        CURRENT.remove()
    }

    @JvmStatic
    fun getCurrentAmount(key: AEKey?): BigInteger? {
        val current = CURRENT.get()
        if (current == null || key == null) return null
        return current.get(key)
    }
}
