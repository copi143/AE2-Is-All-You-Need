package kaptor.a2s.compiler

import kaptor.a2s.ir.A2sScriptFile

/**
 * 顶层编译器：一个脚本 → 事件类字节码 + 脚本类字节码。
 */
class A2sCompiler {
    internal var scriptCounter = 0

    fun resetCounter() {
        scriptCounter = 0
    }

    data class A2sCompiledScript(
        val eventClasses: Map<String, ByteArray>,
        val scriptClass: ByteArray,
        val scriptIndex: Int,
        val eventTypes: List<String>,
        val handlers: List<A2sHandlerInfo>,
        val lambdaClasses: Map<String, ByteArray>,
    )

    data class A2sHandlerInfo(
        val eventType: String,
        val hookType: kaptor.a2s.ir.A2sHookType,
        val methodName: String,
    )

    fun compile(script: A2sScriptFile): A2sCompiledScript {
        val symbols = A2sSymbolTable(script.events, script.functions, script.topLevelVars)
        val classCompiler = A2sClassCompiler(symbols)

        val index = scriptCounter++
        val eventClasses = mutableMapOf<String, ByteArray>()
        for (event in script.events) {
            eventClasses[event.name] = classCompiler.generateEventClass(event)
        }

        val scriptBytes = classCompiler.generateScriptClass(index, script)

        val handlers = script.handlers.map { h ->
            val prefix = when (h.hookType) {
                kaptor.a2s.ir.A2sHookType.ON -> "handle"
                kaptor.a2s.ir.A2sHookType.BEFORE -> "before"
                kaptor.a2s.ir.A2sHookType.AFTER -> "after"
            }
            A2sHandlerInfo(h.eventType, h.hookType, "${prefix}_${A2sNames.sanitize(h.eventType)}")
        }

        return A2sCompiledScript(
            eventClasses = eventClasses,
            scriptClass = scriptBytes,
            scriptIndex = index,
            eventTypes = script.events.map { it.name },
            handlers = handlers,
            lambdaClasses = classCompiler.collectedLambdas,
        )
    }
}
