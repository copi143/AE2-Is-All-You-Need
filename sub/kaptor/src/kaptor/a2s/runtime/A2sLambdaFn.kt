package kaptor.a2s.runtime

/**
 * 函数式接口：所有 a2s lambda 表达式编译为实现此接口的隐藏类。
 *
 * 调用方式：通过 invokeMethod 反射调用 `invoke(Object[])` 方法。
 * 后续阶段实现闭包捕获时，隐藏类字段会持有捕获的变量。
 */
fun interface A2sLambdaFn {
    fun invoke(vararg args: Any?): Any?
}
