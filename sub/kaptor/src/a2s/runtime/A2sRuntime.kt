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
    fun toLong(value: Any?): Long = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is java.math.BigInteger -> value.toLong()
        is Rational -> value.numerator.divide(value.denominator).toLong()
        is Number -> value.toLong()
        else -> 0L
    }

    @JvmStatic
    fun toBigInt(value: Any?): Any? = when (value) {
        is java.math.BigInteger -> value
        is Number -> java.math.BigInteger.valueOf(value.toLong())
        is Rational -> value.numerator.divide(value.denominator)
        is String -> java.math.BigInteger(value)
        else -> java.math.BigInteger.ZERO
    }

    @JvmStatic
    fun toRational(value: Any?): Any? = when (value) {
        is Rational -> value
        is java.math.BigInteger -> Rational.of(value)
        is Number -> Rational.of(value.toLong())
        is String -> Rational.fromDecimalString(value)
        else -> Rational.ZERO
    }

    @JvmStatic
    fun toF32(value: Any?): Any? = when (value) {
        is Number -> value.toFloat()
        is Rational -> value.numerator.toFloat() / value.denominator.toFloat()
        is String -> value.toFloatOrNull() ?: 0f
        else -> 0f
    }

    @JvmStatic
    fun toF64(value: Any?): Any? = when (value) {
        is Number -> value.toDouble()
        is Rational -> value.numerator.toDouble() / value.denominator.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
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
        A2sSandbox.tick()
        if (receiver is A2sLambdaFn && methodName == "invoke") {
            return receiver.invoke(*args)
        }
        if (isBlockedApi(receiver, methodName)) {
            throw A2sLimitException("blocked API: ${receiver?.javaClass?.name}.$methodName")
        }
        val method = receiver?.javaClass?.methods?.find {
            it.name == methodName && it.parameterCount == args.size
        } ?: return null
        if (method.parameterCount == 1 && method.parameterTypes[0].isArray) {
            return method.invoke(receiver, args)
        }
        return method.invoke(receiver, *args)
    }

    private val blockedMethods = setOf("wait", "notify", "notifyAll", "finalize")
    private val blockedClassPrefixes: Array<String> = arrayOf(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.Process",
        "java.lang.System",
        "java.lang.ClassLoader",
        "java.lang.reflect.",
    )

    private fun isBlockedApi(receiver: Any?, methodName: String): Boolean {
        if (methodName in blockedMethods) return true
        val cn = receiver?.javaClass?.name ?: return false
        for (prefix in blockedClassPrefixes) {
            if (cn.startsWith(prefix)) return true
        }
        return false
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

    private val scriptsToEngine = java.util.concurrent.ConcurrentHashMap<Any, A2sEngine>()

    fun bindScript(scriptObj: Any, engine: A2sEngine) {
        scriptsToEngine[scriptObj] = engine
    }

    fun unbindScript(scriptObj: Any) {
        scriptsToEngine.remove(scriptObj)
    }

    fun unbindEngine(engine: A2sEngine) {
        scriptsToEngine.entries.removeIf { it.value === engine }
        if (currentEngine.get() === engine) currentEngine.remove()
    }

    @JvmStatic
    fun postEvent(event: A2sEventObject) {
        engine()?.eventQueue?.post(event)
    }

    @JvmStatic
    fun postEvent(eventType: String, args: Array<Any?>) {
        postEvent(null, eventType, args)
    }

    @JvmStatic
    fun postEvent(scriptObj: Any?, eventType: String, args: Array<Any?>) {
        val engine = (scriptObj?.let { scriptsToEngine[it] }) ?: currentEngine.get() ?: return
        val ctor = engine.eventConstructor(eventType) ?: return
        val event = ctor.invokeWithArguments(*args) as A2sEventObject
        engine.eventQueue.post(event)
    }

    private val currentEngine = ThreadLocal<A2sEngine?>()

    fun registerEngine(engine: A2sEngine) {
        currentEngine.set(engine)
    }

    fun unregisterEngine(engine: A2sEngine) {
        unbindEngine(engine)
    }

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
