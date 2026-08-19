package allyouneed.gtceu.multiblock

import allyouneed.multiblock.async.AsyncBlockKind
import allyouneed.multiblock.async.AsyncModuleCluster
import allyouneed.multiblock.async.AsyncProcessorCluster
import allyouneed.multiblock.async.AsyncStructureDetector
import allyouneed.multiblock.async.AsyncStructureEntityBlock
import allyouneed.multiblock.async.AsyncSwitchCluster
import allyouneed.multiblock.async.IAsyncChannelView
import allyouneed.multiblock.async.setStructuralFormed
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuLocators
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine
import com.gregtechceu.gtceu.api.pattern.MultiblockState
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer
import com.gregtechceu.gtceu.config.ConfigHolder
import net.minecraft.core.BlockPos
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * async 合成结构的 GTCEu 多方块控制器。
 *
 * 交换机/处理器通过 GTCEu **原生** [com.gregtechceu.gtceu.api.pattern.BlockPattern]
 * 检查成形（尾部扩展舱是一个 6-aisle 组，通过 [IGroupedBlockPattern] mixin 重复
 * 0..16 次）。模式检查会把每个必需格子填进 [MultiblockState] 的位置缓存；簇摘要
 * （[AsyncSwitchCluster]/[AsyncProcessorCluster]）只从该缓存重建——交换机/处理器
 * 成形从不运行检测器。
 *
 * 工厂（模块）是例外：模块锚定在其地板接口（Z）上，GT 模式无法表达这种关系，
 * 所以它保留检测器路径（[AsyncStructureDetector.detectModule]）。
 *
 * GTCEu multiblock controller of the async synthesis structures.
 *
 * The switch/processor form through GTCEu's **native** [com.gregtechceu.gtceu.api.pattern.BlockPattern]
 * check (the trailing extension bays are a 6-aisle group repeated 0..16 times via the
 * [IGroupedBlockPattern] mixin). The pattern check fills the [MultiblockState] position cache with
 * every required cell; the cluster summary ([AsyncSwitchCluster]/[AsyncProcessorCluster]) is rebuilt
 * from that cache alone - the detector is never run for switch/processor forming.
 *
 * The factory (module) is the exception: a module is anchored at its floor interface (Z), which a GT
 * pattern cannot express, so it keeps the detector path ([AsyncStructureDetector.detectModule]).
 */
abstract class AsyncStructureGtControllerMachine(
    holder: IMachineBlockEntity,
) : MultiblockControllerMachine(holder), IInteractedMachine, IMachineLife {

    /** 工厂为 true，它通过模块接口探测成形，而不是 GT 模式。 / True for the factory, which forms by module interface probing instead of a GT pattern. */
    protected open val usesDetector: Boolean = false

    /** 最近一次检查的检测结果，由 [rebuildCluster] 消费（仅工厂）。 / Detector result of the most recent check, consumed by [rebuildCluster] (factory only). */
    private var detection: Any? = null

    /** 已成形结构的活动簇（模块 / 交换机 / 处理器）。 / The live cluster of the formed structure (module / switch / processor). */
    private var cluster: Any? = null

    // ---------------------------------------------------------------------------------------------
    // Pattern checking
    // ---------------------------------------------------------------------------------------------

    override fun checkPattern(): Boolean {
        if (!usesDetector) {
            val pattern = getPattern()
            return pattern != null && pattern.checkPatternAt(multiblockState, false)
        }
        val level = level as? ServerLevel ?: return false
        val detected = detect(level)
        detection = detected
        val state = multiblockState
        if (detected == null) {
            state.setError(MultiblockState.UNINIT_ERROR)
            return false
        }
        state.clean()
        for (pos in cachePositions(detected)) {
            state.addPosCache(pos)
        }
        state.setError(null)
        return true
    }

    /**
     * 交换机/处理器使用 GTCEu 默认方式：模式检查在 async 线程上运行，成形在主线程
     * 上进行。工厂保留主线程延迟，因为检测器直接读世界，绝不能在子线程上跑。
     *
     * The switch/processor use GTCEu's default: the pattern check runs on the async thread, forming
     * happens on the main thread. The factory keeps the main-thread deferral because the detector
     * reads the world directly and must not run off-thread.
     */
    override fun asyncCheckPattern(periodID: Long) {
        if (!usesDetector) {
            super.asyncCheckPattern(periodID)
            return
        }
        val level = level as? ServerLevel ?: return
        if (multiblockState.hasError() || !isFormed) {
            if ((offsetTimer + periodID) % 4L == 0L) {
                level.server.tell(TickTask(0) {
                    patternLock.lock()
                    try {
                        if (checkPatternWithLock()) {
                            setFlipped(false)
                            onStructureFormed()
                            val mwsd = MultiblockWorldSavedData.getOrCreate(level)
                            mwsd.addMapping(multiblockState)
                            mwsd.removeAsyncLogic(this)
                        }
                    } finally {
                        patternLock.unlock()
                    }
                })
            }
        }
    }

    /** 以控制器位置为锚点，运行本结构种类的检测器。 / Runs the detector for this structure kind, anchored at the controller's position. */
    protected open fun detect(level: ServerLevel): Any? = null

    /**
     * 宿主控制器自己没有结构方块状态，所以 GTCEu 的方块状态钩子无法判断它锚定的
     * 结构已经消失。宿主被移除时，把结构整体拆掉（熄灭其余方块、解链连接器）。
     *
     * The host controller carries no structural block state of its own, so GTCEu's block-state
     * hook cannot tell that the structure it anchored is gone. When the host is removed, tear the
     * structure down (unlight the remaining blocks, unlink the connectors).
     */
    override fun onMachineRemoved() {
        val level = level as? ServerLevel ?: return
        if (isFormed) {
            onStructureInvalid()
            MultiblockWorldSavedData.getOrCreate(level).removeMapping(multiblockState)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Structure lifecycle
    // ---------------------------------------------------------------------------------------------

    override fun onStructureFormed() {
        super.onStructureFormed()
        updateFormedBlockState(true)
        val level = level as? ServerLevel ?: return
        // GTCEu re-runs onStructureFormed on every block change at a cached position, and the
        // connector FORMED flip below setBlocks such a position. Once the cluster is built, the
        // structure content is unchanged, so skip the rebuild to break that re-entrancy.
        if (cluster != null) return
        rebuildCluster(level)
    }

    override fun onStructureInvalid() {
        super.onStructureInvalid()
        updateFormedBlockState(false)
        val level = level as? ServerLevel
        if (level != null) {
            destroyCluster(level)
        }
        detection = null
    }

    /** 与普通 async 方块一致：翻转 FORMED 方块状态，不通知邻居。 / Mirrors the vanilla async blocks: flip the FORMED block state without notifying neighbours. */
    private fun updateFormedBlockState(formed: Boolean) {
        val level = level as? ServerLevel ?: return
        val current = level.getBlockState(pos)
        if (current.block !is AsyncStructureGtMachineBlock) return
        val newState = current.setValue(AsyncStructureEntityBlock.FORMED, formed)
        if (current != newState) {
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS)
        }
    }

    private fun rebuildCluster(level: ServerLevel) {
        destroyCluster(level)
        val newCluster = buildCluster(level) ?: return
        cluster = newCluster
        for (pos in connectorPositionsOf(newCluster)) {
            (getMachine(level, pos) as? AsyncStructureGtConnectorMachine)?.setHostController(this)
        }
        val (min, max) = boundsOf(newCluster)
        setStructuralFormed(level, min, max, true)
    }

    private fun destroyCluster(level: ServerLevel) {
        val old = cluster ?: return
        cluster = null
        for (pos in connectorPositionsOf(old)) {
            (getMachine(level, pos) as? AsyncStructureGtConnectorMachine)?.setHostController(null)
        }
        val (min, max) = boundsOf(old)
        setStructuralFormed(level, min, max, false)
    }

    /**
     * 构建簇摘要。交换机/处理器的一切都从 pattern 的位置缓存推导（单一事实来源，
     * 不再跑第二次检测器）；工厂则消费自己的检测结果。
     *
     * Builds the cluster summary. For the switch/processor this derives everything from the
     * pattern's position cache (single source of truth, no second detector run); the factory
     * consumes its detector result instead.
     */
    private fun buildCluster(level: ServerLevel): Any? {
        if (usesDetector) {
            return detection.also { detection = null }
        }
        val scan = scanCache(level) ?: return null
        return createCluster(level, scan)
    }

    /** 从匹配格子的模式扫描结果构造本结构专属的簇。 / Constructs the structure-specific cluster from the pattern scan of the matched cells. */
    protected open fun createCluster(level: ServerLevel, scan: CacheScan): Any? = null

    private fun boundsOf(cluster: Any): Pair<BlockPos, BlockPos> = when (cluster) {
        is AsyncModuleCluster -> cluster.boundsMin to cluster.boundsMax
        is AsyncSwitchCluster -> cluster.boundsMin to cluster.boundsMax
        is AsyncProcessorCluster -> cluster.boundsMin to cluster.boundsMax
        else -> BlockPos.ZERO to BlockPos.ZERO
    }

    protected open fun connectorPositionsOf(cluster: Any?): List<BlockPos> = when (cluster) {
        is AsyncSwitchCluster -> cluster.connectorPositions
        is AsyncProcessorCluster -> cluster.connectorPositions
        else -> emptyList()
    }

    /** 模式检查匹配到的格子，转为簇所需的信息。 / The matched cells of the pattern check, as the information the cluster needs. */
    protected class CacheScan(
        val min: BlockPos,
        val max: BlockPos,
        val blockCount: Int,
        val storageBytes: Long,
        val meConnectors: List<BlockPos>,
        val wanConnectors: List<BlockPos>,
        val lanConnectors: List<BlockPos>,
        val interfaces: List<BlockPos>,
    )

    /**
     * 汇总 pattern 的位置缓存：匹配格子的边界与方块数，加上簇需要的存储/连接器/
     * 接口位置。缓存中的空气格被跳过，与检测器的扫描保持一致。
     *
     * Summarizes the pattern's position cache: bounds and block count of the matched cells, plus
     * the storage/connector/interface positions the cluster needs. Air cells in the cache are
     * skipped, mirroring the detector's scan.
     */
    private fun scanCache(level: ServerLevel): CacheScan? {
        val cache = multiblockState.getCache()
        if (cache.isEmpty()) return null
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        var blockCount = 0
        var storageBytes = 0L
        val me = ArrayList<BlockPos>()
        val wan = ArrayList<BlockPos>()
        val lan = ArrayList<BlockPos>()
        val interfaces = ArrayList<BlockPos>()
        for (pos in cache) {
            val actual = AsyncStructureDetector.kindOf(level, pos) ?: continue
            blockCount++
            minX = minOf(minX, pos.x)
            minY = minOf(minY, pos.y)
            minZ = minOf(minZ, pos.z)
            maxX = maxOf(maxX, pos.x)
            maxY = maxOf(maxY, pos.y)
            maxZ = maxOf(maxZ, pos.z)
            when (actual) {
                AsyncBlockKind.STORAGE -> storageBytes += actual.storageBytes
                AsyncBlockKind.ME_CONNECTOR -> me.add(pos)
                AsyncBlockKind.WAN_CONNECTOR -> wan.add(pos)
                AsyncBlockKind.LAN_CONNECTOR -> lan.add(pos)
                AsyncBlockKind.MODULE_INTERFACE -> interfaces.add(pos)
                else -> {}
            }
        }
        return CacheScan(
            BlockPos(minX, minY, minZ),
            BlockPos(maxX, maxY, maxZ),
            blockCount,
            storageBytes,
            me,
            wan,
            lan,
            interfaces,
        )
    }

    /**
     * 缓存检测结构的每个在界位置，让 GTCEu 的 LevelMixin 在任意结构方块变化时触发
     * [MultiblockState.onBlockStateChanged]。仅工厂使用，其检测结果携带边界。
     *
     * Caches every in-bounds position of the detected structure so GTCEu's LevelMixin fires
     * [MultiblockState.onBlockStateChanged] for a change at any structural block. Only used by the
     * factory, whose detector result carries the bounds.
     */
    private fun cachePositions(detected: Any): List<BlockPos> {
        val (min, max) = when (detected) {
            is AsyncModuleCluster -> detected.boundsMin to detected.boundsMax
            is AsyncSwitchCluster -> detected.boundsMin to detected.boundsMax
            is AsyncProcessorCluster -> detected.boundsMin to detected.boundsMax
            else -> return emptyList()
        }
        return buildList {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    for (x in min.x..max.x) {
                        add(BlockPos(x, y, z))
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction / status
    // ---------------------------------------------------------------------------------------------

    override fun onUse(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        // Mirror IMultiController.onUse: sneak + empty hand on an unformed controller previews the
        // structure in-world. The real pattern (AsyncStructureGtPattern) renders the shape.
        if (!isFormed && player.isShiftKeyDown && player.getItemInHand(hand).isEmpty) {
            if (isRemote) {
                MultiblockInWorldPreviewRenderer.showPreview(
                    pos,
                    this,
                    ConfigHolder.INSTANCE.client.inWorldPreviewDuration * 20,
                )
            }
            return InteractionResult.SUCCESS
        }
        if (!isRemote) {
            MenuOpener.open(AsyncStructureGtStatusMenu.TYPE, player, MenuLocators.forBlockEntity(holder.self()))
        }
        return InteractionResult.sidedSuccess(isRemote)
    }

    fun getCluster(): Any? = cluster

    fun connectorPositions(): List<BlockPos> = connectorPositionsOf(cluster)

    fun getConnectorViews(): List<IAsyncChannelView> {
        val level = level as? ServerLevel ?: return emptyList()
        return connectorPositions().mapNotNull { pos ->
            (getMachine(level, pos) as? IAsyncChannelView)
        }
    }
}

/** async 合成处理器（19 x 15 x (19 + 6N)）的 GTCEu 控制器。 / GTCEu controller of the async synthesis processor (19 x 15 x (19 + 6N)). */
class AsyncStructureGtProcessorMachine(holder: IMachineBlockEntity) : AsyncStructureGtControllerMachine(holder) {
    override fun createCluster(level: ServerLevel, scan: CacheScan): Any? {
        val cluster = AsyncProcessorCluster(
            anchorPos = pos,
            boundsMin = scan.min,
            boundsMax = scan.max,
            blockCount = scan.blockCount,
            storageBytes = scan.storageBytes,
            connectorCount = scan.meConnectors.size + scan.lanConnectors.size,
            meConnectorPositions = scan.meConnectors,
            wanConnectorPositions = scan.wanConnectors,
            lanConnectorPositions = scan.lanConnectors,
            interfacePositions = scan.interfaces,
        )
        for (interfacePos in scan.interfaces) {
            AsyncStructureDetector.detectModule(level, interfacePos)?.let(cluster::addModule)
        }
        return cluster
    }
}

/** async 合成网络交换机（19 x 7 x (11 + 6N)）的 GTCEu 控制器。 / GTCEu controller of an async synthesis network switch (19 x 7 x (11 + 6N)). */
class AsyncStructureGtSwitchMachine(holder: IMachineBlockEntity) : AsyncStructureGtControllerMachine(holder) {
    override fun createCluster(level: ServerLevel, scan: CacheScan): Any? {
        val cluster = AsyncSwitchCluster(
            anchorPos = pos,
            boundsMin = scan.min,
            boundsMax = scan.max,
            blockCount = scan.blockCount,
            meConnectorPositions = scan.meConnectors,
            wanConnectorPositions = scan.wanConnectors,
            lanConnectorPositions = scan.lanConnectors,
            interfacePositions = scan.interfaces,
        )
        for (interfacePos in scan.interfaces) {
            AsyncStructureDetector.detectModule(level, interfacePos)?.let(cluster::addModule)
        }
        return cluster
    }
}

/**
 * async 合成工厂（3 x 7 x 5）的 GTCEu 控制器。工厂是模块的顶前方块；检测从它
 * 正下后方的模块接口开始。
 *
 * GTCEu controller of an async synthesis factory (3 x 7 x 5). The factory is the top-front block of
 * the module; detection starts at the module interface directly below/behind it.
 */
class AsyncStructureGtFactoryMachine(holder: IMachineBlockEntity) : AsyncStructureGtControllerMachine(holder) {
    override val usesDetector: Boolean get() = true

    override fun detect(level: ServerLevel): Any? {
        val facing = frontFacing
        val interfacePos = pos.offset(2 * facing.stepX, -4, 2 * facing.stepZ)
        return AsyncStructureDetector.detectModule(level, interfacePos)
    }
}
