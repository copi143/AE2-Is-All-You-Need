package kaptor.a2s.runtime

/**
 * 可变闭包捕获盒，对应 Kotlin 编译器的 `kotlin.jvm.internal.Ref.ObjectRef`。
 * `var` 被 lambda 捕获时，外层局部与捕获字段都持有同一个盒子。
 */
class A2sRef(@JvmField var element: Any?)
