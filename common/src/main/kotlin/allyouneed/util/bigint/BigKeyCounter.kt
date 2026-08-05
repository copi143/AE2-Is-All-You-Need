package allyouneed.util.bigint

import allyouneed.util.saturateToLong
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.math.BigInteger
import java.util.*
import java.util.function.Consumer

/** Network-wide amount tally using [BigInteger] to avoid long overflow when summing cells. */
class BigKeyCounter {
    private val amounts = Object2ObjectOpenHashMap<AEKey, BigInteger>()

    fun clear() {
        amounts.clear()
    }

    fun add(key: AEKey, amount: Long) {
        if (amount == 0L) return
        add(key, BigInteger.valueOf(amount))
    }

    fun add(key: AEKey, amount: BigInteger) {
        if (amount.signum() == 0) return
        amounts.merge(key, amount, BigInteger::add)
    }

    fun addAll(other: KeyCounter) {
        for (entry in other) {
            add(entry.key, entry.longValue)
        }
    }

    fun addAll(other: BigKeyCounter) {
        for (entry in other.amounts.object2ObjectEntrySet()) {
            add(entry.key, entry.value)
        }
    }

    fun set(key: AEKey, amount: BigInteger?) {
        if (amount == null || amount.signum() == 0) {
            amounts.remove(key)
        } else {
            amounts[key] = amount
        }
    }

    fun get(key: AEKey): BigInteger = amounts.getOrDefault(key, BigInteger.ZERO)

    fun getSaturatedLong(key: AEKey): Long = get(key).saturateToLong()

    fun isEmpty(): Boolean = amounts.isEmpty()

    fun size(): Int = amounts.size

    fun keySet(): Set<AEKey> = Collections.unmodifiableSet(amounts.keys)

    fun entries(): Object2ObjectMap.FastEntrySet<AEKey, BigInteger> = amounts.object2ObjectEntrySet()

    fun removeZeros() {
        amounts.object2ObjectEntrySet().removeIf { it.value.signum() == 0 }
    }

    /** Diff keys whose amounts differ from [other] (including keys only present on one side). */
    fun collectChangedKeys(other: BigKeyCounter, out: Consumer<AEKey>) {
        for (entry in amounts.object2ObjectEntrySet()) {
            if (entry.value.compareTo(other.get(entry.key)) != 0) {
                out.accept(entry.key)
            }
        }
        for (entry in other.amounts.object2ObjectEntrySet()) {
            if (!amounts.containsKey(entry.key)) {
                out.accept(entry.key)
            }
        }
    }

    fun copy(): BigKeyCounter {
        val copy = BigKeyCounter()
        copy.amounts.putAll(amounts)
        return copy
    }

    /** Add saturated long amounts into a KeyCounter for AE2 compatibility paths. */
    fun copySaturatedTo(out: KeyCounter) {
        for (entry in amounts.object2ObjectEntrySet()) {
            out.add(entry.key, entry.value.saturateToLong())
        }
    }

    companion object {
        @JvmStatic
        fun fromKeyCounter(counter: KeyCounter?): BigKeyCounter? {
            if (counter == null) return null
            return BigKeyCounter().also { it.addAll(counter) }
        }
    }
}
