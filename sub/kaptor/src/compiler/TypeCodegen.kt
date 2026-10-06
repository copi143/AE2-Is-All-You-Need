package kaptor.compiler

import kaptor.ir.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

fun unboxTop(ctx: MethodContext, type: IrType) {
    when (type) {
        IrIntType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)
        IrLongType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false)
        IrFloatType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false)
        IrDoubleType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false)
        IrBoolType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
        else -> {}
    }
}

fun boxTop(ctx: MethodContext, type: IrType) {
    when (type) {
        IrIntType -> ctx.mv.visitMethodInsn(
            INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false
        )

        IrLongType -> ctx.mv.visitMethodInsn(
            INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false
        )

        IrFloatType -> ctx.mv.visitMethodInsn(
            INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false
        )

        IrDoubleType -> ctx.mv.visitMethodInsn(
            INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false
        )

        IrBoolType -> ctx.mv.visitMethodInsn(
            INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false
        )

        else -> {}
    }
}

fun convertTo(ctx: MethodContext, from: IrType, to: IrType) {
    if (from == to) return
    when (from) {
        IrIntType -> when (to) {
            IrLongType -> ctx.mv.visitInsn(I2L)
            IrFloatType -> ctx.mv.visitInsn(I2F)
            IrDoubleType -> ctx.mv.visitInsn(I2D)
            else -> {}
        }

        IrLongType -> when (to) {
            IrIntType -> ctx.mv.visitInsn(L2I)
            IrFloatType -> ctx.mv.visitInsn(L2F)
            IrDoubleType -> ctx.mv.visitInsn(L2D)
            else -> {}
        }

        IrFloatType -> when (to) {
            IrIntType -> ctx.mv.visitInsn(F2I)
            IrLongType -> ctx.mv.visitInsn(F2L)
            IrDoubleType -> ctx.mv.visitInsn(F2D)
            else -> {}
        }

        IrDoubleType -> when (to) {
            IrIntType -> ctx.mv.visitInsn(D2I)
            IrLongType -> ctx.mv.visitInsn(D2L)
            IrFloatType -> ctx.mv.visitInsn(D2F)
            else -> {}
        }

        else -> {}
    }
}

fun promoteType(a: IrType, b: IrType): IrType {
    val order = listOf(IrIntType, IrLongType, IrFloatType, IrDoubleType)
    val ai = order.indexOf(a)
    val bi = order.indexOf(b)
    if (ai == -1 && bi == -1) return IrObjectType
    if (ai == -1) return b
    if (bi == -1) return a
    return if (ai >= bi) a else b
}

fun inferType(expr: IrExpression): IrType = when (expr) {
    is IrIntLiteral -> IrIntType
    is IrLongLiteral -> IrLongType
    is IrFloatLiteral -> expr.numericType
    is IrBoolLiteral -> IrBoolType
    is IrStringLiteral -> IrStringType
    is IrNullLiteral -> IrObjectType
    is IrIdentifier -> expr.type
    is IrFieldAccess -> expr.fieldType
    is IrBinaryExpression -> expr.resultType
    is IrUnaryExpression -> expr.resultType
    is IrStringInterpolation -> IrStringType
    is IrFunctionCall -> IrObjectType
    is IrMethodCall -> IrObjectType
    is IrIndexAccess -> IrObjectType
    is IrMergedExpression -> IrObjectType
}

fun MethodVisitor.pushBoolean(jumpOpcode: Int, pushOnTrue: Boolean) {
    val trueLabel = Label()
    val endLabel = Label()
    visitJumpInsn(jumpOpcode, trueLabel)
    visitInsn(if (pushOnTrue) ICONST_1 else ICONST_0)
    visitJumpInsn(GOTO, endLabel)
    visitLabel(trueLabel)
    visitInsn(if (pushOnTrue) ICONST_0 else ICONST_1)
    visitLabel(endLabel)
}
