package allyouneed.api

/**
 * Mixed into [appeng.me.ManagedGridNode] as the authoritative MAC store
 * across node destroy/recreate cycles.
 */
interface IManagedMacAddressHolder {
    var macAddress: Long
    val macTagName: String
}
