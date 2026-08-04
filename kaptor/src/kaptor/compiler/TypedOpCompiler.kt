package kaptor.compiler

import kaptor.ir.*
import org.objectweb.asm.Opcodes.*

private val objectOpNames = mapOf(
    BinaryOperator.PLUS to "add",
    BinaryOperator.MINUS to "sub",
    BinaryOperator.MULTIPLY to "mul",
    BinaryOperator.DIVIDE to "div",
    BinaryOperator.MODULO to "mod",
    BinaryOperator.BIT_AND to "bitAnd",
    BinaryOperator.BIT_OR to "bitOr",
    BinaryOperator.BIT_XOR to "bitXor",
    BinaryOperator.SHL to "shl",
    BinaryOperator.SHR to "shr",
)

private val booleanOpNames = mapOf(
    BinaryOperator.EQUALS to "equals",
    BinaryOperator.NOT_EQUALS to "notEquals",
    BinaryOperator.LESS to "lessThan",
    BinaryOperator.LESS_EQUAL to "lessEqual",
    BinaryOperator.GREATER to "greaterThan",
    BinaryOperator.GREATER_EQUAL to "greaterEqual",
)

fun compileTypedBinaryOp(ctx: MethodContext, expr: IrBinaryExpression) {
    val op = expr.operator
    val resultType = expr.resultType

    val leftType = inferType(expr.left)
    val rightType = inferType(expr.right)

    if (op == BinaryOperator.PLUS && resultType == IrStringType) {
        compileStringConcat(ctx)
        return
    }

    if (resultType == IrObjectType) {
        compileBinaryOpFallback(ctx, op)
        return
    }

    when (op) {
        BinaryOperator.PLUS, BinaryOperator.MINUS, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.MODULO -> {
            compilePrimitiveArithmetic(ctx, op, leftType, rightType)
        }

        BinaryOperator.EQUALS, BinaryOperator.NOT_EQUALS, BinaryOperator.LESS, BinaryOperator.LESS_EQUAL, BinaryOperator.GREATER, BinaryOperator.GREATER_EQUAL -> {
            if (leftType != IrObjectType && rightType != IrObjectType) {
                compilePrimitiveComparison(ctx, op, leftType, rightType)
            } else if (op == BinaryOperator.EQUALS || op == BinaryOperator.NOT_EQUALS) {
                compileObjectEquals(ctx, op)
            } else {
                compileBinaryOpFallback(ctx, op)
            }
        }

        BinaryOperator.BIT_AND, BinaryOperator.BIT_OR, BinaryOperator.BIT_XOR, BinaryOperator.SHL, BinaryOperator.SHR -> {
            compilePrimitiveBitwise(ctx, op, leftType, rightType)
        }

        BinaryOperator.AND, BinaryOperator.OR -> {
            compileLogicalOp(ctx, op)
        }
    }
}

fun compileTypedUnaryOp(ctx: MethodContext, expr: IrUnaryExpression) {
    val op = expr.operator
    val operandType = inferType(expr.operand)
    val mv = ctx.mv

    when (op) {
        UnaryOperator.MINUS -> {
            if (operandType != IrObjectType) {
                unboxTop(ctx, operandType)
                when (operandType) {
                    IrIntType -> mv.visitInsn(INEG)
                    IrLongType -> mv.visitInsn(LNEG)
                    IrFloatType -> mv.visitInsn(FNEG)
                    IrDoubleType -> mv.visitInsn(DNEG)
                    else -> {}
                }
                boxTop(ctx, operandType)
            } else {
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    TYPE_SCRIPT_RUNTIME,
                    "neg",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false
                )
            }
        }

        UnaryOperator.NOT -> {
            if (operandType != IrObjectType) {
                unboxTop(ctx, operandType)
                if (operandType == IrBoolType) {
                    mv.pushBoolean(IFNE, false)
                } else {
                    mv.visitInsn(ICONST_1)
                    mv.visitInsn(ICONST_1)
                    mv.visitInsn(ISUB)
                }
                boxTop(ctx, IrBoolType)
            } else {
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    TYPE_SCRIPT_RUNTIME,
                    "not",
                    "(Ljava/lang/Object;)Ljava/lang/Boolean;",
                    false
                )
            }
        }

        UnaryOperator.BIT_NOT -> {
            if (operandType != IrObjectType) {
                unboxTop(ctx, operandType)
                when (operandType) {
                    IrIntType -> {
                        mv.visitInsn(ICONST_M1); mv.visitInsn(IXOR)
                    }

                    IrLongType -> {
                        mv.visitLdcInsn(-1L); mv.visitInsn(LXOR)
                    }

                    else -> {
                        mv.visitInsn(ICONST_M1); mv.visitInsn(IXOR)
                    }
                }
                boxTop(ctx, operandType)
            } else {
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    TYPE_SCRIPT_RUNTIME,
                    "bitNot",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false
                )
            }
        }
    }
}

fun compilePrimitiveArithmetic(ctx: MethodContext, op: BinaryOperator, leftType: IrType, rightType: IrType) {
    val promotedType = withPromotedOperands(ctx, leftType, rightType)
    val mv = ctx.mv

    when (promotedType) {
        IrIntType -> when (op) {
            BinaryOperator.PLUS -> mv.visitInsn(IADD)
            BinaryOperator.MINUS -> mv.visitInsn(ISUB)
            BinaryOperator.MULTIPLY -> mv.visitInsn(IMUL)
            BinaryOperator.DIVIDE -> mv.visitInsn(IDIV)
            BinaryOperator.MODULO -> mv.visitInsn(IREM)
            else -> {}
        }

        IrLongType -> when (op) {
            BinaryOperator.PLUS -> mv.visitInsn(LADD)
            BinaryOperator.MINUS -> mv.visitInsn(LSUB)
            BinaryOperator.MULTIPLY -> mv.visitInsn(LMUL)
            BinaryOperator.DIVIDE -> mv.visitInsn(LDIV)
            BinaryOperator.MODULO -> mv.visitInsn(LREM)
            else -> {}
        }

        IrFloatType -> when (op) {
            BinaryOperator.PLUS -> mv.visitInsn(FADD)
            BinaryOperator.MINUS -> mv.visitInsn(FSUB)
            BinaryOperator.MULTIPLY -> mv.visitInsn(FMUL)
            BinaryOperator.DIVIDE -> mv.visitInsn(FDIV)
            BinaryOperator.MODULO -> mv.visitInsn(FREM)
            else -> {}
        }

        IrDoubleType -> when (op) {
            BinaryOperator.PLUS -> mv.visitInsn(DADD)
            BinaryOperator.MINUS -> mv.visitInsn(DSUB)
            BinaryOperator.MULTIPLY -> mv.visitInsn(DMUL)
            BinaryOperator.DIVIDE -> mv.visitInsn(DDIV)
            BinaryOperator.MODULO -> mv.visitInsn(DREM)
            else -> {}
        }

        else -> {}
    }
    boxTop(ctx, promotedType)
}

fun compilePrimitiveComparison(ctx: MethodContext, op: BinaryOperator, leftType: IrType, rightType: IrType) {
    val promotedType = withPromotedOperands(ctx, leftType, rightType)
    val mv = ctx.mv

    when (promotedType) {
        IrIntType -> {
            val jumpOp = when (op) {
                BinaryOperator.EQUALS -> IF_ICMPEQ
                BinaryOperator.NOT_EQUALS -> IF_ICMPNE
                BinaryOperator.LESS -> IF_ICMPLT
                BinaryOperator.LESS_EQUAL -> IF_ICMPLE
                BinaryOperator.GREATER -> IF_ICMPGT
                BinaryOperator.GREATER_EQUAL -> IF_ICMPGE
                else -> IF_ICMPEQ
            }
            mv.pushBoolean(jumpOp, true)
        }

        IrLongType, IrFloatType, IrDoubleType -> {
            val cmpInsn = when (promotedType) {
                IrLongType -> LCMP
                IrFloatType -> FCMPG
                else -> DCMPG
            }
            mv.visitInsn(cmpInsn)
            val jumpOp = when (op) {
                BinaryOperator.EQUALS -> IFEQ
                BinaryOperator.NOT_EQUALS -> IFNE
                BinaryOperator.LESS -> IFLT
                BinaryOperator.LESS_EQUAL -> IFLE
                BinaryOperator.GREATER -> IFGT
                BinaryOperator.GREATER_EQUAL -> IFGE
                else -> IFEQ
            }
            mv.pushBoolean(jumpOp, true)
        }

        else -> {}
    }

    boxTop(ctx, IrBoolType)
}

fun compileObjectEquals(ctx: MethodContext, op: BinaryOperator) {
    ctx.mv.visitMethodInsn(
        INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false
    )
    if (op == BinaryOperator.NOT_EQUALS) {
        ctx.mv.pushBoolean(IFNE, false)
    }
    boxTop(ctx, IrBoolType)
}

fun compilePrimitiveBitwise(ctx: MethodContext, op: BinaryOperator, leftType: IrType, rightType: IrType) {
    val promotedType = withPromotedOperands(ctx, leftType, rightType)
    val mv = ctx.mv

    when (promotedType) {
        IrIntType -> when (op) {
            BinaryOperator.BIT_AND -> mv.visitInsn(IAND)
            BinaryOperator.BIT_OR -> mv.visitInsn(IOR)
            BinaryOperator.BIT_XOR -> mv.visitInsn(IXOR)
            BinaryOperator.SHL -> mv.visitInsn(ISHL)
            BinaryOperator.SHR -> mv.visitInsn(ISHR)
            else -> {}
        }

        IrLongType -> when (op) {
            BinaryOperator.BIT_AND -> mv.visitInsn(LAND)
            BinaryOperator.BIT_OR -> mv.visitInsn(LOR)
            BinaryOperator.BIT_XOR -> mv.visitInsn(LXOR)
            BinaryOperator.SHL -> mv.visitInsn(LSHL)
            BinaryOperator.SHR -> mv.visitInsn(LSHR)
            else -> {}
        }

        else -> {}
    }
    boxTop(ctx, promotedType)
}

fun compileStringConcat(ctx: MethodContext) {
    ctx.mv.visitTypeInsn(NEW, "java/lang/StringBuilder")
    ctx.mv.visitInsn(DUP)
    ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
    ctx.mv.visitInsn(SWAP)
    ctx.mv.visitMethodInsn(
        INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false
    )
    ctx.mv.visitInsn(SWAP)
    ctx.mv.visitMethodInsn(
        INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false
    )
    ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
}

fun compileLogicalOp(ctx: MethodContext, op: BinaryOperator) {
    val mv = ctx.mv
    mv.visitInsn(SWAP)
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
    mv.visitInsn(SWAP)
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)

    if (op == BinaryOperator.AND) {
        mv.pushBoolean(IFEQ, true)
    } else {
        mv.pushBoolean(IFNE, true)
    }
    boxTop(ctx, IrBoolType)
}

fun compileBinaryOpFallback(ctx: MethodContext, op: BinaryOperator) {
    when (op) {
        BinaryOperator.AND, BinaryOperator.OR -> compileLogicalOp(ctx, op)
        else -> {
            val methodName = objectOpNames[op] ?: booleanOpNames[op]
            if (methodName != null) {
                val desc = if (op in booleanOpNames) {
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;"
                } else {
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                }
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_SCRIPT_RUNTIME, methodName, desc, false)
            }
        }
    }
}

private fun withPromotedOperands(ctx: MethodContext, leftType: IrType, rightType: IrType): IrType {
    val promotedType = promoteType(leftType, rightType)
    val rightLocal = ctx.declareLocal("__op_right", "Ljava/lang/Object;")
    ctx.mv.visitVarInsn(ASTORE, rightLocal)
    unboxTop(ctx, leftType)
    convertTo(ctx, leftType, promotedType)
    ctx.mv.visitVarInsn(ALOAD, rightLocal)
    unboxTop(ctx, rightType)
    convertTo(ctx, rightType, promotedType)
    return promotedType
}
