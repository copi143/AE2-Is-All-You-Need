package kaptor.a2s.runtime

import kaptor.a2s.ir.A2sType

/**
 * 事件 schema：描述一个事件类型的名称、字段和用途。
 * 由 [A2sEventBridge.registeredEvents] 返回，供引擎在编译期推断事件字段类型。
 */
data class EventSchema(
    val name: String,
    val description: String,
    val fields: List<FieldSchema>,
)

/**
 * 事件字段 schema：字段名、IR 类型、说明。
 */
data class FieldSchema(
    val name: String,
    val type: A2sType,
    val description: String = "",
)
