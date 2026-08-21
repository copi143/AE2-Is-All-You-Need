package allyouneed.parts.logger

import allyouneed.util.id.mac.MacPolicy
import appeng.api.networking.IGrid
import appeng.api.networking.IGridNode
import appeng.api.networking.IGridServiceProvider
import appeng.api.networking.events.GridChannelRequirementChanged
import appeng.api.networking.events.GridControllerChange
import appeng.api.networking.events.GridCraftingCpuChange
import appeng.api.networking.events.GridPowerStatusChange
import appeng.api.networking.pathing.ControllerState
import net.minecraft.nbt.CompoundTag

class NetworkLogService(private val grid: IGrid) : INetworkLogService, IGridServiceProvider {
    private val loggers = ArrayList<NetworkLoggerBlockEntity>()
    private val pending = ArrayList<NetworkLogEntry>()
    private var conflicted = false
    private var dirty = true

    override fun addNode(gridNode: IGridNode, savedData: CompoundTag?) {
        val owner = gridNode.owner
        if (owner is NetworkLoggerBlockEntity) {
            loggers.add(owner)
            dirty = true
        }
        if (MacPolicy.shouldHaveMac(gridNode) && !NetworkLogSettle.noteJoin(identity(gridNode))) {
            pending += NetworkLogHooks.entry(grid, NetworkLogKind.NODE_ADDED, *NetworkLogHooks.describe(gridNode))
        }
    }

    override fun removeNode(gridNode: IGridNode) {
        val owner = gridNode.owner
        if (owner is NetworkLoggerBlockEntity) {
            loggers.remove(owner)
            dirty = true
        }
        if (MacPolicy.shouldHaveMac(gridNode)) {
            val loggerId = loggers.singleOrNull()?.loggerId ?: 0
            NetworkLogSettle.noteLeave(
                identity(gridNode),
                loggerId,
                NetworkLogHooks.entry(grid, NetworkLogKind.NODE_REMOVED, *NetworkLogHooks.describe(gridNode)),
            )
        }
    }

    override fun onServerStartTick() {
        NetworkLogSettle.beginTick()
    }

    override fun onServerEndTick() {
        if (dirty) {
            recheck()
            dirty = false
        }
        NetworkLogSettle.flushIfNeeded()
        if (pending.isNotEmpty()) {
            val batch = ArrayList(pending)
            pending.clear()
            for (entry in batch) {
                append(entry)
            }
        }
        for (logger in loggers) {
            if (logger.loggerId != 0) {
                LogStore.persist(logger.loggerId)
            }
        }
    }

    override fun append(entry: NetworkLogEntry) {
        if (conflicted) return
        val logger = loggers.singleOrNull() ?: return
        logger.record(entry)
    }

    override fun isConflicted(): Boolean = conflicted

    override fun loggerCount(): Int = loggers.size

    fun onChannelRequirement(event: GridChannelRequirementChanged) {
        if (booting()) return
        append(
            NetworkLogHooks.entry(
                grid,
                NetworkLogKind.CHANNEL_REQUIREMENT,
                *NetworkLogHooks.describe(event.node),
            ),
        )
    }

    fun onCpuChange(event: GridCraftingCpuChange) {
        if (booting()) return
        append(
            NetworkLogHooks.entry(
                grid,
                NetworkLogKind.CPU_CHANGE,
                *NetworkLogHooks.describe(event.node),
            ),
        )
    }

    private fun booting(): Boolean = try {
        grid.pathingService.isNetworkBooting
    } catch (_: RuntimeException) {
        true
    }

    private fun identity(node: IGridNode): Any = node.owner ?: node

    fun onPower(event: GridPowerStatusChange) {
        if (booting()) return
        val powered = grid.energyService.isNetworkPowered
        append(
            NetworkLogHooks.entry(
                grid,
                if (powered) NetworkLogKind.POWER_ON else NetworkLogKind.POWER_OFF,
            ),
        )
    }

    fun onController(event: GridControllerChange) {
        val kind = when (grid.pathingService.controllerState) {
            ControllerState.CONTROLLER_ONLINE -> NetworkLogKind.CONTROLLER_ONLINE
            ControllerState.NO_CONTROLLER -> NetworkLogKind.CONTROLLER_NONE
            ControllerState.CONTROLLER_CONFLICT -> NetworkLogKind.CONTROLLER_CONFLICT
        }
        append(NetworkLogHooks.entry(grid, kind))
    }

    private fun recheck() {
        val count = loggers.size
        val nowConflicted = count > 1
        if (nowConflicted && !conflicted) {
            for (logger in loggers) {
                logger.setConflict(true)
                logger.record(
                    NetworkLogHooks.entry(grid, NetworkLogKind.LOGGER_CONFLICT, count.toString()),
                )
            }
        } else if (!nowConflicted && conflicted) {
            val logger = loggers.singleOrNull()
            if (logger != null) {
                logger.setConflict(false)
                logger.record(NetworkLogHooks.entry(grid, NetworkLogKind.LOGGER_OK))
            }
        } else if (nowConflicted) {
            for (logger in loggers) {
                logger.setConflict(true)
            }
        } else {
            loggers.singleOrNull()?.setConflict(false)
        }
        conflicted = nowConflicted
    }
}
