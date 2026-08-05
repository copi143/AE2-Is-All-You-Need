package allyouneed.logic.crafting

import allyouneed.logic.AE2TaskScheduler
import appeng.api.networking.IGrid
import appeng.api.networking.crafting.CalculationStrategy
import appeng.api.networking.crafting.ICraftingPlan
import appeng.api.networking.crafting.ICraftingService
import appeng.api.networking.crafting.ICraftingSimulationRequester
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import appeng.api.stacks.KeyCounter
import appeng.core.AELog
import appeng.crafting.CraftBranchFailure
import appeng.crafting.CraftingCalculation
import appeng.crafting.CraftingPlan
import appeng.crafting.inv.ChildCraftingSimulationState
import appeng.crafting.inv.CraftingSimulationState
import com.google.common.base.Stopwatch
import net.minecraft.world.level.Level
import org.jetbrains.annotations.Contract
import java.util.concurrent.TimeUnit

/**
 * Custom CraftingCalculation that:
 * 1. Eagerly **value-copies** ME inventory ([MeInventorySnapshot]) and crafting patterns
 *    ([CachedCraftingService]) in the constructor on the calling thread.
 * 2. Runs tree computation on [AE2TaskScheduler] using only snapshotted data —
 *    no live grid storage/pattern access after construction.
 * 3. Does **not** register with TickHandler (full snapshot removes the need for the
 *    original pause/simulateFor handshake).
 */
class ACraftingCalculation(
    private val level: Level?,
    grid: IGrid,
    @JvmField val simRequester: ICraftingSimulationRequester,
    output: GenericStack,
    private val strategy: CalculationStrategy?
) : CraftingCalculation(level, grid, simRequester, output, strategy) {
    private val missing = KeyCounter()
    private val output: AEKey = output.what()
    private val requestedAmount: Long = output.amount()
    private val attempts: MutableList<CraftAttempt>? =
        if (AELog.isCraftingLogEnabled()) ArrayList() else null

    private val cachedPatterns: ICraftingService = CachedCraftingService(grid.craftingService)

    private val networkInv: CopiedNetworkSimulationState =
        CopiedNetworkSimulationState(
            MeInventorySnapshot.copy(grid.storageService, simRequester.actionSource)
        )

    private val tree: ACraftingTreeNode = ACraftingTreeNode(cachedPatterns, this, this.output, 1, null, -1)

    private var simulate = false

    @Volatile
    private var done = false

    private var incTime = Int.MAX_VALUE

    private var overallSuccessProbability = 1.0

    fun addMissing(what: AEKey, amount: Long) {
        missing.add(what, amount)
    }

    override fun run(): ICraftingPlan {
        val timer = Stopwatch.createStarted()
        AELog.debug(
            "ACraftingCalculation start: %dx%s on %s",
            requestedAmount,
            output,
            Thread.currentThread().name,
        )
        try {
            val plan: ICraftingPlan = computePlan()
            this.logCraftingJob(plan)
            AELog.debug(
                "ACraftingCalculation done: %dx%s in %d ms (%d bytes, sim=%s)",
                plan.finalOutput().amount(),
                plan.finalOutput().what(),
                timer.elapsed(TimeUnit.MILLISECONDS),
                plan.bytes(),
                plan.simulation(),
            )
            return plan
        } catch (ex: Exception) {
            AELog.info(ex, "Exception during crafting calculation.")
            throw RuntimeException(ex)
        } finally {
            this.finish()
        }
    }

    @Throws(InterruptedException::class)
    private fun computePlan(): ICraftingPlan {
        val fullAmountPlan: CraftingPlan? = runCraftAttempt(false, requestedAmount)
        if (fullAmountPlan != null) {
            return fullAmountPlan
        }

        if (strategy == CalculationStrategy.CRAFT_LESS) {
            var successfulAmount: Long = 0
            var successfulPlan: ICraftingPlan? = null
            var increment = requestedAmount.takeHighestOneBit()
            while (increment > 0) {
                val testAmount = successfulAmount + increment
                if (testAmount < requestedAmount) {
                    val plan: CraftingPlan? = runCraftAttempt(false, testAmount)
                    if (plan != null) {
                        successfulAmount = testAmount
                        successfulPlan = plan
                    }
                }
                increment /= 2
            }

            if (successfulPlan != null) {
                return successfulPlan
            }
        }

        return runCraftAttempt(true, requestedAmount)!!
    }

    @Contract("true, _ -> !null")
    @Throws(InterruptedException::class)
    private fun runCraftAttempt(simulate: Boolean, amount: Long): CraftingPlan? {
        this.simulate = simulate

        val timer = Stopwatch.createStarted()

        val craftingInventory = ChildCraftingSimulationState(networkInv)
        craftingInventory.ignore(this.output)

        try {
            this.tree.request(craftingInventory, amount, null)
        } catch (failure: CraftBranchFailure) {
            if (AELog.isCraftingLogEnabled()) {
                this.attempts!!.add(CraftAttempt("$amount failed", timer))
            }
            return null
        }
        craftingInventory.addBytes((this.tree.nodeCount * 8).toDouble())

        val plan = CraftingSimulationState.buildCraftingPlan(craftingInventory, this, amount)
        this.overallSuccessProbability = this.tree.successProbability
        if (AELog.isCraftingLogEnabled()) {
            val type = if (simulate) "simulated" else "succeeded"
            this.attempts!!.add(CraftAttempt("%d %s (%d bytes)".format(amount, type, plan.bytes()), timer))
        }
        return plan
    }

    /**
     * Cancellation checkpoint. Full inventory/pattern snapshots mean we no longer need
     * the original monitor wait/notify pause for live grid updates.
     */
    @Throws(InterruptedException::class)
    fun handlePaUSING() {
        if (this.incTime > 100) {
            this.incTime = 0
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            Thread.yield()
        }
        this.incTime++
    }

    private fun finish() {
        this.done = true
    }

    override fun isSimulation(): Boolean = this.simulate

    override fun getOutput(): AEKey = output

    override fun getMissingItems(): KeyCounter = missing

    fun getLevel(): Level? = this.level

    /**
     * Not registered with TickHandler; kept for API compatibility if anything polls us.
     * @return true while the job is still running
     */
    override fun simulateFor(micros: Int): Boolean = !this.done

    override fun hasMultiplePaths(): Boolean = this.tree.hasMultiplePaths()

    private fun logCraftingJob(plan: ICraftingPlan) {
        if (AELog.isCraftingLogEnabled()) {
            val actionSource = this.simRequester.actionSource
            val actionSourceName: String?

            if (actionSource != null && actionSource.player().isPresent) {
                val player = actionSource.player().get()
                actionSourceName = player.toString()
            } else if (actionSource != null && actionSource.machine().isPresent) {
                val machineSource = actionSource.machine().get()
                val actionableNode = machineSource.actionableNode
                actionSourceName = actionableNode?.toString() ?: machineSource.toString()
            } else {
                actionSourceName = "[unknown source]"
            }

            val message = StringBuilder()
            message.append(
                "AdaptiveCraftingCalculation issued by %s requesting [%dx%s] breakdown:\n".format(
                    actionSourceName, this.requestedAmount, this.output
                )
            )
            for (attempt in this.attempts!!) {
                message.append(
                    " - %s in %d ms\n".format(
                        attempt.description, attempt.stopwatch!!.elapsed(TimeUnit.MILLISECONDS)
                    )
                )
            }
            message.append(" - final plan: %d (%d bytes)".format(plan.finalOutput().amount(), plan.bytes()))
            message.append("\n - overall success probability: %.4f".format(this.overallSuccessProbability))

            AELog.crafting(message.toString())
        }
    }

    @JvmRecord
    private data class CraftAttempt(val description: String?, val stopwatch: Stopwatch?)
}
