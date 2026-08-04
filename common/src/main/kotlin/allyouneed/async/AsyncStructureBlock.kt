package allyouneed.async

import appeng.api.orientation.IOrientationStrategy
import appeng.api.orientation.OrientationStrategies
import appeng.block.AEBaseEntityBlock
import appeng.blockentity.AEBaseBlockEntity
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuLocators
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult

/**
 * async 合成系统的普通结构方块（框架、机器方块、玻璃、强化塔、专用线缆，
 * 以及能量/计算/存储/执行核心）。它没有方块实体、没有朝向；多方块检测器
 * 直接从世界中读取它。所有结构方块都带有一个 [FORMED] 状态，检测器在识别出
 * 周围结构后设置它，让整座多方块亮起来。
 *
 * Plain structural block of the async synthesis system (frame, machine block, glass, reinforced
 * tower, dedicated cable and the energy/computing/storage/execution cores). It has no block
 * entity and no orientation; the multiblock detectors read it directly from the world. All
 * structural blocks carry a [FORMED] state that the detectors set once the surrounding structure
 * is recognized, lighting the whole multiblock up.
 */
open class AsyncStructureBlock(
    override val kind: AsyncBlockKind,
    props: Properties,
) : Block(props), IAsyncKindBlock {

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(AsyncStructureEntityBlock.FORMED, false),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(AsyncStructureEntityBlock.FORMED)
    }

    @Deprecated("Deprecated in Java")
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        notifyStructureChanged(level, pos)
    }

    @Deprecated("Deprecated in Java")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        super.onRemove(state, level, pos, newState, isMoving)
        notifyStructureChanged(level, pos)
    }

    /**
     * 结构方块没有方块实体，所以它被放置或移除时，没有别的东西会重新校验周围
     * 的多方块。请附近控制器重新校验。
     *
     * Structural blocks carry no block entity, so nothing else revalidates the surrounding
     * multiblock when one is placed or removed. Ask the nearby controllers to revalidate.
     */
    private fun notifyStructureChanged(level: Level, pos: BlockPos) {
        if (level is ServerLevel) {
            AsyncStructureNotifier.onStructuralBlockChanged(level, pos)
        }
    }
}

/**
 * async 机器框架，带 ME 控制器风格的连接贴图。它带有一个 [CONNECTIONS] 掩码
 * （每个方向一位，当该方向的邻居是另一块框架时置位），客户端用它挑选每面的
 * c/h/v 贴图；同时共享结构的 [FORMED] 状态。掩码在放置时和任何邻居变化时重算。
 *
 * Async machine frame with ME-controller-style connection textures. Carries a [CONNECTIONS] mask
 * (one bit per direction, set when the neighbour in that direction is another frame) that the
 * client uses to pick the per-face c/h/v texture, plus the shared structural [FORMED] state. The
 * mask is recomputed on placement and on any neighbour change.
 */
class AsyncStructureFrameBlock(
    kind: AsyncBlockKind,
    props: Properties,
) : AsyncStructureBlock(kind, props) {

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(AsyncStructureEntityBlock.FORMED, false)
                .setValue(CONNECTIONS, 0),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(CONNECTIONS)
    }

    @Deprecated("Deprecated in Java")
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        if (!level.isClientSide) {
            refreshConnections(level, pos)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        blockIn: Block,
        fromPos: BlockPos,
        isMoving: Boolean,
    ) {
        super.neighborChanged(state, level, pos, blockIn, fromPos, isMoving)
        if (!level.isClientSide) {
            refreshConnections(level, pos)
        }
    }

    private fun refreshConnections(level: Level, pos: BlockPos) {
        var mask = 0
        for ((i, dir) in Direction.entries.withIndex()) {
            val kind = (level.getBlockState(pos.relative(dir)).block as? IAsyncKindBlock)?.kind
            if (kind == AsyncBlockKind.FRAME) {
                mask = mask or (1 shl i)
            }
        }
        val state = level.getBlockState(pos)
        if (state.hasProperty(CONNECTIONS) && state.getValue(CONNECTIONS) != mask) {
            level.setBlock(pos, state.setValue(CONNECTIONS, mask), UPDATE_CLIENTS)
        }
    }

    companion object {
        val CONNECTIONS: IntegerProperty = IntegerProperty.create("connections", 0, 63)
    }
}

/**
 * 翻转给定边界内每个结构方块（[AsyncStructureBlock]：框架、机器方块、玻璃、塔、
 * 核心、线缆）的 [AsyncStructureEntityBlock.FORMED] 状态。结构计算器（自有方块与
 * GT）在结构成形/失效时调用它，让整座多方块亮起来。控制器/连接器/接口不受影响——
 * 它们自己管理成形状态。
 *
 * 该状态被直接写入 chunk，并附带一个客户端方块更新包，而不是走 [Level.setBlock]，
 * 因此整座结构翻转 FORMED 不会逐方块触发 onPlace/onRemove 或 GTCEu LevelMixin 的
 * 重新检测。FORMED 纯粹是外观性的，检测器在服务端从不读取它，跳过方块事件是安全的。
 *
 * Flips the [AsyncStructureEntityBlock.FORMED] state of every structural block (an
 * [AsyncStructureBlock]: frame, machine block, glass, tower, cores, cable) inside the given bounds.
 * The structure calculators (plain and GT) call this when a structure forms/invalidates so the
 * whole multiblock lights up. Controllers/connectors/interfaces are not touched - they manage their
 * own formed state.
 *
 * The state is written straight into the chunk plus a client block-update packet instead of
 * [Level.setBlock], so flipping FORMED over a whole structure does not fire onPlace/onRemove or the
 * GTCEu LevelMixin recheck once per block. FORMED is purely cosmetic and never read server-side by
 * the detectors, so skipping the block events is safe.
 */
fun setStructuralFormed(level: ServerLevel, min: BlockPos, max: BlockPos, formed: Boolean) {
    for (y in min.y..max.y) {
        for (z in min.z..max.z) {
            for (x in min.x..max.x) {
                val pos = BlockPos(x, y, z)
                val state = level.getBlockState(pos)
                if (state.block is AsyncStructureBlock) {
                    val newState = state.setValue(AsyncStructureEntityBlock.FORMED, formed)
                    if (state != newState) {
                        level.getChunkAt(pos).setBlockState(pos, newState, false)
                        level.sendBlockUpdated(pos, state, newState, Block.UPDATE_CLIENTS)
                    }
                }
            }
        }
    }
}

/**
 * 实体方块的公共基类：控制器（网络控制器、网络交换机、工厂）与模块接口（Z）。
 * 它们朝向一个水平方向，并带有一个 [FORMED] 方块状态，由结构检测器在识别出
 * 周围多方块后设置。
 *
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
        when (val be = level.getBlockEntity(pos)) {
            is AsyncStructureBlockEntity -> be.updateMultiBlock(fromPos)
            is AsyncStructureConnectorBlockEntity -> be.updateMultiBlock(fromPos)
            else -> {}
        }
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (newState.block === state.block) {
            return
        }
        when (val be = level.getBlockEntity(pos)) {
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

/** 三种结构的控制器：网络控制器（处理器）、网络交换机、工厂。 / Controllers of the three structures: network controller (processor), network switch, factory. */
class AsyncStructureControllerBlock(
    kind: AsyncBlockKind,
    props: Properties,
) : AsyncStructureEntityBlock<AsyncStructureBlockEntity>(kind, props) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
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

/** 模块接口（Z）：async 合成模块的安装点。 / Module interface (Z): the mounting point on which an async synthesis module is built. */
class AsyncStructureInterfaceBlock(
    kind: AsyncBlockKind,
    props: Properties,
) : AsyncStructureEntityBlock<AsyncStructureBlockEntity>(kind, props) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return AsyncStructureBlockEntity(AsyncCraftingRegistration.getStructureBlockEntityType(), pos, state)
    }
}

/**
 * 已接入网格的连接器（ME / WAN / LAN）。额外带有一个 [POWERED] 状态，反映其
 * 网格节点的在线状态。
 *
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

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return AsyncStructureConnectorBlockEntity(
            AsyncCraftingRegistration.getStructureConnectorBlockEntityType(),
            pos,
            state,
        )
    }
}
