package kaptor.compiler

import kaptor.ir.HookType

data class CompiledHandler(
    val eventType: String, val hookType: HookType, val costLimit: Int
)

data class CompiledScript(
    val className: String, val bytecode: ByteArray, val eventTypes: List<String>, val handlers: List<CompiledHandler>
)

class ScriptCompileError(message: String) : RuntimeException(message)
