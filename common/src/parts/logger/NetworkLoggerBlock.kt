package allyouneed.parts.logger

import appeng.block.AEBaseEntityBlock
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuLocators
import appeng.util.InteractionUtil
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class NetworkLoggerBlock(props: Properties) : AEBaseEntityBlock<NetworkLoggerBlockEntity>(props) {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return NetworkLoggerBlockEntity(NetworkLoggerRegistration.getBlockEntityType(), pos, state)
    }

    override fun onActivated(
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        heldItem: ItemStack?,
        hit: BlockHitResult,
    ): InteractionResult {
        val be = getBlockEntity(level, pos) ?: return InteractionResult.PASS
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS
        }
        if (!level.isClientSide) {
            MenuOpener.open(NetworkLoggerMenu.TYPE, player, MenuLocators.forBlockEntity(be))
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
