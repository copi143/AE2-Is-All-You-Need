package kaptor.a2s.runtime

import kaptor.a2s.compiler.A2sCompiler
import kaptor.a2s.compiler.A2sNames
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
 */
class A2sEngine {
    private val hiddenLoader = A2sHiddenClassLoader(A2sHiddenClassLoader.lookupFor(kaptor.a2s.gen.GenHost::class.java))
    private val compiler = A2sCompiler()

    private val eventClasses = ConcurrentHashMap<String, Class<*>>()
    private val eventConstructors = ConcurrentHashMap<String, MethodHandle>()

    private val beforeHandlers = ConcurrentHashMap<String, MutableList<HandlerEntry>>()
    private val onHandlers = ConcurrentHashMap<String, MutableList<HandlerEntry>>()
    private val afterHandlers = ConcurrentHashMap<String, MutableList<HandlerEntry>>()

    private val scriptInstances = ConcurrentHashMap<Int, Any>()

    val eventQueue = A2sEventQueue()

    init {
        A2sRuntime.eventQueue = eventQueue
        A2sRuntime.eventConstructors = eventConstructors
    }

    data class HandlerEntry(
        val instance: Any,
        val methodHandle: MethodHandle,
        val eventType: String,
        val hookType: A2sHookType,
        val scriptIndex: Int,
    )

    /** 注册内置事件类型（由 common 桥接层提供的事件类）。 */
    fun registerEventClass(eventType: String, clazz: Class<*>, constructor: MethodHandle) {
        eventClasses[eventType] = clazz
        eventConstructors[eventType] = constructor
    }

    var lastError: Throwable? = null
        private set

    /** 编译并加载脚本。 */
    fun loadScript(source: String): Boolean {
        return try {
            val ir = parse(source)
            val compiled = compiler.compile(ir)

            // 定义事件类
            for ((name, bytes) in compiled.eventClasses) {
                if (eventClasses.containsKey(name)) continue
                val defined = hiddenLoader.define(bytes)
                eventClasses[name] = defined.clazz
                val eventDecl = ir.events.first { it.name == name }
                eventConstructors[name] = hiddenLoader.findConstructor(
                    defined.clazz, defined.lookup,
                    eventDecl.params.map { boxedClass(it.type) }
                )
            }

            // 定义脚本类
            val scriptDefined = hiddenLoader.define(compiled.scriptClass)
            val scriptClass = scriptDefined.clazz
            val ctor = hiddenLoader.findConstructor(scriptClass, scriptDefined.lookup, emptyList())
            val instance = ctor.invoke()

            // 注册 handler
            for (h in compiled.handlers) {
                val mh = hiddenLoader.findVirtual(
                    scriptDefined.lookup, scriptClass, h.methodName,
                    Void.TYPE, listOf(A2sEventObject::class.java)
                ).bindTo(instance)
                registerHandler(instance, mh, h.eventType, h.hookType, compiled.scriptIndex)
            }

            scriptInstances[compiled.scriptIndex] = instance
            true
        } catch (e: Exception) {
            lastError = e
            false
        }
    }

    private fun parse(source: String): kaptor.a2s.ir.A2sScriptFile {
        val lexer = A2sLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = A2sParser(tokens)
        val tree = parser.script()
        return A2sVisitor().visit(tree) as kaptor.a2s.ir.A2sScriptFile
    }

    private fun registerHandler(instance: Any, mh: MethodHandle, eventType: String, hookType: A2sHookType, index: Int) {
        val entry = HandlerEntry(instance, mh, eventType, hookType, index)
        when (hookType) {
            A2sHookType.BEFORE -> beforeHandlers.getOrPut(eventType) { mutableListOf() }.add(entry)
            A2sHookType.ON -> onHandlers.getOrPut(eventType) { mutableListOf() }.add(entry)
            A2sHookType.AFTER -> afterHandlers.getOrPut(eventType) { mutableListOf() }.add(entry)
        }
    }

    /** 分发事件（由桥接层传入已构造的事件对象）。 */
    fun dispatch(eventType: String, event: A2sEventObject) {
        // before
        beforeHandlers[eventType]?.forEach { invoke(it, event) }

        // on：依次执行，支持 handled() 停止传播
        onHandlers[eventType]?.forEach { entry ->
            if (event.isHandled) return@forEach
            invoke(entry, event)
        }

        // after：无论如何执行
        afterHandlers[eventType]?.forEach { invoke(it, event) }
    }

    private fun invoke(entry: HandlerEntry, event: A2sEventObject) {
        try {
            entry.methodHandle.invoke(event)
        } catch (e: Throwable) {
            // 异常隔离：记录但不中断后续处理器
        }
    }

    /** flush post 队列，延迟 1 tick 分发。由游戏 tick 调用。 */
    fun flushQueue() {
        for (event in eventQueue.drain()) {
            val eventType = event.javaClass.simpleName.removePrefix("A2sEvent_")
            dispatch(eventType, event)
        }
    }

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
}
