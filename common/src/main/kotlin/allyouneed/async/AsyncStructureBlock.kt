package allyouneed.async

import appeng.api.orientation.IOrientationStrategy
import appeng.api.orientation.OrientationStrategies
import appeng.block.AEBaseEntityBlock
import appeng.blockentity.AEBaseBlockEntity
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuLocators
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult

/**
 * Plain structural block of the async synthesis system (frame, machine block, glass, reinforced
 * tower, dedicated cable and the energy/computing/storage/execution cores). It has no block
 * entity and no orientation; the multiblock detectors read it directly from the world.
 */
class AsyncStructureBlock(
    override val kind: AsyncBlockKind,
    props: Properties,
) : Block(props), IAsyncKindBlock

/**
 * Shared base of the entity blocks: controllers (network controller, network switch, factory) and
 * the module interface (Z). They face a horizontal direction and carry a [FORMED] block state that
 * the structure detectors set once the surrounding multiblock is recognized.
 */
abstract class AsyncStructureEntityBlock<T : AEBaseBlockEntity>(
    override val kind: AsyncBlockKind,
    props: Properties,
) : AEBaseEntityBlock<T>(props), IAsyncKindBlock {

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FORMED, false),
        )
    }

    override fun getOrientationStrategy(): IOrientationStrategy {
        return OrientationStrategies.horizontalFacing()
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FORMED)
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        blockIn: Block,
        fromPos: BlockPos,
        isMoving: Boolean,
    ) {
        val be = level.getBlockEntity(pos)
        when (be) {
            is AsyncStructureBlockEntity -> be.updateMultiBlock(fromPos)
            is AsyncStructureConnectorBlockEntity -> be.updateMultiBlock(fromPos)
            else -> {}
        }
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (newState.block === state.block) {
            return
        }
        val be = level.getBlockEntity(pos)
        when (be) {
            is AsyncStructureBlockEntity -> be.disconnect()
            is AsyncStructureConnectorBlockEntity -> be.disconnect()
            else -> {}
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }

    companion object {
        val FORMED: BooleanProperty = BooleanProperty.create("formed")
        val POWERED: BooleanProperty = BooleanProperty.create("powered")
    }
}

/** Controllers of the three structures: network controller (processor), network switch, factory. */
class AsyncStructureControllerBlock(
    kind: AsyncBlockKind,
    props: Properties,
) : AsyncStructureEntityBlock<AsyncStructureBlockEntity>(kind, props) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return AsyncStructureBlockEntity(AsyncCraftingRegistration.getStructureBlockEntityType(), pos, state)
    }

    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        val be = level.getBlockEntity(pos)
        if (be is AsyncStructureBlockEntity) {
            if (!level.isClientSide) {
                MenuOpener.open(AsyncCraftingStatusMenu.TYPE, player, MenuLocators.forBlockEntity(be))
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
        return super.use(state, level, pos, player, hand, hit)
    }
}

/** Module interface (Z): the mounting point on which an async synthesis module is built. */
class AsyncStructureInterfaceBlock(
    kind: AsyncBlockKind,
    props: Properties,
) : AsyncStructureEntityBlock<AsyncStructureBlockEntity>(kind, props) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return AsyncStructureBlockEntity(AsyncCraftingRegistration.getStructureBlockEntityType(), pos, state)
    }
}

/**
 * Grid-connected connector (ME / WAN / LAN). Additionally carries a [POWERED] state reflecting its
 * grid node online state.
 */
class AsyncStructureConnectorBlock(
    kind: AsyncBlockKind,
    props: Properties,
) : AsyncStructureEntityBlock<AsyncStructureConnectorBlockEntity>(kind, props) {

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FORMED, false)
                .setValue(POWERED, false),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(POWERED)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return AsyncStructureConnectorBlockEntity(
            AsyncCraftingRegistration.getStructureConnectorBlockEntityType(),
            pos,
            state,
        )
    }
}
