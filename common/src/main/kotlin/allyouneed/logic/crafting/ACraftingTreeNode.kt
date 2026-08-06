package allyouneed.logic.crafting

import allyouneed.pattern.adaptive.AdaptiveStatisticalPattern
import appeng.api.config.Actionable
import appeng.api.crafting.IPatternDetails
import appeng.api.networking.crafting.ICraftingService
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import appeng.api.stacks.KeyCounter
import appeng.crafting.CraftBranchFailure
import appeng.crafting.execution.CraftingCpuHelper
import appeng.crafting.execution.InputTemplate
import appeng.crafting.inv.ChildCraftingSimulationState
import appeng.crafting.inv.CraftingSimulationState
import appeng.crafting.inv.ICraftingInventory
import net.minecraft.world.level.Level
import java.util.*
import java.util.stream.Collectors

/**
 * Custom CraftingTreeNode that intercepts adaptive statistical patterns
 * and scales their inputs by ceil(N/p) instead of using binomial distribution.
 */
class ACraftingTreeNode(
    private val cc: ICraftingService,
    private val job: ACraftingCalculation,
    what: AEKey,
    private val amount: Long,
    private val parent: ACraftingTreeProcess?,
    slot: Int,
) {
    val parentInput: IPatternDetails.IInput? = if (slot == -1) null else parent?.details?.inputs[slot]
    private val level: Level? = job.level
    private val what: AEKey = findCraftedStack(what)
    private val canEmit: Boolean = cc.canEmitFor(what)
    private var nodes: ArrayList<ACraftingTreeProcess>? = null
    private var adaptiveTotalRequested: Long = 0

    private fun findCraftedStack(wat: AEKey): AEKey {
        if (cc.canEmitFor(wat)) {
            return wat
        }

        val patterns = cc.getCraftingFor(wat)

        if (patterns.isEmpty() && parentInput != null) {
            val acceptableAmount = parentInput.possibleInputs[0].amount()

            for (possibleInput in parentInput.possibleInputs) {
                if (possibleInput.amount() != acceptableAmount) {
                    continue
                }

                val fuzzy = cc.getFuzzyCraftable(
                    possibleInput.what(), { fuzzyCandidate: AEKey? -> this.parentInput.isValid(fuzzyCandidate, level) },
                )

                if (fuzzy != null) {
                    return fuzzy
                }
            }
        }

        return wat
    }

    private fun buildChildPatterns() {
        check(!this.canEmit) { "Internal AE2 error: this node is emitable, it shouldn't use patterns!" }

        if (this.nodes == null) {
            this.nodes = ArrayList<ACraftingTreeProcess>()

            for (details in wrapPatternsForNode(cc, this.what)) {
                if (this.parent == null || this.parent.notRecursive(details)) {
                    this.nodes!!.add(ACraftingTreeProcess(cc, job, details, this))
                }
            }
        }
    }

    private fun wrapPatternsForNode(
        service: ICraftingService, whatToCraft: AEKey?
    ): MutableCollection<IPatternDetails> {
        val patterns = service.getCraftingFor(whatToCraft)
        if (this.adaptiveTotalRequested <= 0) {
            return patterns
        }
        val result = ArrayList<IPatternDetails>(patterns.size)
        for (p in patterns) {
            if (p is AdaptiveStatisticalPattern) {
                result.add(p.forRequest(this.adaptiveTotalRequested))
            } else {
                result.add(p)
            }
        }
        return result
    }

    fun notRecursive(details: IPatternDetails): Boolean {
        for (output in details.outputs) {
            if (this.what.matches(output)) {
                return false
            }
        }

        for (input in details.inputs) {
            if (this.what.matches(input.possibleInputs[0])) {
                return false
            }
        }

        if (this.parent == null) {
            return true
        }

        return this.parent.notRecursive(details)
    }

    @Throws(CraftBranchFailure::class, InterruptedException::class)
    fun request(inv: CraftingSimulationState, requestedAmount: Long, containerItems: KeyCounter?) {
        var requestedAmount = requestedAmount
        this.adaptiveTotalRequested = requestedAmount * this.amount

        this.job.handlePaUSING()

        inv.addStackBytes(what, amount, requestedAmount)

        // 1) COLLECT ITEMS FROM THE INVENTORY
        for (template in getValidItemTemplates(inv)) {
            val extracted = CraftingCpuHelper.extractTemplates(inv, template, requestedAmount)

            if (extracted > 0) {
                requestedAmount -= extracted
                addContainerItems(template.key(), extracted, containerItems)

                if (requestedAmount == 0L) {
                    return
                }
            }
        }

        addContainerItems(what, requestedAmount, containerItems)

        // 2) EMITABLE ITEMS
        if (this.canEmit) {
            inv.emitItems(this.what, this.amount * requestedAmount)
            return
        }

        // 3) USE PATTERNS
        buildChildPatterns()
        var totalRequestedItems = requestedAmount * this.amount
        if (this.nodes!!.size == 1) {
            val pro = this.nodes!![0]
            val craftedPerPattern = pro.getOutputCount(this.what)

            while (pro.possible && totalRequestedItems > 0) {
                val times: Long = if (pro.limitsQuantity()) {
                    1
                } else {
                    (totalRequestedItems + craftedPerPattern - 1) / craftedPerPattern
                }
                pro.request(inv, times)

                val available = inv.extract(this.what, totalRequestedItems, Actionable.MODULATE)
                if (available != 0L) {
                    totalRequestedItems -= available

                    if (totalRequestedItems <= 0) {
                        return
                    }
                } else {
                    val pattern = pro.details.definition
                    val outputs =
                        Arrays.stream(pro.details.outputs).map { obj: GenericStack? -> obj.toString() }.collect(
                            Collectors.joining(", ")
                        )
                    val errorMessage: String = """
                            Unexpected error in the crafting calculation: can't find created items.
                            This is an AE2 bug, please report it, with the following important information:
                            
                            - Found none of %s. Remaining request: %d of %d*%d.
                            - Tried crafting %d times the pattern %s.
                            - Pattern outputs: %s.
                            
                            """.trimIndent()
                        .format(what, totalRequestedItems, requestedAmount, amount, times, pattern, outputs)
                    throw UnsupportedOperationException(errorMessage)
                }
            }
        } else if (this.nodes!!.size > 1) {
            for (pro in this.nodes!!) {
                try {
                    while (pro.possible && totalRequestedItems > 0) {
                        val child = ChildCraftingSimulationState(inv)
                        pro.request(child, 1)

                        val available = child.extract(this.what, totalRequestedItems, Actionable.MODULATE)

                        if (available != 0L) {
                            child.applyDiff(inv)

                            totalRequestedItems -= available

                            if (totalRequestedItems <= 0) {
                                return
                            }
                        } else {
                            pro.possible = false
                        }
                    }
                } catch (fail: CraftBranchFailure) {
                    pro.possible = true
                }
            }
        }

        if (this.job.isSimulation) {
            job.addMissing(this.what, totalRequestedItems)
        } else {
            throw CraftBranchFailure(this.what, totalRequestedItems)
        }
    }

    private fun addContainerItems(template: AEKey?, multiplier: Long, outputList: KeyCounter?) {
        if (outputList != null) {
            val containerItem = parentInput!!.getRemainingKey(template)
            if (containerItem != null) {
                outputList.add(containerItem, multiplier)
            }
        }
    }

    private fun getValidItemTemplates(inv: ICraftingInventory?): Iterable<InputTemplate> {
        if (this.parentInput == null) return listOf(InputTemplate(what, 1))
        return CraftingCpuHelper.getValidItemTemplates(inv, this.parentInput, level)
    }

    val nodeCount: Long
        get() {
            var tot: Long = 1
            if (this.nodes != null) {
                for (pro in this.nodes) {
                    tot += pro.nodeCount
                }
            }
            return tot
        }

    fun hasMultiplePaths(): Boolean {
        if (this.nodes == null) {
            return false
        }
        if (this.nodes!!.size > 1) {
            return true
        }
        for (pro in this.nodes) {
            if (pro.hasMultiplePaths()) {
                return true
            }
        }
        return false
    }

    val successProbability: Double
        get() {
            if (this.nodes == null || this.nodes!!.isEmpty()) {
                return 1.0
            }
            if (this.nodes!!.size == 1) {
                return this.nodes!![0].successProbability
            }
            var failProb = 1.0
            for (pro in this.nodes) {
                failProb *= (1.0 - pro.successProbability)
            }
            return 1.0 - failProb
        }
}
