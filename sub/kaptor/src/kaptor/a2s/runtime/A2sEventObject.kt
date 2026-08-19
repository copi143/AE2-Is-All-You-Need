package kaptor.a2s.runtime

/**
 * 事件基类。所有事件数据类（由 `event` 声明生成）继承此类。
 *
 * 承载事件处理阶段的状态：
 * - [isDenied]：`deny()` 设置，用于阻止 AE2 存储操作（仅内置拦截点）
 * - [isHandled]：`handled()` 设置，用于停止 on 链传播
 */
abstract class A2sEventObject {
    private var denied = false
    private var handled = false

    /** 阻止操作（仅内置拦截点有效）。与 handled() 正交，不停止传播。 */
    fun deny() {
        denied = true
    }

    val isDenied: Boolean get() = denied

    /** 标记已处理，停止 on 链传播。与 deny() 正交，不设置 deny。 */
    fun handled() {
        handled = true
    }

    val isHandled: Boolean get() = handled
}
