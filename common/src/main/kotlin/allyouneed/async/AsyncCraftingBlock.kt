package allyouneed.async

import appeng.api.orientation.IOrientationStrategy
import appeng.api.orientation.OrientationStrategies
import appeng.block.AEBaseEntityBlock
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult

open class AsyncCraftingBlock(
    props: Properties,
    val unitType: AsyncCraftingUnitType,
) : AEBaseEntityBlock<AsyncCraftingBlockEntity>(props) {

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FORMED, false),
        )
    }

    override fun getOrientationStrategy(): IOrientationStrategy {
        return OrientationStrategies.none()
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FORMED)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        val beType = AsyncCraftingRegistration.getBlockEntityType(unitType.role)
        return if (unitType.role == AsyncCraftingUnitRole.CONNECTOR) {
            AsyncCraftingConnectorBlockEntity(beType, pos, state)
        } else {
            AsyncCraftingBlockEntity(beType, pos, state)
        }
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
        if (be is IAsyncCraftingBlockEntity) {
            be.updateMultiBlock(fromPos)
        }
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (newState.block === state.block) {
            return
        }
        val be = level.getBlockEntity(pos)
        if (be is IAsyncCraftingBlockEntity) {
            be.disconnect(true)
        }
        super.onRemove(state, level, pos, newState, isMoving)
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
        if (be is AsyncCraftingBlockEntity && be.unitType.role == AsyncCraftingUnitRole.HOST) {
            if (!level.isClientSide) {
                MenuOpener.open(AsyncCraftingStatusMenu.TYPE, player, MenuLocators.forBlockEntity(be))
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    companion object {
        val FORMED: BooleanProperty = BooleanProperty.create("formed")
        val POWERED: BooleanProperty = BooleanProperty.create("powered")
    }
}

open class AsyncCraftingOrientableBlock(
    props: Properties,
    unitType: AsyncCraftingUnitType,
) : AsyncCraftingBlock(props, unitType) {

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FORMED, false),
        )
    }

    override fun getOrientationStrategy(): IOrientationStrategy {
        return OrientationStrategies.horizontalFacing()
    }
}

class AsyncCraftingConnectorBlock(
    props: Properties,
    unitType: AsyncCraftingUnitType,
) : AsyncCraftingOrientableBlock(props, unitType) {

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
}
