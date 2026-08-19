package kaptor.a2s.runtime

/**
 * 脚本可调用的运行时桥接。
 *
 * 生成的字节码调用这些静态方法实现内置功能（println、资源解析、listOf 等）。
 * 资源解析通过注入的 [resourceResolver] 完成，避免 kaptor 直接依赖 AE2。
 */
object A2sRuntime {
    @Volatile
    var resourceResolver: kaptor.a2s.resource.ResourceResolver? = null

    /** 解析资源引用，返回 key 对象。当前返回规范 key 字符串，后续由 common 注入实现。 */
    @JvmStatic
    fun resolveResource(raw: String): Any? {
        val resolver = resourceResolver ?: return raw
        return parseResourceRef(resolver, raw)?.key
    }

    @JvmStatic
    fun println(value: Any?) {
        kotlin.io.println(value?.toString() ?: "null")
    }

    @JvmStatic
    fun listOf(vararg values: Any?): List<Any?> = values.toList()

    @JvmStatic
    fun toInt(value: Any?): Any? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    @JvmStatic
    fun toI64(value: Any?): Any? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    @JvmStatic
    fun len(value: Any?): Int = when (value) {
        is String -> value.length
        is Collection<*> -> value.size
        else -> 0
    }

    @JvmStatic
    fun equals(a: Any?, b: Any?): Boolean = a == b

    @JvmStatic
    fun compare(a: Any?, b: Any?): Int {
        if (a is java.math.BigInteger && b is java.math.BigInteger) return a.compareTo(b)
        if (a is Rational && b is Rational) return a.compareTo(b)
        if (a is Number && b is Number) return a.toDouble().compareTo(b.toDouble())
        if (a is Comparable<*> && b != null && a::class == b::class) {
            @Suppress("UNCHECKED_CAST")
            return (a as Comparable<Any>).compareTo(b)
        }
        return 0
    }

    /** 反射读取字段（非事件类型字段的兜底） */
    @JvmStatic
    fun getField(receiver: Any?, fieldName: String): Any? {
        if (receiver is Map<*, *>) return receiver[fieldName]
        val getter = receiver?.javaClass?.getMethod("get${fieldName.replaceFirstChar { it.uppercase() }}")
        return getter?.invoke(receiver)
    }

    @JvmStatic
    fun setField(receiver: Any?, fieldName: String, value: Any?) {
        if (receiver is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (receiver as MutableMap<String, Any?>)[fieldName] = value
        }
    }

    @JvmStatic
    fun getAt(receiver: Any?, index: Any?): Any? {
        return when {
            receiver is List<*> -> receiver[(index as Number).toInt()]
            receiver is Map<*, *> -> receiver[index]
            else -> null
        }
    }

    @JvmStatic
    fun invokeMethod(receiver: Any?, methodName: String, args: Array<Any?>): Any? {
        val method = receiver?.javaClass?.methods?.find {
            it.name == methodName && it.parameterCount == args.size
        } ?: return null
        // 当方法签名恰好是 invoke(Object[]) 时，直接传数组（不展开）
        if (method.parameterCount == 1 && method.parameterTypes[0] == Array::class.java) {
            return method.invoke(receiver, args)
        }
        return method.invoke(receiver, *args)
    }

    /** Lambda 构造器注册表：className → (scriptObj, captures[]) → instance。 */
    private val lambdaCtors = java.util.concurrent.ConcurrentHashMap<String, (Any?, Array<out Any?>) -> Any?>()

    /** 注册 lambda 隐藏类的构造器。[factory] 接受 (scriptObj, captures) 返回实例。 */
    @JvmStatic
    fun registerLambdaCtor(className: String, factory: (Any?, Array<out Any?>) -> Any?) {
        lambdaCtors[className] = factory
    }

    /**
     * 工厂方法：由编译后的脚本字节码调用，创建 lambda 实例。
     * 替代 NEW + INVOKESPECIAL（隐藏类无法被 NEW 直接实例化）。
     */
    @JvmStatic
    fun newLambda(className: String, scriptObj: Any?, captures: Array<Any?>): Any? {
        val factory = lambdaCtors[className]
            ?: throw IllegalStateException("Lambda class not registered: $className")
        return factory(scriptObj, captures)
    }

    /** 清除所有已注册的 lambda 构造器（引擎重置时调用）。 */
    @JvmStatic
    fun clearLambdaCtors() {
        lambdaCtors.clear()
    }

    /** post 事件：加入当前引擎的事件队列（延迟 1 tick 分发）。 */
    @JvmStatic
    fun postEvent(event: A2sEventObject) {
        currentEngine.get()?.eventQueue?.post(event)
    }

    /** post EventType(args)：查找构造器 MH 创建事件并入队。 */
    @JvmStatic
    fun postEvent(eventType: String, args: Array<Any?>) {
        val engine = currentEngine.get() ?: return
        val ctor = engine.eventConstructor(eventType) ?: return
        val event = ctor.invokeWithArguments(*args) as A2sEventObject
        engine.eventQueue.post(event)
    }

    /** 当前线程关联的引擎实例。由 [A2sEngine.init] 设置，[A2sEngine.unregisterAll] 清除。 */
    private val currentEngine = ThreadLocal<A2sEngine?>()

    /** 引擎构造时调用，绑定当前线程。 */
    fun registerEngine(engine: A2sEngine) {
        currentEngine.set(engine)
    }

    /** 引擎销毁/重置时调用，解除绑定。 */
    fun unregisterEngine(engine: A2sEngine) {
        if (currentEngine.get() === engine) currentEngine.remove()
    }

    /** 获取当前线程关联的引擎（用于测试/外部调用）。 */
    fun engine(): A2sEngine? = currentEngine.get()

    /** 拆分资源引用 `raw`，如 `item|minecraft:diamond`、`minecraft:diamond`、`diamond`。 */
    private fun parseResourceRef(
        resolver: kaptor.a2s.resource.ResourceResolver,
        raw: String,
    ): kaptor.a2s.resource.ResolvedResource? {
        // 形式：prefix|namespace:path | namespace:path | path
        val prefix: String?
        val rest: String
        val pipeIdx = raw.indexOf('|')
        if (pipeIdx >= 0) {
            prefix = raw.substring(0, pipeIdx)
            rest = raw.substring(pipeIdx + 1)
        } else {
            prefix = null
            rest = raw
        }
        val colonIdx = rest.indexOf(':')
        return if (colonIdx >= 0) {
            resolver.resolve(prefix, rest.substring(0, colonIdx), rest.substring(colonIdx + 1))
        } else {
            resolver.resolve(prefix, null, rest)
        }
    }
}
