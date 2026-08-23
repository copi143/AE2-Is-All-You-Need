package kaptor.a2s.compiler

import kaptor.a2s.ir.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*

/**
 * 表达式编译器：把 A2sExpr 编译为字节码，结果以装箱形式压栈。
 *
 * 依赖 [A2sCompileContext] 解析变量槽号与类型。
 */
class A2sExprCompiler(private val symbols: A2sSymbolTable) {

    lateinit var classCompiler: A2sClassCompiler

    fun compile(ctx: A2sCompileContext, expr: A2sExpr) {
        when (expr) {
            is A2sBigIntLiteral -> compileBigInt(ctx, expr.value)
            is A2sRationalLiteral -> compileRational(ctx, expr.value)
            is A2sI32Literal -> { ctx.mv.visitLdcInsn(expr.value); A2sTypeCodegen.box(ctx.mv, A2sI32) }
            is A2sI64Literal -> { ctx.mv.visitLdcInsn(expr.value); A2sTypeCodegen.box(ctx.mv, A2sI64) }
            is A2sF32Literal -> { ctx.mv.visitLdcInsn(expr.value); A2sTypeCodegen.box(ctx.mv, A2sF32) }
            is A2sF64Literal -> { ctx.mv.visitLdcInsn(expr.value); A2sTypeCodegen.box(ctx.mv, A2sF64) }
            is A2sBoolLiteral -> { ctx.mv.visitInsn(if (expr.value) ICONST_1 else ICONST_0); A2sTypeCodegen.box(ctx.mv, A2sBoolean) }
            is A2sStringLiteral -> ctx.mv.visitLdcInsn(expr.value)
            is A2sNullLiteral -> ctx.mv.visitInsn(ACONST_NULL)
            is A2sIdentifier -> compileIdentifier(ctx, expr)
            is A2sResourceRef -> compileResourceRef(ctx, expr)
            is A2sStringInterpolation -> compileStringInterpolation(ctx, expr)
            is A2sBinary -> compileBinary(ctx, expr)
            is A2sUnary -> compileUnary(ctx, expr)
            is A2sFieldAccess -> compileFieldAccess(ctx, expr)
            is A2sCall -> compileCall(ctx, expr)
            is A2sMethodCall -> compileMethodCall(ctx, expr)
            is A2sIndexAccess -> compileIndexAccess(ctx, expr)
            is A2sElvis -> compileElvis(ctx, expr)
            is A2sNotNull -> compileNotNull(ctx, expr)
            is A2sLambda -> compileLambda(ctx, expr)
            is A2sIfExpr -> throw A2sCompileError("if 表达式不支持求值")
            is A2sWhenExpr -> throw A2sCompileError("when 表达式不支持求值")
        }
    }

    /** 编译标识符：局部变量走 ALOAD，事件字段走 GETFIELD（通过 scriptObjSlot），顶层变量走 GETFIELD（通过 scriptObjSlot）。 */
    private fun compileIdentifier(ctx: A2sCompileContext, expr: A2sIdentifier) {
        when {
            ctx.hasLocal(expr.name) -> ctx.loadVariable(expr.name)
            ctx.isEventField(expr.name) -> {
                ctx.mv.visitVarInsn(ALOAD, ctx.scriptObjSlot)
                val desc = A2sTypeCodegen.boxedDescriptor(ctx.eventFieldType(expr.name))
                ctx.mv.visitFieldInsn(GETFIELD, ctx.className, expr.name, desc)
            }

            symbols.isTopLevelVar(expr.name) -> {
                val desc = A2sTypeCodegen.boxedDescriptor(symbols.topLevelVarType(expr.name))
                ctx.mv.visitVarInsn(ALOAD, ctx.scriptObjSlot)
                ctx.mv.visitFieldInsn(GETFIELD, ctx.className, expr.name, desc)
            }

            else -> throw A2sCompileError("未定义的变量: ${expr.name}")
        }
    }

    private fun compileBigInt(ctx: A2sCompileContext, value: String) {
        ctx.mv.visitTypeInsn(NEW, "java/math/BigInteger")
        ctx.mv.visitInsn(DUP)
        ctx.mv.visitLdcInsn(value)
        ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/math/BigInteger", "<init>", "(Ljava/lang/String;)V", false)
    }

    private fun compileRational(ctx: A2sCompileContext, value: String) {
        ctx.mv.visitLdcInsn(value)
        ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RATIONAL, "fromDecimalString", "(Ljava/lang/String;)L$TYPE_RATIONAL;", false)
    }

    private fun compileResourceRef(ctx: A2sCompileContext, expr: A2sResourceRef) {
        ctx.mv.visitLdcInsn(expr.raw)
        ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "resolveResource", "(Ljava/lang/String;)Ljava/lang/Object;", false)
    }

    private fun compileStringInterpolation(ctx: A2sCompileContext, expr: A2sStringInterpolation) {
        ctx.mv.visitTypeInsn(NEW, "java/lang/StringBuilder")
        ctx.mv.visitInsn(DUP)
        ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
        for (part in expr.parts) {
            when (part) {
                is A2sStrText -> {
                    ctx.mv.visitLdcInsn(part.text)
                    ctx.mv.visitMethodInsn(
                        INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false
                    )
                }

                is A2sStrExpr -> {
                    compile(ctx, part.expr)
                    ctx.mv.visitMethodInsn(
                        INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false
                    )
                }
            }
        }
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
    }

    private fun compileBinary(ctx: A2sCompileContext, expr: A2sBinary) {
        when (expr.op) {
            A2sBinaryOp.AND -> compileLogical(ctx, expr, and = true)
            A2sBinaryOp.OR -> compileLogical(ctx, expr, and = false)
            A2sBinaryOp.PLUS -> {
                if (isStringConcat(ctx, expr)) compileStringConcat(ctx, expr)
                else compileArithmetic(ctx, expr, "+")
            }

            A2sBinaryOp.MINUS -> compileArithmetic(ctx, expr, "-")
            A2sBinaryOp.MULTIPLY -> compileArithmetic(ctx, expr, "*")
            A2sBinaryOp.DIVIDE -> compileArithmetic(ctx, expr, "/")
            A2sBinaryOp.MODULO -> compileArithmetic(ctx, expr, "%")
            A2sBinaryOp.EQUALS -> compileComparison(ctx, expr, "==")
            A2sBinaryOp.NOT_EQUALS -> compileComparison(ctx, expr, "!=")
            A2sBinaryOp.LESS -> compileComparison(ctx, expr, "<")
            A2sBinaryOp.LESS_EQUAL -> compileComparison(ctx, expr, "<=")
            A2sBinaryOp.GREATER -> compileComparison(ctx, expr, ">")
            A2sBinaryOp.GREATER_EQUAL -> compileComparison(ctx, expr, ">=")
            A2sBinaryOp.RANGE -> {
                // a..b → new A2sRange(toLong(a), toLong(b))
                compile(ctx, expr.left)
                val leftTmp = ctx.allocateTemp()
                ctx.mv.visitVarInsn(ASTORE, leftTmp)
                compile(ctx, expr.right)
                val rightTmp = ctx.allocateTemp()
                ctx.mv.visitVarInsn(ASTORE, rightTmp)
                // NEW A2sRange, DUP, load left, unbox, load right, unbox, <init>
                ctx.mv.visitTypeInsn(NEW, "kaptor/a2s/runtime/A2sRange")
                ctx.mv.visitInsn(DUP)
                ctx.mv.visitVarInsn(ALOAD, leftTmp)
                A2sTypeCodegen.unbox(ctx.mv, A2sI64)
                ctx.mv.visitVarInsn(ALOAD, rightTmp)
                A2sTypeCodegen.unbox(ctx.mv, A2sI64)
                ctx.mv.visitMethodInsn(
                    INVOKESPECIAL, "kaptor/a2s/runtime/A2sRange", "<init>", "(JJ)V", false
                )
            }
        }
    }

    private fun isStringConcat(ctx: A2sCompileContext, expr: A2sBinary): Boolean {
        val lt = symbols.inferType(expr.left, ctx.localTypes())
        val rt = symbols.inferType(expr.right, ctx.localTypes())
        return lt == A2sString || rt == A2sString
    }

    private fun compileArithmetic(ctx: A2sCompileContext, expr: A2sBinary, op: String) {
        val lt = symbols.inferType(expr.left, ctx.localTypes())
        val rt = symbols.inferType(expr.right, ctx.localTypes())
        val numericPromoted = A2sTypeCodegen.promoteNumeric(lt, rt)
        // BigInt 除法得到 Rational（精确分数）
        val promoted = if (op == "/" && numericPromoted == A2sBigInt) A2sRational else numericPromoted

        // 先编译右操作数（存临时引用槽），再编译左操作数
        compile(ctx, expr.right)
        val tmp = ctx.allocateTemp()
        ctx.mv.visitVarInsn(ASTORE, tmp)
        compile(ctx, expr.left)

        when (promoted) {
            A2sBigInt -> {
                convertToBigInt(ctx, lt)
                ctx.mv.visitVarInsn(ALOAD, tmp)
                convertToBigInt(ctx, rt)
                ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigInteger", bigIntOp(op), "(Ljava/math/BigInteger;)Ljava/math/BigInteger;", false)
            }

            A2sRational -> {
                convertToRational(ctx, lt)
                ctx.mv.visitVarInsn(ALOAD, tmp)
                convertToRational(ctx, rt)
                ctx.mv.visitMethodInsn(INVOKEVIRTUAL, TYPE_RATIONAL, rationalOp(op), "(L$TYPE_RATIONAL;)L$TYPE_RATIONAL;", false)
            }

            else -> {
                A2sTypeCodegen.unbox(ctx.mv, promoted)
                ctx.mv.visitVarInsn(ALOAD, tmp)
                A2sTypeCodegen.unbox(ctx.mv, promoted)
                primitiveArithmetic(ctx.mv, op, promoted)
                A2sTypeCodegen.box(ctx.mv, promoted)
            }
        }
    }

    /** 将栈顶的定长装箱值转换为 BigInteger（BigInt 类型本身不动）。 */
    private fun convertToBigInt(ctx: A2sCompileContext, type: A2sType) {
        when (type) {
            A2sI32, A2sU32 -> {
                A2sTypeCodegen.unbox(ctx.mv, A2sI32)
                ctx.mv.visitInsn(I2L)
                ctx.mv.visitMethodInsn(INVOKESTATIC, "java/math/BigInteger", "valueOf", "(J)Ljava/math/BigInteger;", false)
            }

            A2sI64, A2sU64 -> {
                A2sTypeCodegen.unbox(ctx.mv, A2sI64)
                ctx.mv.visitMethodInsn(INVOKESTATIC, "java/math/BigInteger", "valueOf", "(J)Ljava/math/BigInteger;", false)
            }

            else -> {
                ctx.mv.visitTypeInsn(CHECKCAST, "java/math/BigInteger")
            }
        }
    }

    /** 将栈顶的值转换为 Rational（BigInt 或定长类型转换，Rational 不动）。 */
    private fun convertToRational(ctx: A2sCompileContext, type: A2sType) {
        when (type) {
            A2sBigInt -> {
                ctx.mv.visitTypeInsn(CHECKCAST, "java/math/BigInteger")
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RATIONAL, "of", "(Ljava/math/BigInteger;)L$TYPE_RATIONAL;", false)
            }

            A2sI32, A2sU32 -> {
                A2sTypeCodegen.unbox(ctx.mv, A2sI32)
                ctx.mv.visitInsn(I2L)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RATIONAL, "of", "(J)L$TYPE_RATIONAL;", false)
            }

            A2sI64, A2sU64 -> {
                A2sTypeCodegen.unbox(ctx.mv, A2sI64)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RATIONAL, "of", "(J)L$TYPE_RATIONAL;", false)
            }

            else -> {}
        }
    }

    private fun bigIntOp(op: String) = when (op) {
        "+" -> "add"; "-" -> "subtract"; "*" -> "multiply"; "/" -> "divide"; "%" -> "remainder"; else -> "add"
    }

    private fun rationalOp(op: String) = when (op) {
        "+" -> "add"; "-" -> "sub"; "*" -> "mul"; "/" -> "div"; "%" -> "mod"; else -> "add"
    }

    private fun primitiveArithmetic(mv: org.objectweb.asm.MethodVisitor, op: String, t: A2sType) {
        when (t) {
            A2sI32, A2sU32 -> when (op) {
                "+" -> mv.visitInsn(IADD); "-" -> mv.visitInsn(ISUB); "*" -> mv.visitInsn(IMUL)
                "/" -> mv.visitInsn(IDIV); "%" -> mv.visitInsn(IREM)
            }

            A2sI64, A2sU64 -> when (op) {
                "+" -> mv.visitInsn(LADD); "-" -> mv.visitInsn(LSUB); "*" -> mv.visitInsn(LMUL)
                "/" -> mv.visitInsn(LDIV); "%" -> mv.visitInsn(LREM)
            }

            A2sF32 -> when (op) {
                "+" -> mv.visitInsn(FADD); "-" -> mv.visitInsn(FSUB); "*" -> mv.visitInsn(FMUL)
                "/" -> mv.visitInsn(FDIV); "%" -> mv.visitInsn(FREM)
            }

            A2sF64 -> when (op) {
                "+" -> mv.visitInsn(DADD); "-" -> mv.visitInsn(DSUB); "*" -> mv.visitInsn(DMUL)
                "/" -> mv.visitInsn(DDIV); "%" -> mv.visitInsn(DREM)
            }

            else -> {}
        }
    }

    private fun compileComparison(ctx: A2sCompileContext, expr: A2sBinary, op: String) {
        val lt = symbols.inferType(expr.left, ctx.localTypes())
        val rt = symbols.inferType(expr.right, ctx.localTypes())
        val promoted = A2sTypeCodegen.promoteNumeric(lt, rt)

        // 编译右操作数存临时槽，再编译左操作数
        compile(ctx, expr.right)
        val tmp = ctx.allocateTemp()
        ctx.mv.visitVarInsn(ASTORE, tmp)
        compile(ctx, expr.left)

        when (promoted) {
            A2sBigInt -> {
                convertToBigInt(ctx, lt)
                ctx.mv.visitVarInsn(ALOAD, tmp)
                convertToBigInt(ctx, rt)
                ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigInteger", "compareTo", "(Ljava/math/BigInteger;)I", false)
                compileIntCompare(ctx.mv, op)
            }

            A2sRational -> {
                convertToRational(ctx, lt)
                ctx.mv.visitVarInsn(ALOAD, tmp)
                convertToRational(ctx, rt)
                ctx.mv.visitMethodInsn(INVOKEVIRTUAL, TYPE_RATIONAL, "compareTo", "(L$TYPE_RATIONAL;)I", false)
                compileIntCompare(ctx.mv, op)
            }

            else -> {
                // 定长类型：拆箱转 long 比较
                A2sTypeCodegen.unbox(ctx.mv, promoted)
                toLong(ctx.mv, promoted)
                ctx.mv.visitVarInsn(ALOAD, tmp)
                A2sTypeCodegen.unbox(ctx.mv, promoted)
                toLong(ctx.mv, promoted)
                ctx.mv.visitInsn(LCMP)
                compileIntCompare(ctx.mv, op)
            }
        }
    }

    private fun toLong(mv: org.objectweb.asm.MethodVisitor, t: A2sType) {
        when (t) {
            A2sI32, A2sU32 -> mv.visitInsn(I2L)
            A2sF32 -> mv.visitInsn(F2L)
            A2sF64 -> mv.visitInsn(D2L)
            else -> {}
        }
    }

    private fun compileIntCompare(mv: org.objectweb.asm.MethodVisitor, op: String) {
        val jumpOp = when (op) {
            "==" -> IFEQ; "!=" -> IFNE; "<" -> IFLT; "<=" -> IFLE; ">" -> IFGT; ">=" -> IFGE
            else -> IFEQ
        }
        val trueLabel = Label()
        val endLabel = Label()
        mv.visitJumpInsn(jumpOp, trueLabel)
        mv.visitInsn(ICONST_0)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(trueLabel)
        mv.visitInsn(ICONST_1)
        mv.visitLabel(endLabel)
        A2sTypeCodegen.box(mv, A2sBoolean)
    }

    private fun compileLogical(ctx: A2sCompileContext, expr: A2sBinary, and: Boolean) {
        compile(ctx, expr.left)
        A2sTypeCodegen.unbox(ctx.mv, A2sBoolean)
        val skipLabel = Label()
        val endLabel = Label()
        if (and) ctx.mv.visitJumpInsn(IFEQ, skipLabel) else ctx.mv.visitJumpInsn(IFNE, skipLabel)
        compile(ctx, expr.right)
        A2sTypeCodegen.unbox(ctx.mv, A2sBoolean)
        if (and) ctx.mv.visitJumpInsn(IFEQ, skipLabel) else ctx.mv.visitJumpInsn(IFNE, skipLabel)
        ctx.mv.visitInsn(ICONST_1)
        ctx.mv.visitJumpInsn(GOTO, endLabel)
        ctx.mv.visitLabel(skipLabel)
        ctx.mv.visitInsn(ICONST_0)
        ctx.mv.visitLabel(endLabel)
        A2sTypeCodegen.box(ctx.mv, A2sBoolean)
    }

    private fun compileStringConcat(ctx: A2sCompileContext, expr: A2sBinary) {
        ctx.mv.visitTypeInsn(NEW, "java/lang/StringBuilder")
        ctx.mv.visitInsn(DUP)
        ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
        compile(ctx, expr.left)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
        compile(ctx, expr.right)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
    }

    private fun compileUnary(ctx: A2sCompileContext, expr: A2sUnary) {
        compile(ctx, expr.operand)
        val operandType = symbols.inferType(expr.operand, ctx.localTypes())
        when (expr.op) {
            A2sUnaryOp.MINUS -> when (operandType) {
                A2sBigInt -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigInteger", "negate", "()Ljava/math/BigInteger;", false)
                A2sRational -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, TYPE_RATIONAL, "negate", "()L$TYPE_RATIONAL;", false)
                else -> {
                    A2sTypeCodegen.unbox(ctx.mv, operandType)
                    when (operandType) {
                        A2sI32, A2sU32 -> ctx.mv.visitInsn(INEG)
                        A2sI64, A2sU64 -> ctx.mv.visitInsn(LNEG)
                        A2sF32 -> ctx.mv.visitInsn(FNEG)
                        A2sF64 -> ctx.mv.visitInsn(DNEG)
            else -> {
                ctx.mv.visitTypeInsn(CHECKCAST, TYPE_RATIONAL)
            }
                    }
                    A2sTypeCodegen.box(ctx.mv, operandType)
                }
            }

            A2sUnaryOp.NOT -> {
                A2sTypeCodegen.unbox(ctx.mv, A2sBoolean)
                val trueLabel = Label()
                val endLabel = Label()
                ctx.mv.visitJumpInsn(IFEQ, trueLabel)
                ctx.mv.visitInsn(ICONST_0)
                ctx.mv.visitJumpInsn(GOTO, endLabel)
                ctx.mv.visitLabel(trueLabel)
                ctx.mv.visitInsn(ICONST_1)
                ctx.mv.visitLabel(endLabel)
                A2sTypeCodegen.box(ctx.mv, A2sBoolean)
            }
        }
    }

    private fun compileFieldAccess(ctx: A2sCompileContext, expr: A2sFieldAccess) {
        val receiverType = symbols.inferType(expr.receiver, ctx.localTypes())
        val fieldType = if (receiverType is A2sEventType) {
            symbols.eventFieldType(receiverType.eventName, expr.fieldName)
        } else {
            A2sAny
        }
        val needsCheckcast = fieldType != A2sAny && fieldType !is A2sUnknown

        if (receiverType is A2sEventType) {
            compile(ctx, expr.receiver)
            if (expr.safe) {
                val nullLabel = Label()
                val endLabel = Label()
                ctx.mv.visitInsn(DUP)
                ctx.mv.visitJumpInsn(IFNULL, nullLabel)
                ctx.mv.visitLdcInsn(expr.fieldName)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "getField", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false)
                if (needsCheckcast) ctx.mv.visitTypeInsn(CHECKCAST, A2sTypeCodegen.boxedInternalName(fieldType))
                ctx.mv.visitJumpInsn(GOTO, endLabel)
                ctx.mv.visitLabel(nullLabel)
                ctx.mv.visitInsn(POP)
                ctx.mv.visitInsn(ACONST_NULL)
                ctx.mv.visitLabel(endLabel)
            } else {
                ctx.mv.visitLdcInsn(expr.fieldName)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "getField", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false)
                if (needsCheckcast) ctx.mv.visitTypeInsn(CHECKCAST, A2sTypeCodegen.boxedInternalName(fieldType))
            }
        } else {
            compile(ctx, expr.receiver)
            if (expr.safe) {
                val nullLabel = Label()
                val endLabel = Label()
                ctx.mv.visitInsn(DUP)
                ctx.mv.visitJumpInsn(IFNULL, nullLabel)
                ctx.mv.visitLdcInsn(expr.fieldName)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "getField", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false)
                ctx.mv.visitJumpInsn(GOTO, endLabel)
                ctx.mv.visitLabel(nullLabel)
                ctx.mv.visitInsn(POP)
                ctx.mv.visitInsn(ACONST_NULL)
                ctx.mv.visitLabel(endLabel)
            } else {
                ctx.mv.visitLdcInsn(expr.fieldName)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "getField", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false)
            }
        }
    }

    private fun compileCall(ctx: A2sCompileContext, expr: A2sCall) {
        when (expr.name) {
            "println" -> {
                ctx.mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                if (expr.arguments.isNotEmpty()) compile(ctx, expr.arguments[0]) else ctx.mv.visitLdcInsn("")
                ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false)
                ctx.mv.visitInsn(ACONST_NULL)
            }

            "len" -> {
                if (expr.arguments.isNotEmpty()) compile(ctx, expr.arguments[0]) else ctx.mv.visitInsn(ACONST_NULL)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "len", "(Ljava/lang/Object;)I", false)
                A2sTypeCodegen.box(ctx.mv, A2sI32)
            }

            "toInt", "toI64" -> {
                if (expr.arguments.isNotEmpty()) compile(ctx, expr.arguments[0]) else ctx.mv.visitInsn(ACONST_NULL)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, expr.name, "(Ljava/lang/Object;)Ljava/lang/Object;", false)
            }

            "listOf" -> compileListOf(ctx, expr)

            else -> {
                // 检查是否是 lambda 变量
                val localType = ctx.localType(expr.name)
                if (localType is A2sLambdaType) {
                    // lambda 调用：通过 invokeMethod 反射
                    // 加载 lambda 实例
                    ctx.loadVariable(expr.name)
                    val lambdaTmp = ctx.declareLocal("__lambda_ref", A2sLambdaType)
                    ctx.mv.visitVarInsn(ASTORE, lambdaTmp)
                    // 打包参数为 Object[]
                    ctx.mv.visitLdcInsn(expr.arguments.size)
                    ctx.mv.visitTypeInsn(ANEWARRAY, "java/lang/Object")
                    for ((i, arg) in expr.arguments.withIndex()) {
                        ctx.mv.visitInsn(DUP)
                        ctx.mv.visitLdcInsn(i)
                        compile(ctx, arg)
                        ctx.mv.visitInsn(AASTORE)
                    }
                    val argsArraySlot = ctx.declareLocal("__lambda_args", A2sAny)
                    ctx.mv.visitVarInsn(ASTORE, argsArraySlot)
                    // 调用 invokeMethod(lambda, "invoke", args)
                    // 栈序：receiver, methodName, args
                    ctx.mv.visitVarInsn(ALOAD, lambdaTmp)
                    ctx.mv.visitLdcInsn("invoke")
                    ctx.mv.visitVarInsn(ALOAD, argsArraySlot)
                    ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "invokeMethod",
                        "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false)
                } else {
                    // 自定义函数：this 调用脚本类自身方法
                    ctx.mv.visitVarInsn(ALOAD, 0)
                    for (arg in expr.arguments) compile(ctx, arg)
                    val desc = "(" + "Ljava/lang/Object;".repeat(expr.arguments.size) + ")Ljava/lang/Object;"
                    ctx.mv.visitMethodInsn(INVOKEVIRTUAL, ctx.className, expr.name, desc, false)
                }
            }
        }
    }

    private fun compileListOf(ctx: A2sCompileContext, expr: A2sCall) {
        val count = expr.arguments.size
        ctx.mv.visitLdcInsn(count)
        ctx.mv.visitTypeInsn(ANEWARRAY, "java/lang/Object")
        for ((i, arg) in expr.arguments.withIndex()) {
            ctx.mv.visitInsn(DUP)
            ctx.mv.visitLdcInsn(i)
            compile(ctx, arg)
            ctx.mv.visitInsn(AASTORE)
        }
        ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "listOf", "([Ljava/lang/Object;)Ljava/util/List;", false)
    }

    /** 编译 elvis `a ?: b`：a 只求值一次，非空返回 a，否则返回 b。 */
    private fun compileElvis(ctx: A2sCompileContext, expr: A2sElvis) {
        val endLabel = Label()
        val elseLabel = Label()
        compile(ctx, expr.left)
        val tmp = ctx.allocateTemp()
        ctx.mv.visitVarInsn(ASTORE, tmp)
        ctx.mv.visitVarInsn(ALOAD, tmp)
        ctx.mv.visitJumpInsn(IFNULL, elseLabel)
        // left 非空
        ctx.mv.visitVarInsn(ALOAD, tmp)
        ctx.mv.visitJumpInsn(GOTO, endLabel)
        // left 为空：求 right
        ctx.mv.visitLabel(elseLabel)
        compile(ctx, expr.right)
        ctx.mv.visitLabel(endLabel)
    }

    /** 编译 `a!!`：若 null 则抛 NPE，否则返回原值。 */
    private fun compileNotNull(ctx: A2sCompileContext, expr: A2sNotNull) {
        val endLabel = Label()
        val throwLabel = Label()
        compile(ctx, expr.expr)
        ctx.mv.visitInsn(DUP)
        ctx.mv.visitJumpInsn(IFNULL, throwLabel)
        ctx.mv.visitJumpInsn(GOTO, endLabel)
        ctx.mv.visitLabel(throwLabel)
        ctx.mv.visitInsn(POP)
        ctx.mv.visitTypeInsn(NEW, "java/lang/NullPointerException")
        ctx.mv.visitInsn(DUP)
        ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "()V", false)
        ctx.mv.visitInsn(ATHROW)
        ctx.mv.visitLabel(endLabel)
    }

    /** 编译 lambda 字面量：分析捕获变量，生成隐藏类（含捕获字段），通过 A2sRuntime.newLambda 创建实例。 */
    private fun compileLambda(ctx: A2sCompileContext, expr: A2sLambda) {
        val capturedVars = analyzeCapturedVars(expr, ctx)

        val lambdaIndex = classCompiler.nextLambdaIndex()
        val lambdaClassName = "${ctx.className}_lambda_$lambdaIndex"
        val lambdaClass = classCompiler.generateLambdaClass(lambdaClassName, expr, ctx.className, capturedVars)
        classCompiler.collectedLambdas[lambdaClassName] = lambdaClass

        // emit: A2sRuntime.newLambda(className, scriptObj, Object[] captures)
        // 压入 className (String)
        ctx.mv.visitLdcInsn(lambdaClassName)
        // 压入 scriptObj
        ctx.mv.visitVarInsn(ALOAD, ctx.scriptObjSlot)
        // 创建 captures Object[] 数组
        ctx.mv.visitLdcInsn(capturedVars.size)
        ctx.mv.visitTypeInsn(ANEWARRAY, "java/lang/Object")
        for ((i, cap) in capturedVars.withIndex()) {
            ctx.mv.visitInsn(DUP)
            ctx.mv.visitLdcInsn(i)
            ctx.loadVariable(cap.first)
            ctx.mv.visitInsn(AASTORE)
        }
        // INVOKESTATIC A2sRuntime.newLambda(String, Object, Object[])Object
        ctx.mv.visitMethodInsn(
            INVOKESTATIC, TYPE_RUNTIME, "newLambda",
            "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
            false
        )
    }

    /**
     * 自由变量分析：扫描 lambda body，找出引用的外层变量（排除自身参数和 body 内声明的局部变量）。
     * 顶层 var 通过 scriptObj 访问，不需要捕获。
     */
    private fun analyzeCapturedVars(
        lambda: A2sLambda,
        ctx: A2sCompileContext,
    ): List<Pair<String, A2sType>> {
        val ownParams = lambda.params.map { it.name }.toSet()
        val declaredInBody = mutableSetOf<String>()
        val referenced = mutableSetOf<String>()

        lateinit var scanExpr: (A2sExpr) -> Unit
        lateinit var scanStmt: (A2sStmt) -> Unit

        scanStmt = { stmt ->
            when (stmt) {
                is A2sVarDecl -> { declaredInBody.add(stmt.name); stmt.initializer?.let(scanExpr) }
                is A2sAssign -> { scanExpr(stmt.target); scanExpr(stmt.value) }
                is A2sExprStmt -> scanExpr(stmt.expr)
                is A2sIf -> { scanExpr(stmt.condition); stmt.thenBody.forEach(scanStmt); stmt.elseBody?.forEach(scanStmt) }
                is A2sFor -> { declaredInBody.add(stmt.variable); scanExpr(stmt.iterable); stmt.body.forEach(scanStmt) }
                is A2sWhile -> { scanExpr(stmt.condition); stmt.body.forEach(scanStmt) }
                is A2sReturn -> stmt.value?.let(scanExpr)
                is A2sThrow -> scanExpr(stmt.expr)
                is A2sTry -> {
                    stmt.body.forEach(scanStmt)
                    stmt.catches.forEach { c -> declaredInBody.add(c.paramName); c.body.forEach(scanStmt) }
                    stmt.finallyBody?.forEach(scanStmt)
                }
                is A2sPost -> stmt.arguments.forEach(scanExpr)
                else -> {}
            }
        }

        scanExpr = { expr ->
            when (expr) {
                is A2sIdentifier -> referenced.add(expr.name)
                is A2sBinary -> { scanExpr(expr.left); scanExpr(expr.right) }
                is A2sUnary -> scanExpr(expr.operand)
                is A2sCall -> {
                    if (ctx.hasLocal(expr.name) || ctx.isEventField(expr.name)) referenced.add(expr.name)
                    expr.arguments.forEach(scanExpr)
                }
                is A2sFieldAccess -> scanExpr(expr.receiver)
                is A2sMethodCall -> { scanExpr(expr.receiver); expr.arguments.forEach(scanExpr) }
                is A2sIndexAccess -> { scanExpr(expr.receiver); scanExpr(expr.index) }
                is A2sElvis -> { scanExpr(expr.left); scanExpr(expr.right) }
                is A2sNotNull -> scanExpr(expr.expr)
                is A2sLambda -> {
                    // 嵌套 lambda：参数视为 "在 body 内声明"，递归扫描 body 以发现外层需要捕获的变量
                    expr.params.forEach { declaredInBody.add(it.name) }
                    expr.body.forEach(scanStmt)
                }
                is A2sStringInterpolation -> expr.parts.filterIsInstance<A2sStrExpr>().forEach { scanExpr(it.expr) }
                is A2sIfExpr -> { scanExpr(expr.condition); expr.thenBody.forEach(scanStmt); expr.elseBody?.forEach(scanStmt) }
                is A2sWhenExpr -> { scanExpr(expr.subject); expr.entries.forEach { e -> e.conditions.forEach(scanExpr); e.body.forEach(scanStmt) } }
                else -> {}
            }
        }

        lambda.body.forEach(scanStmt)

        // captured = referenced - declaredInBody - ownParams
        val capturedNames = referenced - declaredInBody - ownParams
        return capturedNames
            .filter { ctx.hasLocal(it) }
            .map { it to ctx.localType(it) }
    }

    private fun compileMethodCall(ctx: A2sCompileContext, expr: A2sMethodCall) {
        compile(ctx, expr.receiver)
        ctx.mv.visitLdcInsn(expr.methodName)
        ctx.mv.visitLdcInsn(expr.arguments.size)
        ctx.mv.visitTypeInsn(ANEWARRAY, "java/lang/Object")
        for ((i, arg) in expr.arguments.withIndex()) {
            ctx.mv.visitInsn(DUP)
            ctx.mv.visitLdcInsn(i)
            compile(ctx, arg)
            ctx.mv.visitInsn(AASTORE)
        }
        ctx.mv.visitMethodInsn(
            INVOKESTATIC, TYPE_RUNTIME, "invokeMethod",
            "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false
        )
    }

    private fun compileIndexAccess(ctx: A2sCompileContext, expr: A2sIndexAccess) {
        compile(ctx, expr.receiver)
        compile(ctx, expr.index)
        ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "getAt", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
    }
}
