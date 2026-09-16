package allyouneed.util.bigint

import java.math.BigInteger

class IncrementalCellIndex<K> {
    val cells: ObjectCounter<K> = ObjectCounter()
    val last: ObjectCounter<K> = ObjectCounter()
    var valid: Boolean = false
        private set

    fun invalidate() {
        valid = false
    }

    fun markValid() {
        valid = true
    }

    fun onChange(modulate: Boolean, changed: Long, key: K, query: () -> BigInteger?): Boolean {
        if (!modulate || changed <= 0L || !valid) return valid
        val amount = query()
        if (amount == null) {
            valid = false
            return false
        }
        cells.set(key, amount)
        return true
    }

    fun copyLast(): ObjectCounter<K> = last.copy()
}
