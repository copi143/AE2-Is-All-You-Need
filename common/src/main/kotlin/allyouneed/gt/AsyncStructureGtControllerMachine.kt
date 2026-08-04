package allyouneed.gt

import allyouneed.async.AsyncBlockKind
import allyouneed.async.AsyncModuleCluster
import allyouneed.async.AsyncProcessorCluster
import allyouneed.async.AsyncStructureDetector
import allyouneed.async.AsyncStructureEntityBlock
import allyouneed.async.AsyncSwitchCluster
import allyouneed.async.IAsyncChannelView
import allyouneed.async.setStructuralFormed
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

    /** True for the factory, which forms by module interface probing instead of a GT pattern. */
    protected open val usesDetector: Boolean = false

    /** Detector result of the most recent check, consumed by [rebuildCluster] (factory only). */
    private var detection: Any? = null

    /** The live cluster of the formed structure (module / switch / processor). */
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

    /** Runs the detector for this structure kind, anchored at the controller's position. */
    protected open fun detect(level: ServerLevel): Any? = null

    /**
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

    /** Mirrors the vanilla async blocks: flip the FORMED block state without notifying neighbours. */
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

    /** Constructs the structure-specific cluster from the pattern scan of the matched cells. */
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

    /** The matched cells of the pattern check, as the information the cluster needs. */
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

/** GTCEu controller of the async synthesis processor (19 x 15 x (19 + 6N)). */
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

/** GTCEu controller of an async synthesis network switch (19 x 7 x (11 + 6N)). */
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
