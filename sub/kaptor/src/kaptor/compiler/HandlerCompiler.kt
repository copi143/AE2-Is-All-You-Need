package kaptor.compiler

import kaptor.ir.HookType
import kaptor.ir.IrHandler
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.*

fun generateHandler(
    cw: ClassVisitor, internalName: String, handler: IrHandler
) {
    val prefix = when (handler.hookType) {
        HookType.ON -> "handle"
        HookType.BEFORE -> "before"
        HookType.AFTER -> "after"
    }
    val methodName = "${prefix}_${sanitizeName(handler.eventType)}"
    val methodDesc = "(Ljava/lang/Object;)V"

    val mv = cw.visitMethod(ACC_PUBLIC, methodName, methodDesc, null, null)
    mv.visitCode()

    val ctx = MethodContext(mv, handler.costLimit, handler.paramName)
    ctx.declareLocal("event", "Ljava/lang/Object;")
    mv.visitVarInsn(ALOAD, 1)
    mv.visitVarInsn(ASTORE, ctx.getLocal("event"))

    ctx.consumeCost(1)
    ctx.checkCostLimit("Handler entry cost exceeded for ${handler.eventType}")

    if (handler.paramName != null) {
        ctx.declareLocal(handler.paramName, "Ljava/lang/Object;")
        mv.visitVarInsn(ALOAD, ctx.getLocal("event"))
        mv.visitVarInsn(ASTORE, ctx.getLocal(handler.paramName))
    }

    for (instr in handler.body) {
        compileInstruction(ctx, instr)
    }

    ctx.consumeCost(1)
    ctx.checkCostLimit("Handler exit cost exceeded for ${handler.eventType}")

    mv.visitInsn(RETURN)
    mv.visitMaxs(ctx.maxStack, ctx.maxLocals)
    mv.visitEnd()
}

private fun sanitizeName(name: String): String {
    return name.replace(Regex("[^a-zA-Z0-9_]"), "_")
}
