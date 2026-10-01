package allyouneed.util

/**
 * 不捕获调用栈的 Throwable 占位（`super(msg, null, true, false)` 是 protected，
 * 只能经由子类调用）。独立顶层类而非 mixin 内部类：mixin 包内的类禁止被注入代码
 * 直接引用（Mixin 0.8.5 的 IllegalClassLoadError），因此必须放在 mixin 包之外。
 */
class NoStackTraceThrowable(message: String) : Throwable(message, null, true, false)
