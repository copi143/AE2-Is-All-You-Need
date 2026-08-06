package allyouneed.logic.crafting

import allyouneed.api.GlobalIdHolder
import appeng.api.config.Actionable
import appeng.api.config.FuzzyMode
import appeng.api.networking.security.IActionSource
import appeng.api.networking.storage.IStorageService
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import appeng.core.AEConfig
import appeng.crafting.inv.CraftingSimulationState
import com.google.common.collect.Iterables
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.Nullable

/**
 * Fully copies the current ME network inventory into a new [KeyCounter].
 *
 * [IStorageService.getCachedInventory] returns a **live reference** — do not share it
 * with background threads. This helper iterates that view on the calling thread and
 * writes independent key/amount pairs into a fresh counter (value copy, not a reference).
 *
 * Must be called on the server/main thread **before** submitting work to [allyouneed.logic.AE2TaskScheduler].
 */
object MeInventorySnapshot {
    @JvmStatic
    fun copy(storage: IStorageService, @Nullable src: IActionSource?): KeyCounter {
        val result = KeyCounter()
        if (src == null) {
            return result
        }
        val simulated = AEConfig.instance().isCraftingSimulatedExtraction
        for (stack in storage.cachedInventory) {
            val key = stack.key
            val networkAmount = if (simulated) {
                storage.inventory.extract(key, stack.longValue, Actionable.SIMULATE, src)
            } else {
                stack.longValue
            }
            if (networkAmount > 0) {
                result.add(key, networkAmount)
            }
        }
        return result
    }
}

/**
 * [CraftingSimulationState] backed solely by a pre-copied [KeyCounter].
 * Holds no reference to [IStorageService] or any live grid object.
 */
class CopiedNetworkSimulationState(private val list: KeyCounter) : CraftingSimulationState() {
    init {
        println("CopiedNetworkSimulationState")
        for (item in list) {
            println("[${(item.key as GlobalIdHolder).globalId}] ${item.key.displayName.string}: ${item.longValue}")
        }
    }

    override fun simulateExtractParent(what: AEKey, amount: Long): Long = minOf(list.get(what), amount)

    override fun findFuzzyParent(input: AEKey): Iterable<AEKey> =
        Iterables.transform(list.findFuzzy(input, FuzzyMode.IGNORE_ALL)) { it.key }
}

class InventorySnapshot {
//    val map = Int2ObjectOpenHashMap
}
