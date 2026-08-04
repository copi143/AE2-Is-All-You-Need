package allyouneed.mac

import appeng.api.implementations.parts.ICablePart
import appeng.api.networking.IGridNode
import appeng.api.networking.IManagedGridNode
import appeng.me.ManagedGridNode
import appeng.parts.networking.CablePart

/**
 * Which AE2 hosts/nodes receive a MAC.
 * Cables are pure interconnect fabric and are excluded.
 */
object MacPolicy {
    @JvmStatic
    fun shouldHaveMac(owner: Any?): Boolean {
        if (owner == null) return false
        if (owner is ICablePart || owner is CablePart) return false
        return true
    }

    @JvmStatic
    fun shouldHaveMac(node: IGridNode): Boolean = shouldHaveMac(node.owner)

    @JvmStatic
    fun shouldHaveMac(managed: IManagedGridNode): Boolean {
        val live = managed.node
        if (live != null) return shouldHaveMac(live)
        // Node not created yet: inspect logical host via ManagedGridNode owner field is private;
        // fall back to true and let ensureAndBind re-check after create. Cables are cleared there.
        return true
    }

    @JvmStatic
    fun clearMac(managed: IManagedMacAddressHolder) {
        managed.setMacAddress(MacAddress.NONE)
    }

    @JvmStatic
    fun isCableManaged(managed: ManagedGridNode): Boolean {
        val node = managed.node
        if (node != null) return !shouldHaveMac(node)
        return false
    }
}
