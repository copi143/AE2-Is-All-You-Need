package allyouneed.api

import allyouneed.mixin.DelegatingMEInventoryAccessor
import allyouneed.util.bigint.BigKeyCounter
import appeng.api.storage.MEStorage
import appeng.me.storage.DelegatingMEInventory
import appeng.me.storage.DriveWatcher

/**
 * Implemented by storages that can report amounts beyond `long`.
 */
interface BigStackSource {
    /**
     * Append this storage's full BigInteger totals into `out`.
     */
    fun getBigAvailableStacks(out: BigKeyCounter)

    val lastBigStacks: BigKeyCounter?
        /**
         * Last snapshot if any; optional cache for callers that already listed.
         */
        get() = null

    companion object {
        /**
         * Unwrap common AE2 wrappers and collect big stacks when available.
         * 
         * @return true if big stacks were collected (caller should skip long path)
         */
        @JvmStatic
        fun collectBigStacks(storage: MEStorage?, out: BigKeyCounter): Boolean {
            var current = storage
            var depth = 0
            while (depth < 6 && current != null) {
                if (current is BigStackSource) {
                    current.getBigAvailableStacks(out)
                    return true
                }
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
            return false
        }
    }
}
