package allyouneed.async

/**
 * Mixed into [appeng.me.GridNode] to track how many channels the grid node of an
 * async processing connector swallowed during the most recent channel assignment.
 */
interface AsyncChannelNodeHolder {
    var asyncSwallowedChannels: Int
}
