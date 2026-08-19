package kaptor.a2s.runtime

import kaptor.a2s.compiler.A2sCompiler
import kaptor.a2s.ir.A2sHookType
import kaptor.a2s.parser.A2sLexer
import kaptor.a2s.parser.A2sParser
import kaptor.a2s.parser.A2sVisitor
import org.antlr.v4.runtime.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap

/**
 * a2s 引擎实例。每个玩家 AE2 网络一个实例。
 *
 * 职责：编译脚本、注册事件类型、注册 handler、分发事件（before/on/after）、post 队列。
 * 支持多脚本管理：按名加载/卸载/重载。
 */
class A2sEngine {
    private val hiddenLoader = A2sHiddenClassLoader(A2sHiddenClassLoader.lookupFor(kaptor.a2s.gen.GenHost::class.java))
    private val compiler = A2sCompiler()

    private val eventClasses = ConcurrentHashMap<String, Class<*>>()
    private val eventConstructors = ConcurrentHashMap<String, MethodHandle>()
    private val eventFieldOrders = ConcurrentHashMap<String, List<String>>()

    private val beforeHandlers = ConcurrentHashMap<String, MutableList<HandlerEntry>>()
    private val onHandlers = ConcurrentHashMap<String, MutableList<HandlerEntry>>()
    private val afterHandlers = ConcurrentHashMap<String, MutableList<HandlerEntry>>()

    private val scriptInstances = ConcurrentHashMap<Int, Any>()
    private val scripts = ConcurrentHashMap<String, ScriptEntry>()

    /** 桥接接口。设置后，[loadScript] 会自动从桥接预注册事件 schema。 */
    var bridge: A2sEventBridge? = null

    val eventQueue = A2sEventQueue()

    private val errorRing = ArrayDeque<A2sError>(MAX_ERRORS + 1)

    init {
        A2sRuntime.registerEngine(this)
    }

    // ── 数据结构 ──

    data class HandlerEntry(
        val instance: Any,
        val methodHandle: MethodHandle,
        val eventType: String,
        val hookType: A2sHookType,
        val scriptIndex: Int,
        val scriptName: String,
    )

    data class ScriptEntry(
        val name: String,
        val scriptIndex: Int,
        val instance: Any,
        val handlerEntries: List<HandlerEntry>,
    )

    // ── 事件注册 ──

    /** 注册内置事件类型（由 common 桥接层提供的事件类）。 */
    fun registerEventClass(eventType: String, clazz: Class<*>, constructor: MethodHandle) {
        eventClasses[eventType] = clazz
        eventConstructors[eventType] = constructor
    }

    // ── 错误管理 ──

    /** 最近一次错误（兼容旧 API）。 */
    val lastError: Throwable? get() = errors.lastOrNull()?.cause

    /** 错误历史（最近 [MAX_ERRORS] 条）。 */
    val errors: List<A2sError> get() = errorRing.toList()

    private fun recordError(scriptName: String?, phase: A2sError.Phase, message: String, cause: Throwable?) {
        if (errorRing.size >= MAX_ERRORS) errorRing.removeFirst()
        errorRing.addLast(A2sError(scriptName = scriptName, phase = phase, message = message, cause = cause))
    }

    /** 清空错误历史。 */
    fun clearErrors() {
        errorRing.clear()
    }

    // ── 脚本加载 ──

    /**
     * 编译并加载脚本。
     *
     * @param source a2s 源码
     * @param scriptName 脚本名称（用于卸载/重载）。若为 null，自动生成 auto_N。
     * @return 是否成功
     */
    fun loadScript(source: String, scriptName: String? = null): Boolean {
        val name = scriptName ?: "auto_${compiler.scriptCounter}"
        val snapshot = takeSnapshot()

        // 同名碰撞：先卸载旧版本
        if (scripts.containsKey(name)) {
            unloadScript(name)
        }

        return try {
            val ir = parse(source, name) ?: return false
            val compiled = compiler.compile(ir)

            // 定义事件类（同名则跳过，保持已有类）
            for ((eventType, bytes) in compiled.eventClasses) {
                if (eventClasses.containsKey(eventType)) continue
                val defined = hiddenLoader.define(bytes)
                eventClasses[eventType] = defined.clazz
                val eventDecl = ir.events.first { it.name == eventType }
                eventConstructors[eventType] = hiddenLoader.findConstructor(
                    defined.clazz, defined.lookup,
                    eventDecl.params.map { boxedClass(it.type) }
                )
                eventFieldOrders[eventType] = eventDecl.params.map { it.name }
            }

            // 定义 lambda 隐藏类
            val lambdaConstructors = mutableMapOf<String, MethodHandle>()
            for ((lambdaName, bytes) in compiled.lambdaClasses) {
                val defined = hiddenLoader.define(bytes)
                lambdaConstructors[lambdaName] = hiddenLoader.findConstructor(
                    defined.clazz, defined.lookup, listOf(Object::class.java)
                )
            }

            // 定义脚本类并实例化
            val scriptDefined = hiddenLoader.define(compiled.scriptClass)
            val scriptClass = scriptDefined.clazz
            val ctor = hiddenLoader.findConstructor(scriptClass, scriptDefined.lookup, emptyList())
            val instance = ctor.invoke()

            // 绑定 lambda 到脚本字段
            for ((lambdaName, _) in compiled.lambdaClasses) {
                val ctorMh = lambdaConstructors[lambdaName] ?: continue
                val lambdaInstance = ctorMh.invoke(instance)
                try {
                    val field = scriptClass.getDeclaredField(lambdaName)
                    field.isAccessible = true
                    field.set(instance, lambdaInstance)
                } catch (_: NoSuchFieldException) { }
            }

            // 注册 handler
            val handlerEntries = mutableListOf<HandlerEntry>()
            for (h in compiled.handlers) {
                val mh = hiddenLoader.findVirtual(
                    scriptDefined.lookup, scriptClass, h.methodName,
                    Void.TYPE, listOf(A2sEventObject::class.java)
                ).bindTo(instance)
                val entry = HandlerEntry(instance, mh, h.eventType, h.hookType, compiled.scriptIndex, name)
                handlerEntries.add(entry)
                registerHandler(entry)
            }

            scriptInstances[compiled.scriptIndex] = instance
            scripts[name] = ScriptEntry(name, compiled.scriptIndex, instance, handlerEntries)
            true
        } catch (e: Exception) {
            rollbackSnapshot(snapshot)
            recordError(name, A2sError.Phase.COMPILE, e.message ?: e.toString(), e)
            false
        }
    }

    // ── 脚本卸载 ──

    /** 按名称卸载单个脚本，移除其所有 handler。其他脚本不受影响。 */
    fun unloadScript(name: String): Boolean {
        val entry = scripts.remove(name) ?: return false
        scriptInstances.remove(entry.scriptIndex)
        for (h in entry.handlerEntries) {
            removeHandler(h)
        }
        return true
    }

    // ── 脚本重载 ──

    /** 重载脚本：卸载旧版本，加载新源码。 */
    fun reloadScript(name: String, source: String): Boolean {
        unloadScript(name)
        return loadScript(source, name)
    }

    // ── 全量清理 ──

    /** 清空所有已注册的 handler、脚本实例、事件类和队列。 */
    fun unregisterAll() {
        beforeHandlers.clear()
        onHandlers.clear()
        afterHandlers.clear()
        scriptInstances.clear()
        scripts.clear()
        eventClasses.clear()
        eventConstructors.clear()
        eventFieldOrders.clear()
        eventQueue.clear()
        clearErrors()
    }

    // ── 事件分发 ──

    /** 分发事件（由桥接层传入已构造的事件对象）。 */
    fun dispatch(eventType: String, event: A2sEventObject) {
        beforeHandlers[eventType]?.forEach { invoke(it, event) }
        onHandlers[eventType]?.forEach { entry ->
            if (event.isHandled) return@forEach
            invoke(entry, event)
        }
        afterHandlers[eventType]?.forEach { invoke(it, event) }
    }

    /**
     * 便捷分发：从字段映射构造事件并分发。
     * 使用脚本定义的事件类构造器创建实例，保证 handler 内字段访问成功。
     */
    fun dispatchFromMap(eventType: String, data: Map<String, Any?>) {
        val ctor = eventConstructors[eventType] ?: return
        val fieldOrder = eventFieldOrders[eventType] ?: return
        val args = fieldOrder.map { data[it] }
        val event = ctor.invokeWithArguments(args) as A2sEventObject
        dispatch(eventType, event)
    }

    /** flush post 队列，延迟 1 tick 分发。由游戏 tick 调用。 */
    fun flushQueue() {
        for (event in eventQueue.drain()) {
            val eventType = event.javaClass.simpleName.removePrefix("A2sEvent_")
            dispatch(eventType, event)
        }
    }

    // ── 统计 ──

    data class EngineStats(
        val loadedScripts: Int,
        val totalHandlers: Int,
        val registeredEvents: Int,
        val recentErrors: Int,
    )

    fun stats(): EngineStats = EngineStats(
        loadedScripts = scripts.size,
        totalHandlers = beforeHandlers.values.sumOf { it.size } +
            onHandlers.values.sumOf { it.size } +
            afterHandlers.values.sumOf { it.size },
        registeredEvents = eventClasses.size,
        recentErrors = errorRing.size,
    )

    // ── 内部实现 ──

    private fun parse(source: String, scriptName: String): kaptor.a2s.ir.A2sScriptFile? {
        return try {
            val lexer = A2sLexer(CharStreams.fromString(source))
            val tokens = CommonTokenStream(lexer)
            val parser = A2sParser(tokens)
            val tree = parser.script()
            A2sVisitor().visit(tree) as kaptor.a2s.ir.A2sScriptFile
        } catch (e: Exception) {
            recordError(scriptName, A2sError.Phase.PARSE, e.message ?: e.toString(), e)
            null
        }
    }

    private fun registerHandler(entry: HandlerEntry) {
        when (entry.hookType) {
            A2sHookType.BEFORE -> beforeHandlers.getOrPut(entry.eventType) { mutableListOf() }.add(entry)
            A2sHookType.ON -> onHandlers.getOrPut(entry.eventType) { mutableListOf() }.add(entry)
            A2sHookType.AFTER -> afterHandlers.getOrPut(entry.eventType) { mutableListOf() }.add(entry)
        }
    }

    private fun removeHandler(entry: HandlerEntry) {
        val list = when (entry.hookType) {
            A2sHookType.BEFORE -> beforeHandlers[entry.eventType]
            A2sHookType.ON -> onHandlers[entry.eventType]
            A2sHookType.AFTER -> afterHandlers[entry.eventType]
        }
        list?.removeAll { it.scriptIndex == entry.scriptIndex && it.eventType == entry.eventType && it.hookType == entry.hookType }
    }

    private fun invoke(entry: HandlerEntry, event: A2sEventObject) {
        try {
            entry.methodHandle.invoke(event)
        } catch (e: Throwable) {
            recordError(entry.scriptName, A2sError.Phase.RUNTIME,
                "handler ${entry.hookType.name.lowercase()} ${entry.eventType}: ${e.cause?.message ?: e.message}", e.cause ?: e)
        }
    }

    /** 用于失败回滚的快照。记录加载前已存在的事件类名。 */
    private data class Snapshot(val knownEventClasses: Set<String>, val knownScripts: Set<String>)

    private fun takeSnapshot(): Snapshot = Snapshot(
        knownEventClasses = eventClasses.keys.toSet(),
        knownScripts = scripts.keys.toSet(),
    )

    private fun rollbackSnapshot(snapshot: Snapshot) {
        // 移除加载期间新增的事件类
        val added = eventClasses.keys.filter { it !in snapshot.knownEventClasses }
        for (name in added) {
            eventClasses.remove(name)
            eventConstructors.remove(name)
            eventFieldOrders.remove(name)
        }
    }

    /** 获取事件类型的构造器（供 A2sRuntime.postEvent 使用）。 */
    fun eventConstructor(eventType: String): MethodHandle? = eventConstructors[eventType]

    /** 获取事件类型的字段顺序（供 dispatchFromMap 使用）。 */
    fun eventFieldOrder(eventType: String): List<String>? = eventFieldOrders[eventType]

    fun eventTypeName(clazz: Class<*>): String =
        clazz.simpleName.removePrefix("A2sEvent_")

    private fun boxedClass(type: kaptor.a2s.ir.A2sType): Class<*> = when (type) {
        kaptor.a2s.ir.A2sI32, kaptor.a2s.ir.A2sU32 -> Integer::class.java
        kaptor.a2s.ir.A2sI64, kaptor.a2s.ir.A2sU64 -> Long::class.java
        kaptor.a2s.ir.A2sF32 -> Float::class.java
        kaptor.a2s.ir.A2sF64 -> Double::class.java
        kaptor.a2s.ir.A2sBoolean -> Boolean::class.java
        kaptor.a2s.ir.A2sString -> String::class.java
        kaptor.a2s.ir.A2sBigInt -> java.math.BigInteger::class.java
        kaptor.a2s.ir.A2sRational -> Rational::class.java
        else -> Object::class.java
    }

    companion object {
        private const val MAX_ERRORS = 64
    }
}
