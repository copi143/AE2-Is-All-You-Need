package allyouneed.logic.crafting

import appeng.api.config.FuzzyMode
import appeng.api.crafting.IPatternDetails
import appeng.api.networking.IGridNode
import appeng.api.networking.crafting.CalculationStrategy
import appeng.api.networking.crafting.ICraftingCPU
import appeng.api.networking.crafting.ICraftingPlan
import appeng.api.networking.crafting.ICraftingRequester
import appeng.api.networking.crafting.ICraftingService
import appeng.api.networking.crafting.ICraftingSimulationRequester
import appeng.api.networking.crafting.ICraftingSubmitResult
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import appeng.api.storage.AEKeyFilter
import appeng.core.AELog
import com.google.common.collect.ImmutableSet
import java.util.concurrent.Future

/**
 * **Eager snapshot** of [ICraftingService] pattern/emittance data for background crafting simulation.
 *
 * All simulation-relevant lookups are copied on the calling (main/server) thread in the constructor.
 * After construction this object holds **no reference** to the live crafting service and must not
 * be used to mutate the grid.
 *
 * Snapshotted:
 * - [getCraftingFor] / [isCraftable]
 * - [canEmitFor]
 * - [getFuzzyCraftable] (via local craftable [KeyCounter])
 * - [getCraftables]
 */
class CachedCraftingService(
    live: ICraftingService,
) : ICraftingService {

    private val patterns: Map<AEKey, List<IPatternDetails>>
    private val emittables: Set<AEKey>
    private val craftableKeys: Set<AEKey>
    private val craftableFuzzyIndex = KeyCounter()

    init {
        val patternMap = HashMap<AEKey, List<IPatternDetails>>()
        val emitSet = HashSet<AEKey>()
        val craftSet = HashSet<AEKey>()

        // getCraftables(true) returns both pattern-craftable and emittable keys.
        val allKeys = live.getCraftables { true }
        for (key in allKeys) {
            val list = live.getCraftingFor(key).toList()
            if (list.isNotEmpty()) {
                patternMap[key] = list
                craftSet.add(key)
                craftableFuzzyIndex.add(key, 1)
            }
            if (live.canEmitFor(key)) {
                emitSet.add(key)
            }
        }

        patterns = patternMap
        emittables = emitSet
        craftableKeys = craftSet

        AELog.debug(
            "CachedCraftingService snapshot: %d craftable keys, %d patterns total, %d emittable",
            craftSet.size,
            patternMap.values.sumOf { it.size },
            emitSet.size,
        )
    }

    override fun getCraftingFor(whatToCraft: AEKey): Collection<IPatternDetails> =
        patterns[whatToCraft] ?: emptyList()

    override fun canEmitFor(someItem: AEKey): Boolean =
        emittables.contains(someItem)

    override fun getFuzzyCraftable(whatToCraft: AEKey, filter: AEKeyFilter): AEKey? {
        for (entry in craftableFuzzyIndex.findFuzzy(whatToCraft, FuzzyMode.IGNORE_ALL)) {
            val key = entry.key
            if (filter.matches(key)) {
                return key
            }
        }
        return null
    }

    override fun isCraftable(whatToCraft: AEKey): Boolean =
        patterns.containsKey(whatToCraft)

    override fun getCraftables(filter: AEKeyFilter): Set<AEKey> {
        val result = HashSet<AEKey>()
        for (key in craftableKeys) {
            if (filter.matches(key)) result.add(key)
        }
        for (key in emittables) {
            if (filter.matches(key)) result.add(key)
        }
        return result
    }

    override fun refreshNodeCraftingProvider(node: IGridNode) {
        throw UnsupportedOperationException("CachedCraftingService is a read-only snapshot")
    }

    override fun beginCraftingCalculation(
        level: net.minecraft.world.level.Level,
        simRequester: ICraftingSimulationRequester,
        craftWhat: AEKey,
        amount: Long,
        strategy: CalculationStrategy,
    ): Future<ICraftingPlan> {
        throw UnsupportedOperationException("CachedCraftingService cannot start nested calculations")
    }

    override fun submitJob(
        job: ICraftingPlan,
        requestingMachine: ICraftingRequester?,
        target: ICraftingCPU?,
        prioritizePower: Boolean,
        src: IActionSource,
    ): ICraftingSubmitResult {
        throw UnsupportedOperationException("CachedCraftingService cannot submit jobs")
    }

    override fun getCpus(): ImmutableSet<ICraftingCPU> = ImmutableSet.of()

    override fun isRequesting(what: AEKey): Boolean = false

    override fun getRequestedAmount(what: AEKey): Long = 0

    override fun isRequestingAny(): Boolean = false
}
