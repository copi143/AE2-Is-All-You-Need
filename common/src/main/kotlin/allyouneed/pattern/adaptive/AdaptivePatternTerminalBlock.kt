package allyouneed.pattern.adaptive

import appeng.block.AEBaseEntityBlock
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuLocators
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class AdaptivePatternTerminalBlock(props: Properties) : AEBaseEntityBlock<AdaptivePatternTerminalBlockEntity>(props) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return AdaptivePatternTerminalBlockEntity(
            AdaptivePatternTerminalRegistration.getBlockEntityType(), pos, state
        )
    }

    override fun use(
        state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos)
            if (be is AdaptivePatternTerminalBlockEntity) {
                MenuOpener.open(
                    AdaptivePatternTerminalMenu.TYPE,
                    player,
                    MenuLocators.forBlockEntity(be),
                )
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
