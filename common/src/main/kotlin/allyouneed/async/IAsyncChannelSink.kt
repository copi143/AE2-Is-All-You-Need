package allyouneed.async

/**
 * 由 async 处理连接器方块实体实现。结构成形后，其网格节点会吞掉所有可用的
 * 通道（最多 32），使连接器下游的一切都因缺乏通道而无法工作——这正是
 * “处理”能力发挥作用的方式。
 *
 * Implemented by the async processing connector block entity. Once the structure is formed, its
 * grid node swallows all available channels (up to 32), starving everything downstream of the
 * connector of channels — this is how "processing" takes effect.
 */
interface IAsyncChannelSink {
    /**
     * 该 sink 所属的多方块结构当前是否成形。只有成形状态的 sink 才会吞通道。
     *
     * Whether the sink's multiblock structure is currently formed. Only formed sinks swallow channels.
     */
    fun isFormed(): Boolean
}
