package kaptor.compiler

import kaptor.ir.IrExpression
import kaptor.ir.IrIdentifier
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.INVOKESTATIC

internal const val TYPE_HANDLER_BASE = "kaptor/runtime/ScriptHandlerBase"
internal const val TYPE_SANDBOX = "kaptor/runtime/ScriptSandbox"
internal const val TYPE_SCRIPT_RUNTIME = "kaptor/runtime/ScriptRuntime"
internal const val TYPE_EVENT_ACCESSOR = "kaptor/runtime/EventAccessor"

class MethodContext(
    val mv: MethodVisitor,
    val costLimit: Int,
    val eventParamName: String? = null,
) {
    var currentCost: Int = 0
    private val locals = mutableMapOf<String, Int>()
    private var nextLocal = 1
    var maxStack: Int = 0
        private set
    var maxLocals: Int = 2
        private set
    val loopEndLabels = mutableListOf<Label>()
    val loopStartLabels = mutableListOf<Label>()

    fun declareLocal(name: String, type: String): Int {
        if (name in locals) return locals[name]!!
        val slot = nextLocal++
        locals[name] = slot
        maxLocals = maxOf(maxLocals, nextLocal)
        return slot
    }

    fun getLocal(name: String): Int {
        return locals[name] ?: throw ScriptCompileError("Undefined variable: $name")
    }

    fun consumeCost(amount: Int) {
        currentCost += amount
        maxStack = maxOf(maxStack, 4)
    }

    fun checkCostLimit(message: String) {
        if (currentCost > costLimit) {
            mv.visitLdcInsn(message)
            mv.visitMethodInsn(
                INVOKESTATIC, TYPE_SANDBOX, "throwLimitExceeded", "(Ljava/lang/String;)V", false
            )
        }
    }

    fun isEventVariable(expr: IrExpression): Boolean {
        if (expr !is IrIdentifier) return false
        return expr.name == "event" || expr.name == eventParamName
    }
}
