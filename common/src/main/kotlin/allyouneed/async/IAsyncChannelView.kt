package allyouneed.async

/**
 * Read view of a grid-connected async connector, implemented both by the plain connector block
 * entity and by the GT connector machines. Lets the status menus inspect connectors regardless of
 * which registration path produced them.
 */
interface IAsyncChannelView {
    /** Whether the connector's grid node is online. */
    val isGridConnected: Boolean

    /** Number of channels the connector currently swallows. */
    val swallowedChannels: Int

    /** Whether the connected grid runs in infinite channel mode (nothing to swallow). */
    val isInfiniteChannelMode: Boolean
}
