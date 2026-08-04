package kaptor.compiler

import kaptor.SrcType
import kaptor.ir.IrHandler
import kaptor.ir.IrScriptFile
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*

class ScriptCompiler {
    private var classCounter = 0

    fun resetCounter() {
        classCounter = 0
    }

    fun compile(
        ir: IrScriptFile,
        scriptName: String,
        eventClassMap: Map<String, String> = emptyMap(),
        srcType: SrcType,
    ): CompiledScript {
        val className = "script.${scriptName.replace('.', '_')}_${classCounter++}"
        val internalName = className.replace('.', '/')

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

        cw.visit(
            V17,
            ACC_PUBLIC or ACC_SUPER,
            internalName,
            null,
            TYPE_HANDLER_BASE,
            null,
        )

        cw.visitSource("$scriptName.${srcType.extension}", null)

        generateInit(cw, internalName)

        for (handler in ir.handlers) {
            val eventClass = eventClassMap[handler.eventType]
            generateHandler(cw, internalName, handler, eventClass)
        }

        generateGetEventTypes(cw, ir.handlers)
        generateGetCostLimits(cw, ir.handlers)

        cw.visitEnd()

        return CompiledScript(
            className = className,
            bytecode = cw.toByteArray(),
            eventTypes = ir.handlers.map { it.eventType }.distinct(),
            handlers = ir.handlers.map { CompiledHandler(it.eventType, it.hookType, it.costLimit) })
    }

    private fun generateInit(cw: ClassVisitor, internalName: String) {
        val mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(INVOKESPECIAL, TYPE_HANDLER_BASE, "<init>", "()V", false)
        mv.visitInsn(RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
    }

    private fun generateGetEventTypes(cw: ClassVisitor, handlers: List<IrHandler>) {
        val mv = cw.visitMethod(ACC_PUBLIC, "getEventTypes", "()[Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitLdcInsn(handlers.size)
        mv.visitTypeInsn(ANEWARRAY, "java/lang/String")
        for ((i, handler) in handlers.withIndex()) {
            mv.visitInsn(DUP)
            mv.visitLdcInsn(i)
            mv.visitLdcInsn(handler.eventType)
            mv.visitInsn(AASTORE)
        }
        mv.visitInsn(ARETURN)
        mv.visitMaxs(3, 1)
        mv.visitEnd()
    }

    private fun generateGetCostLimits(cw: ClassVisitor, handlers: List<IrHandler>) {
        val mv = cw.visitMethod(ACC_PUBLIC, "getCostLimits", "()Ljava/util/Map;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(NEW, "java/util/HashMap")
        mv.visitInsn(DUP)
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
        for (handler in handlers) {
            mv.visitInsn(DUP)
            mv.visitLdcInsn(handler.eventType)
            mv.visitLdcInsn(handler.costLimit)
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
            mv.visitMethodInsn(
                INVOKEINTERFACE,
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                true
            )
            mv.visitInsn(POP)
        }
        mv.visitInsn(ARETURN)
        mv.visitMaxs(3, 1)
        mv.visitEnd()
    }
}
