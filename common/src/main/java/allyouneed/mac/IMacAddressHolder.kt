package allyouneed.mac

/**
 * Mixed into [appeng.me.GridNode] to expose the 48-bit MAC address.
 */
interface IMacAddressHolder {
    var macAddress: Long
}
