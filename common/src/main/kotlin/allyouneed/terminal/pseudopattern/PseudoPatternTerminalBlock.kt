package allyouneed.terminal

import appeng.block.AEBaseEntityBlock
import appeng.blockentity.grid.AENetworkBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class PseudoPatternTerminalBlock(props: Properties) : AEBaseEntityBlock<PseudoPatternTerminalBlockEntity>(props) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        // We will register the BlockEntityType later; here we just create with a placeholder.
        // In practice the type is set by the BlockEntityType builder.
        // For simplicity we assume a static accessor will be provided by registration.
        return PseudoPatternTerminalBlockEntity(
            allyouneed.terminal.PseudoPatternTerminalRegistration.getBlockEntityType(),
            pos,
            state
        )
    }

    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos)
            if (be is PseudoPatternTerminalBlockEntity) {
                // Open the menu
                appeng.menu.MenuOpener.open(
                    PseudoPatternTerminalMenu.TYPE,
                    player,
                    appeng.menu.locator.MenuLocators.forBlockEntity(be)
                )
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
