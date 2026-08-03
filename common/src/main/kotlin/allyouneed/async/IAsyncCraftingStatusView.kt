package allyouneed.async

/**
 * Read view of the async processing processor status, implemented by the menus of every host
 * flavour (own block and GTCEu machine) so a single screen can render all of them.
 */
interface IAsyncCraftingStatusView {
    val formed: Int
    val gridConnected: Int
    val swallowedChannels: Int
    val storageBytes: Long
    val blockCount: Int
    val infiniteChannelMode: Int
}
