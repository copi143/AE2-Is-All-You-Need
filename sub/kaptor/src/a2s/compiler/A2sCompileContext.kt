package kaptor.a2s.compiler

import kaptor.a2s.ir.A2sType
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor

class A2sCompileError(message: String) : RuntimeException(message)

/**
 * 局部变量（槽号 + 类型 + 可变性）。
 */
class A2sLocal(val slot: Int, val type: A2sType, val mutable: Boolean, var boxed: Boolean = false)

data class A2sCapturedVar(val name: String, val type: A2sType, val boxed: Boolean)

data class A2sFinallyFrame(val body: List<kaptor.a2s.ir.A2sStmt>, val loopDepth: Int)

/**
 * 统一编译上下文：管理局部变量槽分配、类型环境、循环标签与栈深度。
 *
 * 第一阶段所有值装箱存储，局部变量统一用引用槽（ALOAD/ASTORE）。
 */
class A2sCompileContext(
    val mv: MethodVisitor,
    val symbols: A2sSymbolTable,
    val className: String,
    isStatic: Boolean,
) {
    private val locals = mutableMapOf<String, A2sLocal>()
    private var nextLocal = if (isStatic) 0 else 1
    private var maxStack = 0
    private var maxLocals = if (isStatic) 0 else 1

    private val loopStartLabels = mutableListOf<Label>()
    private val loopEndLabels = mutableListOf<Label>()
    private val finallyStack = mutableListOf<A2sFinallyFrame>()

    /**
     * scriptObj 所在的局部变量槽号。
     * - 普通方法：0（this 即脚本实例）
     * - lambda invoke()：scriptObj 加载后的槽号（供顶层 var 访问用）
     */
    var scriptObjSlot: Int = 0

    /** 事件字段名 → 类型。编译事件方法时注入，用于字段访问走 GETFIELD。 */
    var eventFields: Map<String, kaptor.a2s.ir.A2sType> = emptyMap()

    fun isEventField(name: String): Boolean = eventFields.containsKey(name)

    fun eventFieldType(name: String): kaptor.a2s.ir.A2sType = eventFields[name] ?: kaptor.a2s.ir.A2sUnknown

    fun declareLocal(name: String, type: A2sType, mutable: Boolean = false): Int {
        locals[name]?.let { return it.slot }
        val slot = nextLocal++
        locals[name] = A2sLocal(slot, type, mutable)
        maxLocals = maxOf(maxLocals, nextLocal)
        return slot
    }

    fun getLocal(name: String): A2sLocal =
        locals[name] ?: throw A2sCompileError("未定义的变量: $name")

    fun localType(name: String): A2sType = locals[name]?.type ?: eventFields[name] ?: kaptor.a2s.ir.A2sUnknown

    fun localTypes(): Map<String, A2sType> = locals.mapValues { it.value.type } + eventFields

    fun hasLocal(name: String): Boolean = locals.containsKey(name)

    fun isMutableLocal(name: String): Boolean = locals[name]?.mutable ?: false

    fun loadVariable(name: String) {
        val local = getLocal(name)
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, local.slot)
        if (local.boxed) {
            mv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, TYPE_REF)
            mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETFIELD, TYPE_REF, "element", "Ljava/lang/Object;")
        }
    }

    fun loadVariableRaw(name: String) {
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, getLocal(name).slot)
    }

    fun storeVariable(name: String) {
        val local = getLocal(name)
        if (local.boxed) {
            mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, local.slot)
            mv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, TYPE_REF)
            mv.visitInsn(org.objectweb.asm.Opcodes.SWAP)
            mv.visitFieldInsn(org.objectweb.asm.Opcodes.PUTFIELD, TYPE_REF, "element", "Ljava/lang/Object;")
        } else {
            mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, local.slot)
        }
    }

    fun markBoxed(name: String) {
        getLocal(name).boxed = true
    }

    fun isBoxed(name: String): Boolean = locals[name]?.boxed == true

    fun ensureBoxed(name: String) {
        val local = getLocal(name)
        if (local.boxed) return
        mv.visitTypeInsn(org.objectweb.asm.Opcodes.NEW, TYPE_REF)
        mv.visitInsn(org.objectweb.asm.Opcodes.DUP)
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, local.slot)
        mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, TYPE_REF, "<init>", "(Ljava/lang/Object;)V", false)
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, local.slot)
        local.boxed = true
    }

    fun pushFinally(body: List<kaptor.a2s.ir.A2sStmt>) {
        finallyStack.add(A2sFinallyFrame(body, loopStartLabels.size))
    }

    fun popFinally() {
        finallyStack.removeLast()
    }

    fun finallyFrames(): List<A2sFinallyFrame> = finallyStack.toList()

    fun finallyFramesLeavingLoop(): List<A2sFinallyFrame> =
        finallyStack.filter { it.loopDepth >= loopStartLabels.size }

    fun pushLoop(start: Label, end: Label) {
        loopStartLabels.add(start)
        loopEndLabels.add(end)
    }

    fun popLoop() {
        loopStartLabels.removeLast()
        loopEndLabels.removeLast()
    }

    fun breakLabel(): Label = loopEndLabels.lastOrNull()
        ?: throw A2sCompileError("break 不在循环内")

    fun continueLabel(): Label = loopStartLabels.lastOrNull()
        ?: throw A2sCompileError("continue 不在循环内")

    fun touchStack(size: Int) {
        maxStack = maxOf(maxStack, size)
    }

    private var tempCounter = 0
    /** 分配一个唯一的临时局部变量槽（不会与已声明的变量冲突）。 */
    fun allocateTemp(): Int {
        val slot = nextLocal++
        maxLocals = maxOf(maxLocals, nextLocal)
        return slot
    }

    fun finish() {
        mv.visitMaxs(maxStack, maxLocals)
    }
}
