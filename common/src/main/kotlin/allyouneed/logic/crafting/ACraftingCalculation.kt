package allyouneed.logic.crafting

import allyouneed.util.bigint.BigStack
import allyouneed.util.logger
import appeng.api.networking.IGrid
import appeng.api.networking.crafting.CalculationStrategy
import appeng.api.networking.crafting.ICraftingPlan
import appeng.api.networking.crafting.ICraftingSimulationRequester
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import appeng.api.stacks.KeyCounter
import appeng.core.AELog
import appeng.crafting.CraftingCalculation
import appeng.crafting.CraftingPlan
import com.google.common.base.Stopwatch
import net.minecraft.world.level.Level
import java.util.concurrent.TimeUnit

/**
 * Custom CraftingCalculation that:
 * 1. Eagerly snapshots ME inventory and crafting patterns into [CraftingInventorySnapshot]
 *    in the constructor on the calling thread.
 * 2. Solves the crafting plan with ojalgo MIP ([MipCraftingPlanner]) on [AE2TaskScheduler]
 *    using only snapshotted data — no live grid storage/pattern access after construction.
 * 3. Does **not** register with TickHandler (full snapshot removes the need for the
 *    original pause/simulateFor handshake).
 */
class ACraftingCalculation(
    private val level: Level?,
    grid: IGrid,
    @JvmField val simRequester: ICraftingSimulationRequester,
    output: GenericStack,
    private val strategy: CalculationStrategy?,
) : CraftingCalculation(level, grid, simRequester, output, strategy) {
    private val missing = KeyCounter()
    private val output: AEKey = output.what()
    private val requestedAmount: Long = output.amount()

    private val snapshot: CraftingInventorySnapshot? = if (level != null) {
        try {
            CraftingInventorySnapshot(level, grid, BigStack.from(GenericStack(this.output, this.requestedAmount)))
        } catch (e: Throwable) {
            logger.error("Failed to snapshot crafting inventory", e)
            null
        }
    } else null

    private var simulate = false

    private var multiplePaths = false

    private var overallSuccessProbability = 1.0

    @Volatile
    private var done = false

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

    private fun computePlan(): ICraftingPlan {
        val snap = snapshot
        if (snap == null) {
            throw IllegalStateException("No crafting inventory snapshot available")
        }

        val planner = MipCraftingPlanner(snap, level, output, requestedAmount)
        val mip = planner.plan()

        this.simulate = mip.simulation
        this.multiplePaths = mip.multiplePaths
        for (entry in mip.missingItems) {
            missing.add(entry.key, entry.longValue)
        }

        val plan = CraftingPlan(
            GenericStack(output, mip.finalAmount),
            mip.bytes,
            mip.simulation,
            mip.multiplePaths,
            mip.usedItems,
            mip.emittedItems,
            mip.missingItems,
            mip.patternTimes,
        )
        this.overallSuccessProbability = if (mip.simulation) 0.0 else 1.0
        return plan
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

    override fun hasMultiplePaths(): Boolean = this.multiplePaths

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

            AELog.crafting(
                "AdaptiveCraftingCalculation issued by %s requesting [%dx%s] -> final plan %d (%d bytes), sim=%b, success=%.4f".format(
                    actionSourceName, this.requestedAmount, this.output,
                    plan.finalOutput().amount(), plan.bytes(),
                    plan.simulation(), this.overallSuccessProbability,
                )
            )
        }
    }
}
