package kaptor.a2s.runtime

/**
 * 事件桥接接口。模组侧（Forge/Fabric Mixin）实现此接口，
 * 将 AE2 内部事件转发给 a2s 引擎。
 *
 * 使用流程：
 * ```
 * val bridge: A2sEventBridge = Ae2ScriptBridge()
 * val engine = A2sEngine()
 * engine.bridge = bridge
 * engine.loadScript(source)
 *
 * // AE2 Mixin 中：
 * engine.dispatchFromMap("MeNetworkInsert", mapOf("player" to player, "slot" to 0, ...))
 * ```
 */
interface A2sEventBridge {

    /**
     * 返回此桥接支持的所有事件 schema。
     * 引擎在 [A2sEngine.loadScript] 时调用，用于预注册事件类型。
     *
     * 可以返回 [BuiltinEvents.SCHEMA] 的子集或超集。
     */
    fun registeredEvents(): Map<String, EventSchema>

    /**
     * 从字段映射构造一个 [A2sEventObject] 实例。
     *
     * @param eventType 事件类型名，与 [registeredEvents] 中的 key 对应
     * @param data 字段名到值的映射，键名须与 schema 字段名一致
     * @return 构造好的事件对象，引擎会传给脚本 handler
     * @throws IllegalArgumentException 当 eventType 不支持时
     */
    fun createEvent(eventType: String, data: Map<String, Any?>): A2sEventObject
}
