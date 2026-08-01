package allyouneed.async

/**
 * Implemented by the async processing connector block entity. Its grid node will swallow
 * all available channels (up to 32) once the structure is formed, starving everything
 * downstream of the connector of channels.
 */
interface IAsyncChannelSink {
    /**
     * Whether the sink's multiblock structure is currently formed. Only formed sinks swallow channels.
     */
    fun isFormed(): Boolean
}
