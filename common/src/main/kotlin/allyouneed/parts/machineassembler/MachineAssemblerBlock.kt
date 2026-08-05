package allyouneed.parts.machineassembler

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
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult

class MachineAssemblerBlock(props: Properties) : AEBaseEntityBlock<MachineAssemblerBlockEntity>(props) {

    companion object {
        val POWERED: BooleanProperty = BooleanProperty.create("powered")
    }

    init {
        registerDefaultState(defaultBlockState().setValue(POWERED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(POWERED)
    }

    override fun updateBlockStateFromBlockEntity(
        currentState: BlockState,
        be: MachineAssemblerBlockEntity
    ): BlockState {
        return currentState.setValue(POWERED, be.isPowered())
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return MachineAssemblerBlockEntity(MachineAssemblerRegistration.getBlockEntityType(), pos, state)
    }

    override fun onActivated(
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        heldItem: ItemStack?,
        hit: BlockHitResult,
    ): InteractionResult {
        val be = getBlockEntity(level, pos)
        if (be != null) {
            if (!InteractionUtil.isInAlternateUseMode(player)) {
                if (!level.isClientSide) {
                    MenuOpener.open(MachineAssemblerMenu.TYPE, player, MenuLocators.forBlockEntity(be))
                }
                return InteractionResult.sidedSuccess(level.isClientSide)
            }
        }
        return InteractionResult.PASS
    }
}
