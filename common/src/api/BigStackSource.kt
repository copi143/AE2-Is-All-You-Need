package allyouneed.api

import allyouneed.mixin.ae2.DelegatingMEInventoryAccessor
import allyouneed.util.bigint.BigKeyCounter
import appeng.api.stacks.AEKey
import appeng.api.storage.MEStorage
import appeng.me.storage.DelegatingMEInventory
import appeng.me.storage.DriveWatcher
import appeng.me.storage.NetworkStorage
import java.math.BigInteger

/**
 * Implemented by storages that can report amounts beyond `long`.
 */
interface BigStackSource {
    /**
     * Append this storage's full BigInteger totals into `out`.
     */
    fun getBigAvailableStacks(out: BigKeyCounter)

    fun getBigAmount(what: AEKey): BigInteger {
        val tmp = BigKeyCounter()
        getBigAvailableStacks(tmp)
        return tmp.getBigInteger(what)
    }

    val lastBigStacks: BigKeyCounter?
        /**
         * Last snapshot if any; optional cache for callers that already listed.
         */
        get() = null

    companion object {
        @JvmStatic
        fun unwrap(storage: MEStorage?): MEStorage? {
            var current = storage
            var depth = 0
            while (depth < 6 && current != null) {
                if (current is DriveWatcher) {
                    current = current.cell
                    depth++
                    continue
                }
                if (current is DelegatingMEInventory) {
                    current = (current as DelegatingMEInventoryAccessor).`allyouneed$getDelegate`()
                    depth++
                    continue
                }
                break
            }
            return current
        }

        @JvmStatic
        fun isCellMount(storage: MEStorage?): Boolean {
            val current = unwrap(storage) ?: return false
            if (current is NetworkStorage) return false
            return current is BigStackSource
        }

        @JvmStatic
        fun queryAmount(storage: MEStorage?, what: AEKey): BigInteger? {
            val current = unwrap(storage) ?: return null
            if (current is BigStackSource) return current.getBigAmount(what)
            return null
        }

        /**
         * Unwrap common AE2 wrappers and collect big stacks when available.
         *
         * @return true if big stacks were collected (caller should skip long path)
         */
        @JvmStatic
        fun collectBigStacks(storage: MEStorage?, out: BigKeyCounter): Boolean {
            val current = unwrap(storage) ?: return false
            if (current is BigStackSource) {
                current.getBigAvailableStacks(out)
                return true
            }
            return false
        }
    }
}
