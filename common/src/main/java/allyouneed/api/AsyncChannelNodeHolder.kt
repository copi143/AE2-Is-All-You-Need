package allyouneed.api

/**
 * 混入 [appeng.me.GridNode]：记录 async 处理连接器的网格节点在最近一次通道分配
 * 中吞掉了多少条通道。
 *
 * Mixed into [appeng.me.GridNode] to track how many channels the grid node of an
 * async processing connector swallowed during the most recent channel assignment.
 */
interface AsyncChannelNodeHolder {
    var asyncSwallowedChannels: Int
}
