package kaptor.a2s.compiler

import kaptor.a2s.ir.*
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

internal const val TYPE_RATIONAL = "kaptor/a2s/runtime/Rational"
internal const val TYPE_RUNTIME = "kaptor/a2s/runtime/A2sRuntime"
internal const val TYPE_EVENT_OBJECT = "kaptor/a2s/runtime/A2sEventObject"

/**
 * 类型工具：JVM 描述符映射、装箱拆箱、数值运算编译。
 *
 * 第一阶段所有值统一用装箱表示（局部变量/字段均为 Object 语义，但装箱类型用具体类描述符以便调用方法）。
 */
object A2sTypeCodegen {

    /** 装箱后的 JVM 描述符。定长类型返回对应包装类，引用类型返回自身描述符。 */
    fun boxedDescriptor(type: A2sType): String = when (type) {
        A2sI32, A2sU32 -> "Ljava/lang/Integer;"
        A2sI64, A2sU64 -> "Ljava/lang/Long;"
        A2sF32 -> "Ljava/lang/Float;"
        A2sF64 -> "Ljava/lang/Double;"
        A2sBoolean -> "Ljava/lang/Boolean;"
        A2sString -> "Ljava/lang/String;"
        A2sBigInt -> "Ljava/math/BigInteger;"
        A2sRational -> "L$TYPE_RATIONAL;"
        A2sLambdaType -> "Lkaptor/a2s/runtime/A2sLambdaFn;"
        is A2sListType -> "Ljava/util/List;"
        else -> "Ljava/lang/Object;"
    }

    /** 装箱类型的内部名（用于 CHECKCAST 指令）。 */
    fun boxedInternalName(type: A2sType): String = when (type) {
        A2sI32, A2sU32 -> "java/lang/Integer"
        A2sI64, A2sU64 -> "java/lang/Long"
        A2sF32 -> "java/lang/Float"
        A2sF64 -> "java/lang/Double"
        A2sBoolean -> "java/lang/Boolean"
        A2sString -> "java/lang/String"
        A2sBigInt -> "java/math/BigInteger"
        A2sRational -> TYPE_RATIONAL
        A2sLambdaType -> "kaptor/a2s/runtime/A2sLambdaFn"
        is A2sListType -> "java/util/List"
        else -> "java/lang/Object"
    }

    /** 原生描述符（仅定长类型有） */
    fun primitiveDescriptor(type: A2sType): String = when (type) {
        A2sI32, A2sU32 -> "I"
        A2sI64, A2sU64 -> "J"
        A2sF32 -> "F"
        A2sF64 -> "D"
        A2sBoolean -> "Z"
        else -> "Ljava/lang/Object;"
    }

    fun isPrimitive(type: A2sType): Boolean = type in PRIMITIVE_TYPES

    /** 将栈顶的装箱值拆箱为原生类型（仅定长类型生效） */
    fun unbox(mv: MethodVisitor, type: A2sType) {
        when (type) {
            A2sI32, A2sU32 -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer")
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)
            }
            A2sI64, A2sU64 -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Long")
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false)
            }
            A2sF32 -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Float")
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false)
            }
            A2sF64 -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Double")
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false)
            }
            A2sBoolean -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean")
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
            }
            else -> {}
        }
    }

    /** 将栈顶的原生值装箱为包装类型 */
    fun box(mv: MethodVisitor, type: A2sType) {
        when (type) {
            A2sI32, A2sU32 -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
            A2sI64, A2sU64 -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
            A2sF32 -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false)
            A2sF64 -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false)
            A2sBoolean -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false)
            else -> {}
        }
    }

    private val PRIMITIVE_TYPES = setOf(A2sI32, A2sI64, A2sU32, A2sU64, A2sF32, A2sF64, A2sBoolean)

    /** 数值二元运算的提升类型 */
    fun promoteNumeric(a: A2sType, b: A2sType): A2sType = when {
        a == A2sRational || b == A2sRational -> A2sRational
        a == A2sBigInt || b == A2sBigInt -> A2sBigInt
        a == A2sF64 || b == A2sF64 -> A2sF64
        a == A2sF32 || b == A2sF32 -> A2sF32
        a == A2sI64 || b == A2sI64 -> A2sI64
        a == A2sU64 || b == A2sU64 -> A2sU64
        else -> A2sI32
    }
}
