package allyouneed.gt

import allyouneed.async.*
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
 * Detector-driven GTCEu multiblock controller of the async synthesis structures. The common
 * [AsyncStructureDetector] is the single source of truth for what counts as formed, so both the
 * plain (own block) and the GT flavours behave identically.
 *
 * GTCEu normally matches a [com.gregtechceu.gtceu.api.pattern.BlockPattern]; the depth-extending
 * switch/processor cannot be expressed as a GT pattern (a pattern repeats a single aisle only), so
 * a placeholder pattern is registered purely to satisfy registrate and [checkPattern] is overridden
 * to run the detector instead. Because [IMultiController.checkPattern] is invoked from an async
 * thread, the actual detection is deferred to the main thread via the GT multiblock lock and a
 * server tick task.
 */
abstract class AsyncStructureGtControllerMachine(
    holder: IMachineBlockEntity,
) : MultiblockControllerMachine(holder), IInteractedMachine, IMachineLife {

    /** The detector result of the most recent pattern check, consumed by [onStructureFormed]. */
    private var detection: Any? = null

    /** The live cluster of the formed structure (module / switch / processor). */
    private var cluster: Any? = null

    /** Runs the detector for this structure kind, anchored at the controller's position. */
    protected abstract fun detect(level: ServerLevel): Any?

    /** Connector positions of a detected structure, used to link the GT connector machines. */
    protected open fun connectorPositionsOf(cluster: Any?): List<BlockPos> = emptyList()

    // ---------------------------------------------------------------------------------------------
    // Pattern checking (detector-driven)
    // ---------------------------------------------------------------------------------------------

    override fun checkPattern(): Boolean {
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
     * Mirrors the GT default but never touches the world from the async thread: detection is
     * deferred to the main thread inside the multiblock lock.
     */
    override fun asyncCheckPattern(periodID: Long) {
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

    /**
     * Forces a main-thread pattern check and keeps the saved-data mapping / async-logic state
     * consistent. Used by [allyouneed.async.AsyncStructureNotifier] so manual builds revalidate
     * immediately instead of waiting for the async poller. Mirrors the body of [asyncCheckPattern]
     * but also re-enters async logic on failure, so a formed-but-broken structure unforms and
     * resumes polling.
     */
    fun requestStructureCheck() {
        val level = level as? ServerLevel ?: return
        level.server.tell(TickTask(0) {
            patternLock.lock()
            try {
                if (checkPatternWithLock()) {
                    setFlipped(false)
                    onStructureFormed()
                    val mwsd = MultiblockWorldSavedData.getOrCreate(level)
                    mwsd.addMapping(multiblockState)
                    mwsd.removeAsyncLogic(this)
                } else {
                    onStructureInvalid()
                    val mwsd = MultiblockWorldSavedData.getOrCreate(level)
                    mwsd.removeMapping(multiblockState)
                    mwsd.addAsyncLogic(this)
                }
            } finally {
                patternLock.unlock()
            }
        })
    }

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
        val detected = detection
        detection = null
        if (detected == null) return
        cluster = detected
        for (pos in connectorPositionsOf(detected)) {
            (getMachine(level, pos) as? AsyncStructureGtConnectorMachine)?.setHostController(this)
        }
        val (min, max) = boundsOf(detected)
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

    private fun boundsOf(cluster: Any): Pair<BlockPos, BlockPos> = when (cluster) {
        is AsyncModuleCluster -> cluster.boundsMin to cluster.boundsMax
        is AsyncSwitchCluster -> cluster.boundsMin to cluster.boundsMax
        is AsyncProcessorCluster -> cluster.boundsMin to cluster.boundsMax
        else -> BlockPos.ZERO to BlockPos.ZERO
    }

    /**
     * Caches every in-bounds position of the detected structure so GTCEu's LevelMixin fires
     * [MultiblockState.onBlockStateChanged] for a change at any structural block, not just the
     * bounds corners. Breaking or replacing any block of a formed structure therefore invalidates
     * it (and re-enters async logic) instead of leaving the glow stuck.
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
    override fun detect(level: ServerLevel): Any? = AsyncStructureDetector.detectProcessor(level, pos)

    override fun connectorPositionsOf(cluster: Any?): List<BlockPos> =
        (cluster as? AsyncProcessorCluster)?.connectorPositions ?: emptyList()
}

/** GTCEu controller of an async synthesis network switch (19 x 7 x (11 + 6N)). */
class AsyncStructureGtSwitchMachine(holder: IMachineBlockEntity) : AsyncStructureGtControllerMachine(holder) {
    override fun detect(level: ServerLevel): Any? = AsyncStructureDetector.detectSwitch(level, pos)

    override fun connectorPositionsOf(cluster: Any?): List<BlockPos> =
        (cluster as? AsyncSwitchCluster)?.connectorPositions ?: emptyList()
}

/**
 * GTCEu controller of an async synthesis factory (3 x 7 x 5). The factory is the top-front block of
 * the module; detection starts at the module interface directly below/behind it.
 */
class AsyncStructureGtFactoryMachine(holder: IMachineBlockEntity) : AsyncStructureGtControllerMachine(holder) {
    override fun detect(level: ServerLevel): Any? {
        val facing = frontFacing
        val interfacePos = pos.offset(2 * facing.stepX, -4, 2 * facing.stepZ)
        return AsyncStructureDetector.detectModule(level, interfacePos)
    }
}
