package allyouneed.parts.logger

import allyouneed.api.IMacAddressHolder
import allyouneed.util.id.mac.MacAddress
import allyouneed.util.id.mac.MacPolicy
import appeng.api.networking.GridHelper
import appeng.api.networking.GridServices
import appeng.api.networking.IGrid
import appeng.api.networking.IGridNode
import appeng.api.networking.IGridNodeListener
import appeng.api.networking.crafting.ICraftingPlan
import appeng.api.networking.crafting.ICraftingSubmitResult
import appeng.api.networking.events.GridChannelRequirementChanged
import appeng.api.networking.events.GridControllerChange
import appeng.api.networking.events.GridCraftingCpuChange
import appeng.api.networking.events.GridPowerStatusChange
import appeng.api.stacks.GenericStack
import appeng.core.sync.packets.CraftingJobStatusPacket
import net.minecraft.world.level.block.entity.BlockEntity

object NetworkLogHooks {
    fun register() {
        GridServices.register(INetworkLogService::class.java, NetworkLogService::class.java)
        GridHelper.addGridServiceEventHandler(
            GridPowerStatusChange::class.java,
            INetworkLogService::class.java,
        ) { svc, ev -> (svc as NetworkLogService).onPower(ev) }
        GridHelper.addGridServiceEventHandler(
            GridControllerChange::class.java,
            INetworkLogService::class.java,
        ) { svc, ev -> (svc as NetworkLogService).onController(ev) }
        GridHelper.addGridServiceEventHandler(
            GridChannelRequirementChanged::class.java,
            INetworkLogService::class.java,
        ) { svc, ev -> (svc as NetworkLogService).onChannelRequirement(ev) }
        GridHelper.addGridServiceEventHandler(
            GridCraftingCpuChange::class.java,
            INetworkLogService::class.java,
        ) { svc, ev -> (svc as NetworkLogService).onCpuChange(ev) }
    }

    @JvmStatic
    fun onNodeStatus(node: IGridNode, reason: IGridNodeListener.State) {
        if (!MacPolicy.shouldHaveMac(node)) return
        if (isTransient(node)) return
        val kind = when (reason) {
            IGridNodeListener.State.POWER ->
                if (node.isPowered) NetworkLogKind.NODE_POWER_ON else NetworkLogKind.NODE_POWER_OFF
            IGridNodeListener.State.CHANNEL ->
                if (node.meetsChannelRequirements()) NetworkLogKind.NODE_CHANNEL_ON else NetworkLogKind.NODE_CHANNEL_OFF
            else -> return
        }
        append(node, kind, *describe(node))
    }

    @JvmStatic
    fun onSubmitJob(grid: IGrid, plan: ICraftingPlan, result: ICraftingSubmitResult) {
        val label = stackLabel(plan.finalOutput())
        if (result.successful()) {
            append(grid, NetworkLogKind.CRAFT_SUBMIT_OK, label)
        } else {
            append(grid, NetworkLogKind.CRAFT_SUBMIT_FAIL, label, result.errorCode()?.name ?: "?")
        }
    }

    @JvmStatic
    fun onCraftingJob(grid: IGrid?, output: GenericStack?, status: CraftingJobStatusPacket.Status) {
        if (grid == null) return
        val kind = when (status) {
            CraftingJobStatusPacket.Status.STARTED -> NetworkLogKind.CRAFT_START
            CraftingJobStatusPacket.Status.FINISHED -> NetworkLogKind.CRAFT_DONE
            CraftingJobStatusPacket.Status.CANCELLED -> NetworkLogKind.CRAFT_CANCEL
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
        if (NetworkLogSettle.wasMoved(owner)) return true
        return try {
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
