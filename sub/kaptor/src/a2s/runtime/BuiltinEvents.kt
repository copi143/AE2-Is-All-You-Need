package kaptor.a2s.runtime

import kaptor.a2s.ir.*

/**
 * a2s 引擎预定义的 8 个 AE2 内置事件 schema。
 *
 * 模组侧通过 [A2sEventBridge] 接口提供实际的事件构造逻辑，
 * 本对象仅定义字段名称和类型，供编译期类型推断使用。
 */
object BuiltinEvents {

    /** 所有内置事件 schema，key 为事件名。 */
    val SCHEMA: Map<String, EventSchema> = listOf(
        // ── ME 存储拦截 ──
        EventSchema(
            name = "MeNetworkInsert",
            description = "ME 网络存储插入操作。调用 deny() 可阻止插入。",
            fields = listOf(
                FieldSchema("player", A2sAny, "执行操作的玩家"),
                FieldSchema("slot", A2sI32, "目标槽位"),
                FieldSchema("item", A2sAny, "被插入的物品"),
                FieldSchema("amount", A2sI64, "插入数量"),
            ),
        ),
        EventSchema(
            name = "MeNetworkExtract",
            description = "ME 网络存储提取操作。调用 deny() 可阻止提取。",
            fields = listOf(
                FieldSchema("player", A2sAny, "执行操作的玩家"),
                FieldSchema("slot", A2sI32, "源槽位"),
                FieldSchema("item", A2sAny, "被提取的物品"),
                FieldSchema("amount", A2sI64, "提取数量"),
            ),
        ),

        // ── 网络状态变化 ──
        EventSchema(
            name = "MeNetworkFormed",
            description = "ME 网络组建成功。",
            fields = listOf(
                FieldSchema("player", A2sAny, "触发组建的玩家（可能为 null）"),
                FieldSchema("networkId", A2sString, "网络标识"),
            ),
        ),
        EventSchema(
            name = "MeNetworkBroken",
            description = "ME 网络断开。",
            fields = listOf(
                FieldSchema("player", A2sAny, "触发断开的玩家（可能为 null）"),
                FieldSchema("networkId", A2sString, "网络标识"),
            ),
        ),

        // ── 玩家交互 ──
        EventSchema(
            name = "PlayerInteractAe2Block",
            description = "玩家右键点击 AE2 方块。",
            fields = listOf(
                FieldSchema("player", A2sAny, "交互的玩家"),
                FieldSchema("blockPos", A2sAny, "方块坐标"),
                FieldSchema("hand", A2sAny, "交互手"),
                FieldSchema("item", A2sAny, "手持物品"),
                FieldSchema("face", A2sAny, "点击面"),
            ),
        ),
        EventSchema(
            name = "PlayerBreakAe2Block",
            description = "玩家破坏 AE2 方块。",
            fields = listOf(
                FieldSchema("player", A2sAny, "破坏的玩家"),
                FieldSchema("blockPos", A2sAny, "方块坐标"),
            ),
        ),

        // ── 设备生命周期 ──
        EventSchema(
            name = "DeviceActivated",
            description = "AE2 设备上线。",
            fields = listOf(
                FieldSchema("device", A2sAny, "设备实例"),
                FieldSchema("networkId", A2sString, "所属网络标识"),
            ),
        ),
        EventSchema(
            name = "DeviceDeactivated",
            description = "AE2 设备离线。",
            fields = listOf(
                FieldSchema("device", A2sAny, "设备实例"),
                FieldSchema("networkId", A2sString, "所属网络标识"),
            ),
        ),
        EventSchema(
            name = "DeviceChannelChanged",
            description = "AE2 设备频道变化。",
            fields = listOf(
                FieldSchema("device", A2sAny, "设备实例"),
                FieldSchema("networkId", A2sString, "所属网络标识"),
                FieldSchema("oldChannel", A2sI32, "原频道"),
                FieldSchema("newChannel", A2sI32, "新频道"),
            ),
        ),
    ).associateBy { it.name }
}
