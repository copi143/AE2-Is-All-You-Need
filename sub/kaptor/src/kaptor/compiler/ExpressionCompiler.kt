package kaptor.compiler

import kaptor.ir.*
import org.objectweb.asm.Opcodes.*

fun compileExpression(ctx: MethodContext, expr: IrExpression) {
    val mv = ctx.mv

    when (expr) {
        is IrStringLiteral -> {
            mv.visitLdcInsn(expr.value)
        }

        is IrIntLiteral -> compileIntLiteral(ctx, expr.value)
        is IrLongLiteral -> compileLongLiteral(ctx, expr.value)
        is IrFloatLiteral -> compileFloatLiteral(ctx, expr)

        is IrBoolLiteral -> {
            mv.visitInsn(if (expr.value) ICONST_1 else ICONST_0)
            boxTop(ctx, IrBoolType)
        }

        is IrNullLiteral -> {
            mv.visitInsn(ACONST_NULL)
        }

        is IrIdentifier -> {
            mv.visitVarInsn(ALOAD, ctx.getLocal(expr.name))
        }

        is IrFieldAccess -> compileFieldAccess(ctx, expr)

        is IrMethodCall -> {
            compileExpression(ctx, expr.receiver)
            for (arg in expr.arguments) {
                compileExpression(ctx, arg)
            }
            val argTypes = expr.arguments.joinToString("") { "Ljava/lang/Object;" }
            mv.visitMethodInsn(
                INVOKEVIRTUAL, "java/lang/Object", expr.methodName, "(${argTypes})Ljava/lang/Object;", false
            )
        }

        is IrFunctionCall -> compileFunctionCall(ctx, expr)

        is IrBinaryExpression -> {
            compileExpression(ctx, expr.left)
            compileExpression(ctx, expr.right)
            compileTypedBinaryOp(ctx, expr)
        }

        is IrUnaryExpression -> {
            compileExpression(ctx, expr.operand)
            compileTypedUnaryOp(ctx, expr)
        }

        is IrStringInterpolation -> compileStringInterpolation(ctx, expr)

        is IrIndexAccess -> {
            compileExpression(ctx, expr.receiver)
            compileExpression(ctx, expr.index)
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true)
        }

        is IrMergedExpression -> {
            for (e in expr.expressions) {
                compileExpression(ctx, e)
            }
        }
    }
}

private fun compileIntLiteral(ctx: MethodContext, value: Int) {
    val mv = ctx.mv
    when (value) {
        0 -> mv.visitInsn(ICONST_0)
        1 -> mv.visitInsn(ICONST_1)
        2 -> mv.visitInsn(ICONST_2)
        3 -> mv.visitInsn(ICONST_3)
        4 -> mv.visitInsn(ICONST_4)
        5 -> mv.visitInsn(ICONST_5)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> mv.visitIntInsn(BIPUSH, value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> mv.visitIntInsn(SIPUSH, value)
        else -> mv.visitLdcInsn(value)
    }
    boxTop(ctx, IrIntType)
}

private fun compileLongLiteral(ctx: MethodContext, value: Long) {
    val mv = ctx.mv
    when (value) {
        0L -> mv.visitInsn(LCONST_0)
        1L -> mv.visitInsn(LCONST_1)
        else -> mv.visitLdcInsn(value)
    }
    boxTop(ctx, IrLongType)
}

private fun compileFloatLiteral(ctx: MethodContext, expr: IrFloatLiteral) {
    val mv = ctx.mv
    if (expr.numericType == IrFloatType) {
        mv.visitLdcInsn(expr.value.toFloat())
        boxTop(ctx, IrFloatType)
    } else {
        mv.visitLdcInsn(expr.value)
        boxTop(ctx, IrDoubleType)
    }
}

private fun compileFieldAccess(ctx: MethodContext, expr: IrFieldAccess) {
    val mv = ctx.mv
    compileExpression(ctx, expr.receiver)
    if (ctx.eventClassName != null && ctx.isEventVariable(expr.receiver)) {
        mv.visitTypeInsn(CHECKCAST, ctx.eventClassName)
        val getterName = "get${expr.fieldName.replaceFirstChar { it.uppercase() }}"
        mv.visitMethodInsn(INVOKEVIRTUAL, ctx.eventClassName, getterName, "()Ljava/lang/Object;", false)
    } else {
        mv.visitTypeInsn(CHECKCAST, "java/util/Map")
        mv.visitLdcInsn(expr.fieldName)
        mv.visitMethodInsn(
            INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true
        )
    }
}

private fun compileFunctionCall(ctx: MethodContext, expr: IrFunctionCall) {
    val mv = ctx.mv
    when (expr.name) {
        "println" -> {
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
            if (expr.arguments.isNotEmpty()) {
                compileExpression(ctx, expr.arguments[0])
            } else {
                mv.visitLdcInsn("")
            }
            mv.visitMethodInsn(
                INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false
            )
            mv.visitInsn(ACONST_NULL)
        }

        "toString" -> {
            if (expr.arguments.isNotEmpty()) {
                compileExpression(ctx, expr.arguments[0])
            } else {
                mv.visitInsn(ACONST_NULL)
            }
            mv.visitMethodInsn(
                INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false
            )
        }

        "toInt" -> {
            if (expr.arguments.isNotEmpty()) {
                compileExpression(ctx, expr.arguments[0])
            }
            mv.visitMethodInsn(
                INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false
            )
            boxTop(ctx, IrIntType)
        }

        "len" -> {
            if (expr.arguments.isNotEmpty()) {
                compileExpression(ctx, expr.arguments[0])
            }
            mv.visitMethodInsn(
                INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false
            )
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            boxTop(ctx, IrIntType)
        }

        "listOf" -> {
            mv.visitTypeInsn(NEW, "java/util/ArrayList")
            mv.visitInsn(DUP)
            mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            for (arg in expr.arguments) {
                mv.visitInsn(DUP)
                compileExpression(ctx, arg)
                mv.visitMethodInsn(
                    INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true
                )
                mv.visitInsn(POP)
            }
        }

        else -> {
            mv.visitVarInsn(ALOAD, 0)
            for (arg in expr.arguments) {
                compileExpression(ctx, arg)
            }
            val argTypes = expr.arguments.joinToString("") { "Ljava/lang/Object;" }
            mv.visitMethodInsn(
                INVOKEVIRTUAL, TYPE_HANDLER_BASE, expr.name, "(${argTypes})Ljava/lang/Object;", false
            )
        }
    }
}

private fun compileStringInterpolation(ctx: MethodContext, expr: IrStringInterpolation) {
    val mv = ctx.mv
    mv.visitTypeInsn(NEW, "java/lang/StringBuilder")
    mv.visitInsn(DUP)
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)

    for (part in expr.parts) {
        when (part) {
            is IrLiteralPart -> {
                mv.visitLdcInsn(part.text)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
            }

            is IrExpressionPart -> {
                compileExpression(ctx, part.expr)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
                    false
                )
            }
        }
    }

    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
}
