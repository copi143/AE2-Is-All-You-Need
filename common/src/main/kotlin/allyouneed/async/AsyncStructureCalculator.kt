package allyouneed.async

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * 每个方块实体的检测状态，服务于 async 合成结构。
 *
 * 宿主实体扮演的角色决定检测什么：
 *  - 模块接口：检测搭建在它上面的模块（向上）
 *  - 网络交换机：检测交换机结构及其舱位上的模块
 *  - 网络控制器：检测处理器结构、其模块以及经线缆连接的交换机
 *
 * 每次重扫都会刷新方块实体的 FORMED 状态、把结构内的连接器方块实体链接到它，
 * 并通知上层的所属结构（接口 -> 宿主，交换机 -> 处理器），使模块/交换机列表保持一致。
 *
 * Per-block-entity detector state for the async synthesis structures.
 *
 * The role of the host entity decides what is detected:
 *  - module interface: the module built on it (upward)
 *  - network switch: the switch structure plus the modules on its bays
 *  - network controller: the processor structure, its modules and the switches linked via cable
 *
 * On every rescan the block entity's FORMED state is refreshed, connector block entities of the
 * structure are linked to it, and the owning structure above (interface -> host, switch ->
 * processor) is notified so module/switch lists stay coherent.
 */
class AsyncStructureCalculator(private val host: AsyncStructureBlockEntity) {

    private var moduleCluster: AsyncModuleCluster? = null
    private var switchCluster: AsyncSwitchCluster? = null
    private var processorCluster: AsyncProcessorCluster? = null
    private var linkedConnectors = ArrayList<BlockPos>()

    fun calculateMultiblock(level: ServerLevel, pos: BlockPos) = revalidate(level, pos)

    fun updateMultiblockAfterNeighborUpdate(level: ServerLevel, selfPos: BlockPos, changedPos: BlockPos) {
        revalidate(level, selfPos)
    }

    /** 由上层方块调用，强制对本结构做一次完整重扫。 / Called by an upstream block to force a full rescan of this structure. */
    fun requestRescan(level: ServerLevel) {
        revalidate(level, host.blockPos)
    }

    fun getModuleCluster(): AsyncModuleCluster? = moduleCluster
    fun getSwitchCluster(): AsyncSwitchCluster? = switchCluster
    fun getProcessorCluster(): AsyncProcessorCluster? = processorCluster

    fun isFormed(): Boolean {
        val sw = switchCluster
        val proc = processorCluster
        return when (host.kind) {
            AsyncBlockKind.MODULE_INTERFACE -> moduleCluster != null
            AsyncBlockKind.SWITCH -> sw != null && !sw.isDestroyed
            AsyncBlockKind.CONTROLLER -> proc != null && !proc.isDestroyed
            else -> false
        }
    }

    private fun revalidate(level: ServerLevel, triggerPos: BlockPos) {
        if (host.isRemoved || host.notLoaded()) return
        when (host.kind) {
            AsyncBlockKind.MODULE_INTERFACE -> updateModule(level)
            AsyncBlockKind.SWITCH -> updateSwitch(level)
            AsyncBlockKind.CONTROLLER -> updateProcessor(level)
            else -> {}
        }
    }

    /** 方块被移除时销毁缓存的簇，并通知上游。 / Destroys the cached cluster(s) when the block is removed; notifies upstream. */
    fun destroy(level: ServerLevel) {
        val hadModule = moduleCluster != null
        val hadSwitch = switchCluster != null
        val hadProcessor = processorCluster != null
        destroyCurrent(level)
        host.updateSubType()
        when {
            hadProcessor -> {}
            hadSwitch -> notifyProcessor(level)
            hadModule -> notifyHostController(level)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Module interface -> module
    // ---------------------------------------------------------------------------------------------

    private fun updateModule(level: ServerLevel) {
        val formed = AsyncStructureDetector.detectModule(level, host.blockPos)
        val old = moduleCluster
        val unchanged = formed != null && old != null && formed.factoryPos == old.factoryPos
        if (unchanged) return

        old?.let { setModuleFormed(level, it, false) }
        moduleCluster = formed
        formed?.let { setModuleFormed(level, it, true) }
        host.updateSubType()
        notifyHostController(level)
    }

    private fun setModuleFormed(level: ServerLevel, module: AsyncModuleCluster, formed: Boolean) {
        val factory = level.getBlockEntity(module.factoryPos)
        if (factory is AsyncStructureBlockEntity && factory.kind == AsyncBlockKind.FACTORY) {
            factory.setFormedState(formed)
        }
        setStructuralFormed(level, module.boundsMin, module.boundsMax, formed)
    }

    // ---------------------------------------------------------------------------------------------
    // Switch controller -> switch + modules
    // ---------------------------------------------------------------------------------------------

    private fun updateSwitch(level: ServerLevel) {
        val formed = AsyncStructureDetector.detectSwitch(level, host.blockPos)
        val old = switchCluster
        val oldLive = old != null && !old.isDestroyed
        val structureChanged = !oldLive || formed == null ||
            formed.anchorPos != old.anchorPos || formed.boundsMin != old.boundsMin || formed.boundsMax != old.boundsMax
        val moduleChanged = oldLive && formed != null &&
            formed.getModuleFactoryPositions() != old.getModuleFactoryPositions()

        if (oldLive && formed != null && !structureChanged) {
            old.clearModules()
            formed.getModules().forEach(old::addModule)
            resyncConnectors(level, formed)
            host.updateSubType()
            if (moduleChanged) {
                notifyProcessor(level)
            }
            return
        }

        destroyCurrent(level)
        switchCluster = formed
        if (formed != null) {
            resyncConnectors(level, formed)
            setStructuralFormed(level, formed.boundsMin, formed.boundsMax, true)
        }
        host.updateSubType()
        if (structureChanged) {
            notifyProcessor(level)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Processor controller -> processor + modules + switches
    // ---------------------------------------------------------------------------------------------

    private fun updateProcessor(level: ServerLevel) {
        val formed = AsyncStructureDetector.detectProcessor(level, host.blockPos)
        val old = processorCluster
        val oldLive = old != null && !old.isDestroyed
        val changed = !oldLive || formed == null ||
            formed.anchorPos != old.anchorPos || formed.boundsMin != old.boundsMin || formed.boundsMax != old.boundsMax

        if (oldLive && formed != null && !changed) {
            return
        }

        destroyCurrent(level)
        processorCluster = formed
        if (formed != null) {
            resyncConnectors(level, formed)
            setStructuralFormed(level, formed.boundsMin, formed.boundsMax, true)
        }
        host.updateSubType()
    }

    // ---------------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------------

    private fun destroyCurrent(level: ServerLevel) {
        unlinkConnectors(level)
        val m = moduleCluster
        val s = switchCluster
        val p = processorCluster
        moduleCluster = null
        switchCluster = null
        processorCluster = null
        m?.let { setModuleFormed(level, it, false) }
        s?.let { setStructuralFormed(level, it.boundsMin, it.boundsMax, false) }
        p?.let { setStructuralFormed(level, it.boundsMin, it.boundsMax, false) }
    }

    private fun resyncConnectors(level: ServerLevel, cluster: Any) {
        val positions = when (cluster) {
            is AsyncSwitchCluster -> cluster.connectorPositions.toSet()
            is AsyncProcessorCluster -> cluster.connectorPositions.toSet()
            else -> emptySet()
        }
        val removed = linkedConnectors - positions
        for (pos in removed) {
            (level.getBlockEntity(pos) as? AsyncStructureConnectorBlockEntity)?.setHostController(null)
        }
        for (pos in positions) {
            (level.getBlockEntity(pos) as? AsyncStructureConnectorBlockEntity)?.setHostController(host)
        }
        linkedConnectors = ArrayList(positions)
    }

    private fun unlinkConnectors(level: ServerLevel) {
        for (pos in linkedConnectors) {
            (level.getBlockEntity(pos) as? AsyncStructureConnectorBlockEntity)?.setHostController(null)
        }
        linkedConnectors.clear()
    }

    /**
     * 模块接口通知托管它的交换机或处理器（其缓存结构边界包含该接口）重扫。
     *
     * A module interface notifies the switch or processor that hosts it (its cached structure
     * bounds contain the interface) to rescan.
     */
    private fun notifyHostController(level: ServerLevel) {
        AsyncStructureDetector.findHostController(level, host.blockPos)?.requestRescan()
    }

    /**
     * 交换机通知与它相连的处理器。处理器通过从交换机 WAN 连接器向上沿线缆
     * 走到处理器所有的 LAN 连接器来定位。
     *
     * A switch notifies the processor it is wired to. The processor is found by walking cables
     * upstream from the switch's WAN connector until a processor-owned LAN connector is reached.
     */
    private fun notifyProcessor(level: ServerLevel) {
        val cluster = switchCluster ?: return
        for (wan in cluster.wanConnectorPositions) {
            val farEnd = AsyncStructureDetector.followCableFromWan(level, wan) ?: continue
            val processor = AsyncStructureDetector.findHostController(level, farEnd) ?: continue
            if (processor.kind == AsyncBlockKind.CONTROLLER) {
                processor.requestRescan()
                return
            }
        }
    }
}
