package allyouneed.iodrive

import appeng.block.AEBaseEntityBlock
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuLocators
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class MEIODriveBlock(props: Properties) : AEBaseEntityBlock<MEIODriveBlockEntity>(props) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return MEIODriveBlockEntity(
            MEIODriveRegistration.getBlockEntityType(), pos, state
        )
    }

    override fun use(
        state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos)
            if (be is MEIODriveBlockEntity) {
                if (player.isShiftKeyDown) {
                    val newMode = be.getMode().next()
                    be.setMode(newMode)
                    player.displayClientMessage(
                        Component.literal("ME IO Drive: ${newMode.label}"), true
                    )
                } else {
                    MenuOpener.open(MEIODriveMenu.TYPE, player, MenuLocators.forBlockEntity(be))
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
