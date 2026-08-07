package allyouneed.cell

import allyouneed.Platform
import appeng.api.config.Actionable
import appeng.blockentity.networking.EnergyCellBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

object LightningRodCharge {
    val gtceuLoaded = Platform.isModLoaded("gtceu")

    @JvmStatic
    fun onLightningStrike(state: BlockState, level: Level, rodPos: BlockPos) {
        if (level.isClientSide || !gtceuLoaded) return
        val facing = state.getValue(BlockStateProperties.FACING)
        val cell = level.getBlockEntity(rodPos.relative(facing.opposite)) as? EnergyCellBlockEntity ?: return
        cell.injectAEPower(Double.MAX_VALUE, Actionable.MODULATE)
    }
}
