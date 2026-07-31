package allyouneed.cell

import appeng.api.config.Actionable
import appeng.api.networking.IGridNode
import appeng.api.networking.ticking.TickRateModulation
import appeng.api.networking.ticking.TickingRequest
import appeng.blockentity.networking.EnergyCellBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * Energy cell that passively regenerates [getAEMaxPower] / 1024 AE per game tick until full.
 */
class SelfPoweredEnergyCellBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : EnergyCellBlockEntity(type, pos, state) {

    override fun getTickingRequest(node: IGridNode): TickingRequest {
        // Always awake, every tick (parent sleeps after neighbor updates).
        return TickingRequest(1, 1, false, true)
    }

    override fun tickingRequest(node: IGridNode, ticksSinceLastCall: Int): TickRateModulation {
        // Preserve parent neighbor/comparator update behavior.
        super.tickingRequest(node, ticksSinceLastCall)

        val max = aeMaxPower
        val current = aeCurrentPower
        if (current < max && max > 0) {
            val ticks = ticksSinceLastCall.coerceAtLeast(1)
            val amount = max / 1024.0 * ticks
            injectAEPower(amount, Actionable.MODULATE)
        }
        return TickRateModulation.SAME
    }
}
