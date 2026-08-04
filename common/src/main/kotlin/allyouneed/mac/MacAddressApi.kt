package allyouneed.mac

import appeng.api.implementations.parts.ICablePart
import appeng.api.networking.IGridNode
import appeng.api.networking.IManagedGridNode
import appeng.me.ManagedGridNode
import appeng.me.helpers.IGridConnectedBlockEntity
import appeng.parts.AEBasePart

/** Extension: MAC of a live grid node, or [MacAddress.NONE]. */
fun IGridNode.macAddress(): Long = if (this is IMacAddressHolder) macAddress else MacAddress.NONE

/** Extension: MAC cached on a managed node, or [MacAddress.NONE]. */
fun IManagedGridNode.macAddress(): Long = if (this is IManagedMacAddressHolder) macAddress else MacAddress.NONE

fun Long.toMacString(): String = MacAddress.format(this)

/**
 * Collect / apply MAC maps on common AE2 hosts for wrench export and place import.
 * Cable hosts are ignored.
 */
object MacHosts {
    @JvmStatic
    fun collectMacs(host: Any?): Map<String, Long> {
        if (host == null || isCableHost(host)) return emptyMap()
        return MacAddressRegistry.collectFromManaged(collectManagedNodes(host))
    }

    @JvmStatic
    fun applyMacs(host: Any?, macs: Map<String, Long>) {
        if (host == null || macs.isEmpty() || isCableHost(host)) return
        for (managed in collectManagedNodes(host)) {
            MacAddressRegistry.applyToManaged(managed, macs)
        }
    }

    private fun isCableHost(host: Any): Boolean = host is ICablePart

    private fun collectManagedNodes(host: Any): List<ManagedGridNode> {
        val out = ArrayList<ManagedGridNode>(2)
        when (host) {
            is IGridConnectedBlockEntity -> addManaged(host.mainNode, out)
            is AEBasePart -> addManaged(host.mainNode, out)
        }
        scrapeManagedFields(host, out)
        return out
    }

    private fun addManaged(node: IManagedGridNode?, out: MutableList<ManagedGridNode>) {
        if (node is ManagedGridNode && node !in out) {
            out.add(node)
        }
    }

    private fun scrapeManagedFields(host: Any, out: MutableList<ManagedGridNode>) {
        var cls: Class<*>? = host.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (!IManagedGridNode::class.java.isAssignableFrom(field.type)) continue
                try {
                    field.isAccessible = true
                    addManaged(field.get(host) as? IManagedGridNode, out)
                } catch (_: Exception) {
                    // ignore
                }
            }
            cls = cls.superclass
        }
    }
}
