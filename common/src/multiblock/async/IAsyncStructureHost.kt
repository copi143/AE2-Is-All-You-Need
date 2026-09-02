package allyouneed.multiblock.async

/**
 * 已成形（或可重扫）的交换机 / 处理器宿主。普通方块实体与 GTCEu 控制器都实现它，
 * 让模块接口、连接器和线缆通知不必知道注册路径。
 *
 * A formed (or rescan-capable) switch / processor host. Implemented by both the plain block
 * entity and the GTCEu controller so module interfaces, connectors and cables can notify without
 * knowing the registration path.
 */
interface IAsyncStructureHost {
    val kind: AsyncBlockKind
    fun requestRescan()
}
