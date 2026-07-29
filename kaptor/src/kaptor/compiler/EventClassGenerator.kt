package kaptor.compiler

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*

object EventClassGenerator {

    private const val RUNTIME_PKG = "kaptor/runtime/event"

    fun generate(eventType: String, schema: EventSchema): ByteArray {
        val safeName = sanitize(eventType)
        val internalName = "$RUNTIME_PKG/$safeName"
        val desc = "L$internalName;"

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(V17, ACC_PUBLIC or ACC_SUPER, internalName, null, "java/lang/Object", null)

        val fieldName = "__data"
        val fieldDesc = "Ljava/util/Map;"

        cw.visitField(ACC_PRIVATE or ACC_FINAL, fieldName, fieldDesc, null, null).visitEnd()

        generateConstructor(cw, internalName, fieldName, fieldDesc)
        for (param in schema.parameters) {
            generateGetter(cw, internalName, param)
            generateSetter(cw, internalName, param, fieldName, fieldDesc)
        }
        generateGetData(cw, internalName, fieldName, fieldDesc)

        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun generateConstructor(cw: ClassVisitor, internalName: String, fieldName: String, fieldDesc: String) {
        val mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/util/Map;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitVarInsn(ALOAD, 0)
        mv.visitVarInsn(ALOAD, 1)
        mv.visitFieldInsn(PUTFIELD, internalName, fieldName, fieldDesc)
        mv.visitInsn(RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()
    }

    private fun generateGetter(cw: ClassVisitor, internalName: String, param: ParamDef) {
        val getterName = "get${param.name.replaceFirstChar { it.uppercase() }}"
        val mv = cw.visitMethod(ACC_PUBLIC, getterName, "()Ljava/lang/Object;", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitFieldInsn(GETFIELD, internalName, "__data", "Ljava/util/Map;")
        mv.visitLdcInsn(param.name)
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true)
        mv.visitInsn(ARETURN)
        mv.visitMaxs(2, 1)
        mv.visitEnd()
    }

    private fun generateSetter(cw: ClassVisitor, internalName: String, param: ParamDef, fieldName: String, fieldDesc: String) {
        val setterName = "set${param.name.replaceFirstChar { it.uppercase() }}"
        val mv = cw.visitMethod(ACC_PUBLIC, setterName, "(Ljava/lang/Object;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitFieldInsn(GETFIELD, internalName, fieldName, fieldDesc)
        mv.visitLdcInsn(param.name)
        mv.visitVarInsn(ALOAD, 1)
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true)
        mv.visitInsn(POP)
        mv.visitInsn(RETURN)
        mv.visitMaxs(3, 2)
        mv.visitEnd()
    }

    private fun generateGetData(cw: ClassVisitor, internalName: String, fieldName: String, fieldDesc: String) {
        val mv = cw.visitMethod(ACC_PUBLIC, "getData", "()Ljava/util/Map;", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitFieldInsn(GETFIELD, internalName, fieldName, fieldDesc)
        mv.visitInsn(ARETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
    }

    fun className(eventType: String): String {
        return "$RUNTIME_PKG/${sanitize(eventType)}"
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }
}
