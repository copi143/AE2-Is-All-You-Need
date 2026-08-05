package allyouneed.logic.crafting

import appeng.api.networking.IGrid
import appeng.api.networking.crafting.CalculationStrategy
import appeng.api.networking.crafting.ICraftingPlan
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
import appeng.crafting.inv.NetworkCraftingSimulationState
import appeng.hooks.ticking.TickHandler
import com.google.common.base.Stopwatch
import net.minecraft.world.level.Level
import org.jetbrains.annotations.Contract
import java.util.concurrent.TimeUnit

/**
 * Custom CraftingCalculation that uses ACraftingTreeNode/ACraftingTreeProcess
 * for adaptive probability patterns (ceil(N/p) scaling).
 */
class ACraftingCalculation(
    private val level: Level?,
    grid: IGrid,
    @JvmField val simRequester: ICraftingSimulationRequester,
    output: GenericStack,
    private val strategy: CalculationStrategy?
) : CraftingCalculation(level, grid, simRequester, output, strategy) {
    private val missing = KeyCounter()
    private val monitor = Any()
    private val watch: Stopwatch = Stopwatch.createUnstarted()
    private val output: AEKey = output.what()
    private val requestedAmount: Long = output.amount()
    private val attempts: MutableList<CraftAttempt>? =
        if (AELog.isCraftingLogEnabled()) ArrayList<CraftAttempt>() else null
    private val networkInv: NetworkCraftingSimulationState =
        NetworkCraftingSimulationState(grid.storageService, simRequester.actionSource)
    private val tree: ACraftingTreeNode = ACraftingTreeNode(grid.craftingService, this, this.output, 1, null, -1)
    private var simulate = false
    private var running = false
    private var done = false
    private var time = 5
    private var incTime = Int.MAX_VALUE
    private var overallSuccessProbability = 1.0

    fun addMissing(what: AEKey, amount: Long) {
        missing.add(what, amount)
    }

    override fun run(): ICraftingPlan {
        try {
            TickHandler.instance().registerCraftingSimulation(this.level, this)
            this.handlePausing()

            val plan: ICraftingPlan = computePlan()
            this.logCraftingJob(plan)
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

    @Throws(InterruptedException::class)
    fun handlePausing() {
        if (this.incTime > 100) {
            this.incTime = 0

            synchronized(this.monitor) {
                if (this.watch.elapsed(TimeUnit.MICROSECONDS) > this.time) {
                    this.running = false
                    this.watch.stop()
                    (this.monitor as Object).notify()
                }
                if (!this.running) {
                    AELog.craftingDebug("crafting job will now sleep")

                    while (!this.running) {
                        (this.monitor as Object).wait()
                    }

                    AELog.craftingDebug("crafting job now active")
                }
            }

            if (Thread.interrupted()) {
                throw InterruptedException()
            }
        }
        this.incTime++
    }

    private fun finish() {
        synchronized(this.monitor) {
            this.running = false
            this.done = true
            (this.monitor as Object).notify()
        }
    }

    override fun isSimulation(): Boolean {
        return this.simulate
    }

    override fun getOutput(): AEKey {
        return output
    }

    override fun getMissingItems(): KeyCounter {
        return missing
    }

    fun getLevel(): Level? {
        return this.level
    }

    override fun simulateFor(micros: Int): Boolean {
        this.time = micros

        synchronized(this.monitor) {
            if (this.done) {
                return false
            }
            this.watch.reset()
            this.watch.start()
            this.running = true

            AELog.craftingDebug("main thread is now going to sleep")

            (this.monitor as Object).notify()

            while (this.running) {
                try {
                    this.monitor.wait()
                } catch (ignored: InterruptedException) {
                }
            }
            AELog.craftingDebug("main thread is now active")
        }

        return true
    }

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

    override fun hasMultiplePaths(): Boolean {
        return this.tree.hasMultiplePaths()
    }

    @JvmRecord
    private data class CraftAttempt(val description: String?, val stopwatch: Stopwatch?)
}
