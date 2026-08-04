package allyouneed.async

/**
 * 已接入网格的 async 连接器的只读视图，由普通连接器方块实体和 GT 连接器机器
 * 共同实现。让状态菜单无需关心连接器由哪条注册路径产生，即可检查它们。
 *
 * Read view of a grid-connected async connector, implemented both by the plain connector block
 * entity and by the GT connector machines. Lets the status menus inspect connectors regardless of
 * which registration path produced them.
 */
interface IAsyncChannelView {
    /** 连接器的网格节点当前是否在线。 / Whether the connector's grid node is online. */
    val isGridConnected: Boolean

    /** 连接器当前吞掉的通道数。 / Number of channels the connector currently swallows. */
    val swallowedChannels: Int

    /** 所连网格是否运行在无限通道模式（此时无通道可吞）。 / Whether the connected grid runs in infinite channel mode (nothing to swallow). */
    val isInfiniteChannelMode: Boolean
}
