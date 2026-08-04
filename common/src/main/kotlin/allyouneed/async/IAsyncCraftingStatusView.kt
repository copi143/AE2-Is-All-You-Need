package allyouneed.async

/**
 * 处理器结构状态的只读视图，由每种宿主的菜单实现（自有方块与 GTCEu 机器），
 * 这样同一个界面就能渲染所有宿主。
 *
 * Read view of the async processing processor status, implemented by the menus of every host
 * flavour (own block and GTCEu machine) so a single screen can render all of them.
 */
interface IAsyncCraftingStatusView {
    /** 已成形处理器结构数量。 / Number of formed processor structures. */
    val formed: Int

    /** 已接入网格的处理器数量。 / Number of processors connected to the grid. */
    val gridConnected: Int

    /** 所有处理器吞掉的总通道数。 / Total channels swallowed by all processors. */
    val swallowedChannels: Int

    /** 所有处理器的总存储容量（字节）。 / Total storage capacity in bytes across all processors. */
    val storageBytes: Long

    /** 所有处理器的总方块数。 / Total number of blocks across all processors. */
    val blockCount: Int

    /** 处于无限通道模式的处理器的数量。 / Number of processors running in infinite channel mode. */
    val infiniteChannelMode: Int
}
