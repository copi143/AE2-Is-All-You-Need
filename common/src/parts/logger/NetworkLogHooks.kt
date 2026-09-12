package allyouneed.parts.logger

import allyouneed.api.IMacAddressHolder
import allyouneed.util.id.mac.MacAddress
import allyouneed.util.id.mac.MacPolicy
import appeng.api.networking.*
import appeng.api.networking.crafting.ICraftingPlan
import appeng.api.networking.crafting.ICraftingSubmitResult
import appeng.api.networking.events.*
import appeng.api.stacks.GenericStack
import appeng.core.sync.packets.CraftingJobStatusPacket
import net.minecraft.world.level.block.entity.BlockEntity

object NetworkLogHooks {
    fun register() {
        GridServices.register(INetworkLogService::class.java, NetworkLogService::class.java)
        listOf<Pair<Class<out GridEvent>, (IGridService, GridEvent) -> Unit>>(
            GridPowerStatusChange::class.java to { svc, e -> (svc as NetworkLogService).onPower(e as GridPowerStatusChange) },
            GridControllerChange::class.java to { svc, e -> (svc as NetworkLogService).onController(e as GridControllerChange) },
            GridChannelRequirementChanged::class.java to { svc, e -> (svc as NetworkLogService).onChannelRequirement(e as GridChannelRequirementChanged) },
            GridCraftingCpuChange::class.java to { svc, e -> (svc as NetworkLogService).onCpuChange(e as GridCraftingCpuChange) },
        ).forEach { (eventClass, handler) ->
            GridHelper.addGridServiceEventHandler(eventClass, INetworkLogService::class.java, handler)
        }
    }

    @JvmStatic
    fun onNodeStatus(node: IGridNode, reason: IGridNodeListener.State) {
        if (!MacPolicy.shouldHaveMac(node)) return
        if (isTransient(node)) return
        val kind = when (reason) {
            IGridNodeListener.State.POWER -> if (node.isPowered) NetworkLogKind.NodePowerOn else NetworkLogKind.NodePowerOff

            IGridNodeListener.State.CHANNEL -> if (node.meetsChannelRequirements()) NetworkLogKind.NodeChannelOn else NetworkLogKind.NodeChannelOff

            else -> return
        }
        append(node, kind, *describe(node))
    }

    @JvmStatic
    fun onSubmitJob(grid: IGrid, plan: ICraftingPlan, result: ICraftingSubmitResult) {
        val label = stackLabel(plan.finalOutput())
        if (result.successful()) {
            append(grid, NetworkLogKind.CraftSubmitOk, label)
        } else {
            append(grid, NetworkLogKind.CraftSubmitFail, label, result.errorCode()?.name ?: "?")
        }
    }

    @JvmStatic
    fun onCraftingJob(grid: IGrid?, output: GenericStack?, status: CraftingJobStatusPacket.Status) {
        if (grid == null) return
        val kind = when (status) {
            CraftingJobStatusPacket.Status.STARTED -> NetworkLogKind.CraftStart
            CraftingJobStatusPacket.Status.FINISHED -> NetworkLogKind.CraftDone
            CraftingJobStatusPacket.Status.CANCELLED -> NetworkLogKind.CraftCancel
        }
        append(grid, kind, stackLabel(output))
    }

    fun entry(grid: IGrid, kind: NetworkLogKind, vararg args: String): NetworkLogEntry {
        return NetworkLogEntry(System.currentTimeMillis(), kind, args.toList())
    }

    fun describe(node: IGridNode): Array<out String> {
        val vis = node.visualRepresentation
        val name = vis?.displayName?.string ?: node.owner?.javaClass?.simpleName ?: "?"
        val pos = when (val owner = node.owner) {
            is BlockEntity -> {
                val p = owner.blockPos
                "${p.x},${p.y},${p.z}"
            }

            else -> "-"
        }
        val macHolder = node as? IMacAddressHolder
        val mac = if (macHolder != null && MacAddress.isValid(macHolder.macAddress)) {
            MacAddress.format(macHolder.macAddress)
        } else {
            ""
        }
        return if (mac.isEmpty()) arrayOf(name, pos) else arrayOf(name, pos, mac)
    }

    private fun append(node: IGridNode, kind: NetworkLogKind, vararg args: String) {
        val service = serviceOf(node) ?: return
        service.append(entry(node.grid, kind, *args))
    }

    private fun append(grid: IGrid, kind: NetworkLogKind, vararg args: String) {
        val service = serviceOf(grid) ?: return
        service.append(entry(grid, kind, *args))
    }

    private fun isTransient(node: IGridNode): Boolean {
        val owner = node.owner ?: node
        return NetworkLogSettle.wasMoved(owner) || try {
            node.grid.pathingService.isNetworkBooting
        } catch (_: RuntimeException) {
            true
        }
    }

    private fun serviceOf(node: IGridNode): INetworkLogService? {
        return try {
            node.grid.getService(INetworkLogService::class.java)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun serviceOf(grid: IGrid): INetworkLogService? {
        return try {
            grid.getService(INetworkLogService::class.java)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun stackLabel(stack: GenericStack?): String {
        if (stack == null) return "?"
        return "${stack.what().displayName.string} x${stack.amount()}"
    }
}
