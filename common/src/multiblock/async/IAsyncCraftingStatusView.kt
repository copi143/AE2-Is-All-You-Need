package allyouneed.multiblock.async

/**
 * 处理器结构状态的只读视图，由每种宿主的菜单实现（自有方块与 GTCEu 机器），
 * 这样同一个界面就能渲染所有宿主。
 *
 * Read view of the async processing processor status, implemented by the menus of every host
 * flavour (own block and GTCEu machine) so a single screen can render all of them.
 */
interface IAsyncCraftingStatusView {
    /** 当前宿主是否已成形（0/1）。 / Whether the current host is formed (0/1). */
    val formed: Int

    /** 当前宿主是否已接入网格（0/1）。 / Whether the current host is connected to the grid (0/1). */
    val gridConnected: Int

    /** 当前宿主吞掉的总通道数。 / Total channels swallowed by the current host. */
    val swallowedChannels: Int

    /** 当前宿主的存储容量（字节）。 / Storage capacity in bytes of the current host. */
    val storageBytes: Long

    /** 当前宿主的总方块数。 / Total number of blocks of the current host. */
    val blockCount: Int

    /** 当前宿主是否处于无限通道模式（0/1）。 / Whether the current host is in infinite channel mode (0/1). */
    val infiniteChannelMode: Int
}
