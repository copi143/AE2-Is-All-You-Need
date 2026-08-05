package allyouneed.logic.crafting

import allyouneed.pattern.adaptive.AdaptiveStatisticalPattern
import appeng.api.config.Actionable
import appeng.api.crafting.IPatternDetails
import appeng.api.networking.crafting.ICraftingService
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import appeng.crafting.CraftBranchFailure
import appeng.crafting.inv.CraftingSimulationState

/**
 * Custom CraftingTreeProcess that intercepts adaptive statistical patterns
 * and scales their inputs by ceil(N/p) instead of using binomial distribution.
 */
class ACraftingTreeProcess(
    cc: ICraftingService,
    private val job: ACraftingCalculation,
    val details: IPatternDetails,
    private val parent: ACraftingTreeNode?
) {
    private val nodes: MutableMap<ACraftingTreeNode, Long?> = LinkedHashMap<ACraftingTreeNode, Long?>()
    var possible: Boolean = true
    private var containerItems = false
    private var limitQty = false

    init {
        updateLimitQty()

        val inputs = this.details.inputs
        for (x in inputs.indices) {
            val input = inputs[x]
            val firstInput = input.possibleInputs[0]
            this.nodes[ACraftingTreeNode(cc, job, firstInput.what(), firstInput.amount(), this, x)] = input.multiplier
        }
    }

    fun notRecursive(details: IPatternDetails): Boolean {
        return this.parent == null || this.parent.notRecursive(details)
    }

    private fun updateLimitQty() {
        for (input in details.inputs) {
            val primaryInput = input.possibleInputs[0]
            var isAnInput = false

            for (output in details.outputs) {
                if (output.what().matches(primaryInput)) {
                    isAnInput = true
                    break
                }
            }

            if (isAnInput) {
                this.limitQty = true
            }

            if (input.getRemainingKey(primaryInput.what()) != null) {
                this.containerItems = true
                this.limitQty = true
            }
        }
    }

    fun limitsQuantity(): Boolean {
        return this.limitQty
    }

    @Throws(CraftBranchFailure::class, InterruptedException::class)
    fun request(inv: CraftingSimulationState, times: Long) {
        this.job.handlePausing()

        val containerItems = if (this.containerItems) KeyCounter() else null

        for (entry in this.nodes.entries) {
            entry.key.request(inv, entry.value!! * times, containerItems)
        }

        if (containerItems != null) {
            for (stack in containerItems) {
                inv.insert(stack.key, stack.longValue, Actionable.MODULATE)
                inv.addStackBytes(stack.key, stack.longValue, 1)
            }
        }

        for (out in this.details.outputs) {
            inv.insert(out.what(), out.amount() * times, Actionable.MODULATE)
        }

        inv.addCrafting(details, times)
        inv.addBytes(times.toDouble())
    }

    val nodeCount: Long
        get() {
            var tot: Long = 0
            for (node in this.nodes.keys) {
                tot += node.nodeCount
            }
            return tot
        }

    fun getOutputCount(what: AEKey): Long {
        var tot: Long = 0
        for (`is` in this.details.outputs) {
            if (what.matches(`is`)) {
                tot += `is`.amount()
            }
        }
        return tot
    }

    fun hasMultiplePaths(): Boolean {
        for (entry in nodes.entries) {
            if (entry.key.hasMultiplePaths()) {
                return true
            }
        }
        return false
    }

    val successProbability: Double
        get() {
            var ownProb = 1.0
            if (this.details is AdaptiveStatisticalPattern) {
                ownProb = 1.0 - (1.0 - details.probability)
            }
            var childProb = 1.0
            for (entry in this.nodes.entries) {
                childProb *= entry.key.successProbability
            }
            return ownProb * childProb
        }
}
