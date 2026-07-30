package kaptor.compiler

import kaptor.ir.*
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*

class ScriptCompiler {
    private var classCounter = 0

    fun resetCounter() {
        classCounter = 0
    }

    fun compile(ir: IrScriptFile, scriptName: String, eventClassMap: Map<String, String> = emptyMap(), isKt: Boolean = false): CompiledScript {
        val className = "script.${scriptName.replace('.', '_')}_${classCounter++}"
        val internalName = className.replace('.', '/')

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

        cw.visit(
            V17,
            ACC_PUBLIC or ACC_SUPER,
            internalName,
            null,
            TYPE_HANDLER_BASE,
            null
        )

        val sourceExtension = if (isKt) "kt" else "kts"
        cw.visitSource("$scriptName.$sourceExtension", null)

        generateInit(cw, internalName)

        for (handler in ir.handlers) {
            val eventClass = eventClassMap[handler.eventType]
            generateHandler(cw, internalName, handler, eventClass)
        }

        generateGetEventTypes(cw, internalName, ir.handlers)
        generateGetCostLimits(cw, internalName, ir.handlers)

        cw.visitEnd()

        return CompiledScript(
            className = className,
            bytecode = cw.toByteArray(),
            eventTypes = ir.handlers.map { it.eventType }.distinct(),
            handlers = ir.handlers.map { CompiledHandler(it.eventType, it.hookType, it.costLimit) }
        )
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

    private fun generateHandler(cw: ClassVisitor, internalName: String, handler: IrHandler, eventClassName: String? = null) {
        val prefix = when (handler.hookType) {
            HookType.ON -> "handle"
            HookType.BEFORE -> "before"
            HookType.AFTER -> "after"
        }
        val methodName = "${prefix}_${sanitizeName(handler.eventType)}"
        val methodDesc = "(Ljava/lang/Object;)V"

        val mv = cw.visitMethod(ACC_PUBLIC, methodName, methodDesc, null, null)
        mv.visitCode()

        val ctx = MethodContext(mv, handler.costLimit, eventClassName, handler.paramName)
        ctx.declareLocal("event", "Ljava/lang/Object;")
        mv.visitVarInsn(ALOAD, 1)
        mv.visitVarInsn(ASTORE, ctx.getLocal("event"))

        ctx.consumeCost(1, "handler_entry")
        checkCostLimit(ctx, "Handler entry cost exceeded for ${handler.eventType}")

        if (handler.paramName != null) {
            ctx.declareLocal(handler.paramName, "Ljava/lang/Object;")
            mv.visitVarInsn(ALOAD, ctx.getLocal("event"))
            mv.visitVarInsn(ASTORE, ctx.getLocal(handler.paramName))
        }

        for (instr in handler.body) {
            compileInstruction(ctx, instr)
        }

        ctx.consumeCost(1, "handler_exit")
        checkCostLimit(ctx, "Handler exit cost exceeded for ${handler.eventType}")

        mv.visitInsn(RETURN)
        mv.visitMaxs(ctx.maxStack, ctx.maxLocals)
        mv.visitEnd()
    }

    private fun compileInstruction(ctx: MethodContext, instr: IrInstruction) {
        when (instr) {
            is IrValDecl -> compileValDecl(ctx, instr)
            is IrVarDecl -> compileVarDecl(ctx, instr)
            is IrAssignment -> compileAssignment(ctx, instr)
            is IrExpressionStatement -> compileExprStmt(ctx, instr)
            is IrIfStatement -> compileIf(ctx, instr)
            is IrWhileStatement -> compileWhile(ctx, instr)
            is IrForStatement -> compileFor(ctx, instr)
            is IrReturnStatement -> compileReturn(ctx, instr)
            is IrBreakStatement -> compileBreak(ctx)
            is IrContinueStatement -> compileContinue(ctx)
        }
    }

    private fun compileValDecl(ctx: MethodContext, instr: IrValDecl) {
        ctx.consumeCost(instr.cost, "val_decl:${instr.name}")
        checkCostLimit(ctx, "Cost exceeded in val declaration: ${instr.name}")
        ctx.declareLocal(instr.name, "Ljava/lang/Object;")
        compileExpression(ctx, instr.initializer)
        ctx.mv.visitVarInsn(ASTORE, ctx.getLocal(instr.name))
    }

    private fun compileVarDecl(ctx: MethodContext, instr: IrVarDecl) {
        ctx.consumeCost(instr.cost, "var_decl:${instr.name}")
        checkCostLimit(ctx, "Cost exceeded in var declaration: ${instr.name}")
        ctx.declareLocal(instr.name, "Ljava/lang/Object;")
        if (instr.initializer != null) {
            compileExpression(ctx, instr.initializer)
        } else {
            ctx.mv.visitInsn(ACONST_NULL)
        }
        ctx.mv.visitVarInsn(ASTORE, ctx.getLocal(instr.name))
    }

    private fun compileAssignment(ctx: MethodContext, instr: IrAssignment) {
        ctx.consumeCost(instr.cost, "assignment")
        checkCostLimit(ctx, "Cost exceeded in assignment")
        compileExpression(ctx, instr.value)
        compileStoreTarget(ctx, instr.target)
    }

    private fun compileExprStmt(ctx: MethodContext, instr: IrExpressionStatement) {
        ctx.consumeCost(instr.cost, "expr_stmt")
        checkCostLimit(ctx, "Cost exceeded in expression statement")
        compileExpression(ctx, instr.expr)
        ctx.mv.visitInsn(POP)
    }

    private fun compileIf(ctx: MethodContext, instr: IrIfStatement) {
        ctx.consumeCost(3, "if_entry")
        checkCostLimit(ctx, "Cost exceeded in if statement")

        val elseLabel = Label()
        val endLabel = Label()

        compileExpression(ctx, instr.condition)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
        ctx.mv.visitJumpInsn(IFEQ, elseLabel)

        for (stmt in instr.thenBranch) {
            compileInstruction(ctx, stmt)
        }

        if (instr.elseBranch != null) {
            ctx.mv.visitJumpInsn(GOTO, endLabel)
            ctx.mv.visitLabel(elseLabel)
            for (stmt in instr.elseBranch) {
                compileInstruction(ctx, stmt)
            }
            ctx.mv.visitLabel(endLabel)
        } else {
            ctx.mv.visitLabel(elseLabel)
        }
    }

    private fun compileWhile(ctx: MethodContext, instr: IrWhileStatement) {
        val startLabel = Label()
        val endLabel = Label()

        ctx.loopEndLabels.add(endLabel)
        ctx.loopStartLabels.add(startLabel)

        ctx.mv.visitLabel(startLabel)
        ctx.consumeCost(5, "while_iteration")
        checkCostLimit(ctx, "Cost exceeded in while loop")

        compileExpression(ctx, instr.condition)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
        ctx.mv.visitJumpInsn(IFEQ, endLabel)

        for (stmt in instr.body) {
            compileInstruction(ctx, stmt)
        }

        ctx.mv.visitJumpInsn(GOTO, startLabel)
        ctx.mv.visitLabel(endLabel)

        ctx.loopEndLabels.removeLast()
        ctx.loopStartLabels.removeLast()
    }

    private fun compileFor(ctx: MethodContext, instr: IrForStatement) {
        val startLabel = Label()
        val endLabel = Label()
        val bodyLabel = Label()

        ctx.loopEndLabels.add(endLabel)
        ctx.loopStartLabels.add(startLabel)

        ctx.consumeCost(5, "for_entry")
        checkCostLimit(ctx, "Cost exceeded in for loop")

        compileExpression(ctx, instr.iterable)

        ctx.declareLocal("__iter_${instr.variable}", "Ljava/util/Iterator;")
        ctx.mv.visitTypeInsn(CHECKCAST, "java/lang/Iterable")
        ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true)
        ctx.mv.visitVarInsn(ASTORE, ctx.getLocal("__iter_${instr.variable}"))

        ctx.mv.visitLabel(startLabel)
        ctx.mv.visitVarInsn(ALOAD, ctx.getLocal("__iter_${instr.variable}"))
        ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true)
        ctx.mv.visitJumpInsn(IFEQ, endLabel)

        ctx.mv.visitVarInsn(ALOAD, ctx.getLocal("__iter_${instr.variable}"))
        ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true)

        ctx.declareLocal(instr.variable, "Ljava/lang/Object;")
        ctx.mv.visitVarInsn(ASTORE, ctx.getLocal(instr.variable))

        for (stmt in instr.body) {
            compileInstruction(ctx, stmt)
        }

        ctx.mv.visitJumpInsn(GOTO, startLabel)
        ctx.mv.visitLabel(endLabel)

        ctx.loopEndLabels.removeLast()
        ctx.loopStartLabels.removeLast()
    }

    private fun compileReturn(ctx: MethodContext, instr: IrReturnStatement) {
        ctx.consumeCost(instr.cost, "return")
        if (instr.value != null) {
            compileExpression(ctx, instr.value)
            ctx.mv.visitInsn(ARETURN)
        } else {
            ctx.mv.visitInsn(RETURN)
        }
    }

    private fun compileBreak(ctx: MethodContext) {
        ctx.consumeCost(1, "break")
        if (ctx.loopEndLabels.isEmpty()) {
            throw ScriptCompileError("break outside of loop")
        }
        ctx.mv.visitJumpInsn(GOTO, ctx.loopEndLabels.last())
    }

    private fun compileContinue(ctx: MethodContext) {
        ctx.consumeCost(1, "continue")
        if (ctx.loopStartLabels.isEmpty()) {
            throw ScriptCompileError("continue outside of loop")
        }
        ctx.mv.visitJumpInsn(GOTO, ctx.loopStartLabels.last())
    }

    private fun compileExpression(ctx: MethodContext, expr: IrExpression) {
        when (expr) {
            is IrStringLiteral -> {
                ctx.mv.visitLdcInsn(expr.value)
            }
            is IrIntLiteral -> {
                when (expr.value) {
                    0 -> ctx.mv.visitInsn(ICONST_0)
                    1 -> ctx.mv.visitInsn(ICONST_1)
                    2 -> ctx.mv.visitInsn(ICONST_2)
                    3 -> ctx.mv.visitInsn(ICONST_3)
                    4 -> ctx.mv.visitInsn(ICONST_4)
                    5 -> ctx.mv.visitInsn(ICONST_5)
                    in Byte.MIN_VALUE..Byte.MAX_VALUE -> ctx.mv.visitIntInsn(BIPUSH, expr.value)
                    in Short.MIN_VALUE..Short.MAX_VALUE -> ctx.mv.visitIntInsn(SIPUSH, expr.value)
                    else -> ctx.mv.visitLdcInsn(expr.value)
                }
                ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
            }
            is IrLongLiteral -> {
                when (expr.value) {
                    0L -> ctx.mv.visitInsn(LCONST_0)
                    1L -> ctx.mv.visitInsn(LCONST_1)
                    else -> ctx.mv.visitLdcInsn(expr.value)
                }
                ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
            }
            is IrFloatLiteral -> {
                if (expr.numericType == IrFloatType) {
                    ctx.mv.visitLdcInsn(expr.value.toFloat())
                    ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false)
                } else {
                    ctx.mv.visitLdcInsn(expr.value)
                    ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false)
                }
            }
            is IrBoolLiteral -> {
                ctx.mv.visitInsn(if (expr.value) ICONST_1 else ICONST_0)
                ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false)
            }
            is IrNullLiteral -> {
                ctx.mv.visitInsn(ACONST_NULL)
            }
            is IrIdentifier -> {
                ctx.mv.visitVarInsn(ALOAD, ctx.getLocal(expr.name))
            }
            is IrFieldAccess -> {
                compileExpression(ctx, expr.receiver)
                if (ctx.eventClassName != null && isEventVariable(ctx, expr.receiver)) {
                    ctx.mv.visitTypeInsn(CHECKCAST, ctx.eventClassName)
                    val getterName = "get${expr.fieldName.replaceFirstChar { it.uppercase() }}"
                    ctx.mv.visitMethodInsn(INVOKEVIRTUAL, ctx.eventClassName, getterName, "()Ljava/lang/Object;", false)
                } else {
                    ctx.mv.visitTypeInsn(CHECKCAST, "java/util/Map")
                    ctx.mv.visitLdcInsn(expr.fieldName)
                    ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true)
                }
            }
            is IrMethodCall -> {
                compileExpression(ctx, expr.receiver)
                for (arg in expr.arguments) {
                    compileExpression(ctx, arg)
                }
                val argTypes = expr.arguments.map { "Ljava/lang/Object;" }.joinToString("")
                ctx.mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/Object",
                    expr.methodName,
                    "(${argTypes})Ljava/lang/Object;",
                    false
                )
            }
            is IrFunctionCall -> {
                when (expr.name) {
                    "println" -> {
                        ctx.mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                        if (expr.arguments.isNotEmpty()) {
                            compileExpression(ctx, expr.arguments[0])
                        } else {
                            ctx.mv.visitLdcInsn("")
                        }
                        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
                        ctx.mv.visitInsn(ACONST_NULL)
                    }
                    "toString" -> {
                        if (expr.arguments.isNotEmpty()) {
                            compileExpression(ctx, expr.arguments[0])
                        } else {
                            ctx.mv.visitInsn(ACONST_NULL)
                        }
                        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false)
                    }
                    "toInt" -> {
                        if (expr.arguments.isNotEmpty()) {
                            compileExpression(ctx, expr.arguments[0])
                        }
                        ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false)
                        ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
                    }
                    "len" -> {
                        if (expr.arguments.isNotEmpty()) {
                            compileExpression(ctx, expr.arguments[0])
                        }
                        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false)
                        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                        ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
                    }
                    "listOf" -> {
                        ctx.mv.visitTypeInsn(NEW, "java/util/ArrayList")
                        ctx.mv.visitInsn(DUP)
                        ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
                        for (arg in expr.arguments) {
                            ctx.mv.visitInsn(DUP)
                            compileExpression(ctx, arg)
                            ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true)
                            ctx.mv.visitInsn(POP)
                        }
                    }
                    else -> {
                        ctx.mv.visitVarInsn(ALOAD, 0)
                        for (arg in expr.arguments) {
                            compileExpression(ctx, arg)
                        }
                        val argTypes = expr.arguments.map { "Ljava/lang/Object;" }.joinToString("")
                        ctx.mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            TYPE_HANDLER_BASE,
                            expr.name,
                            "(${argTypes})Ljava/lang/Object;",
                            false
                        )
                    }
                }
            }
            is IrBinaryExpression -> {
                compileExpression(ctx, expr.left)
                compileExpression(ctx, expr.right)
                compileTypedBinaryOp(ctx, expr)
            }
            is IrUnaryExpression -> {
                compileExpression(ctx, expr.operand)
                compileTypedUnaryOp(ctx, expr)
            }
            is IrStringInterpolation -> {
                compileStringInterpolation(ctx, expr)
            }
            is IrIndexAccess -> {
                compileExpression(ctx, expr.receiver)
                compileExpression(ctx, expr.index)
                ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true)
            }
            is IrMergedExpression -> {
                for (e in expr.expressions) {
                    compileExpression(ctx, e)
                }
            }
        }
    }

    private fun compileTypedBinaryOp(ctx: MethodContext, expr: IrBinaryExpression) {
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
            BinaryOperator.PLUS, BinaryOperator.MINUS, BinaryOperator.MULTIPLY,
            BinaryOperator.DIVIDE, BinaryOperator.MODULO -> {
                compilePrimitiveArithmetic(ctx, op, leftType, rightType)
            }
            BinaryOperator.EQUALS, BinaryOperator.NOT_EQUALS,
            BinaryOperator.LESS, BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER, BinaryOperator.GREATER_EQUAL -> {
                if (leftType != IrObjectType && rightType != IrObjectType) {
                    compilePrimitiveComparison(ctx, op, leftType, rightType)
                } else if (op == BinaryOperator.EQUALS || op == BinaryOperator.NOT_EQUALS) {
                    compileObjectEquals(ctx, op)
                } else {
                    compileBinaryOpFallback(ctx, op)
                }
            }
            BinaryOperator.BIT_AND, BinaryOperator.BIT_OR, BinaryOperator.BIT_XOR,
            BinaryOperator.SHL, BinaryOperator.SHR -> {
                compilePrimitiveBitwise(ctx, op, leftType, rightType)
            }
            BinaryOperator.AND, BinaryOperator.OR -> {
                compileLogicalOp(ctx, op)
            }
        }
    }

    private fun compileTypedUnaryOp(ctx: MethodContext, expr: IrUnaryExpression) {
        val op = expr.operator
        val operandType = inferType(expr.operand)

        when (op) {
            UnaryOperator.MINUS -> {
                if (operandType != IrObjectType) {
                    unboxTop(ctx, operandType)
                    when (operandType) {
                        IrIntType -> ctx.mv.visitInsn(INEG)
                        IrLongType -> ctx.mv.visitInsn(LNEG)
                        IrFloatType -> ctx.mv.visitInsn(FNEG)
                        IrDoubleType -> ctx.mv.visitInsn(DNEG)
                        else -> {}
                    }
                    boxTop(ctx, operandType)
                } else {
                    ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "neg", "(Ljava/lang/Object;)Ljava/lang/Object;", false)
                }
            }
            UnaryOperator.NOT -> {
                if (operandType != IrObjectType) {
                    unboxTop(ctx, operandType)
                    if (operandType == IrBoolType) {
                        val trueLabel = Label()
                        val endLabel = Label()
                        ctx.mv.visitJumpInsn(IFNE, trueLabel)
                        ctx.mv.visitInsn(ICONST_1)
                        ctx.mv.visitJumpInsn(GOTO, endLabel)
                        ctx.mv.visitLabel(trueLabel)
                        ctx.mv.visitInsn(ICONST_0)
                        ctx.mv.visitLabel(endLabel)
                    } else {
                        ctx.mv.visitInsn(ICONST_0)
                        ctx.mv.visitInsn(ICONST_1)
                        ctx.mv.visitInsn(ISUB)
                    }
                    boxTop(ctx, IrBoolType)
                } else {
                    ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "not", "(Ljava/lang/Object;)Ljava/lang/Boolean;", false)
                }
            }
            UnaryOperator.BIT_NOT -> {
                if (operandType != IrObjectType) {
                    unboxTop(ctx, operandType)
                    when (operandType) {
                        IrIntType -> { ctx.mv.visitInsn(ICONST_M1); ctx.mv.visitInsn(IXOR) }
                        IrLongType -> { ctx.mv.visitLdcInsn(-1L); ctx.mv.visitInsn(LXOR) }
                        else -> { ctx.mv.visitInsn(ICONST_M1); ctx.mv.visitInsn(IXOR) }
                    }
                    boxTop(ctx, operandType)
                } else {
                    ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitNot", "(Ljava/lang/Object;)Ljava/lang/Object;", false)
                }
            }
        }
    }

    private fun compilePrimitiveArithmetic(ctx: MethodContext, op: BinaryOperator, leftType: IrType, rightType: IrType) {
        val promotedType = promoteType(leftType, rightType)
        val rightLocal = ctx.declareLocal("__arith_right", "Ljava/lang/Object;")
        ctx.mv.visitVarInsn(ASTORE, rightLocal)
        unboxTop(ctx, leftType)
        convertTo(ctx, leftType, promotedType)
        ctx.mv.visitVarInsn(ALOAD, rightLocal)
        unboxTop(ctx, rightType)
        convertTo(ctx, rightType, promotedType)
        when (promotedType) {
            IrIntType -> when (op) {
                BinaryOperator.PLUS -> ctx.mv.visitInsn(IADD)
                BinaryOperator.MINUS -> ctx.mv.visitInsn(ISUB)
                BinaryOperator.MULTIPLY -> ctx.mv.visitInsn(IMUL)
                BinaryOperator.DIVIDE -> ctx.mv.visitInsn(IDIV)
                BinaryOperator.MODULO -> ctx.mv.visitInsn(IREM)
                else -> {}
            }
            IrLongType -> when (op) {
                BinaryOperator.PLUS -> ctx.mv.visitInsn(LADD)
                BinaryOperator.MINUS -> ctx.mv.visitInsn(LSUB)
                BinaryOperator.MULTIPLY -> ctx.mv.visitInsn(LMUL)
                BinaryOperator.DIVIDE -> ctx.mv.visitInsn(LDIV)
                BinaryOperator.MODULO -> ctx.mv.visitInsn(LREM)
                else -> {}
            }
            IrFloatType -> when (op) {
                BinaryOperator.PLUS -> ctx.mv.visitInsn(FADD)
                BinaryOperator.MINUS -> ctx.mv.visitInsn(FSUB)
                BinaryOperator.MULTIPLY -> ctx.mv.visitInsn(FMUL)
                BinaryOperator.DIVIDE -> ctx.mv.visitInsn(FDIV)
                BinaryOperator.MODULO -> ctx.mv.visitInsn(FREM)
                else -> {}
            }
            IrDoubleType -> when (op) {
                BinaryOperator.PLUS -> ctx.mv.visitInsn(DADD)
                BinaryOperator.MINUS -> ctx.mv.visitInsn(DSUB)
                BinaryOperator.MULTIPLY -> ctx.mv.visitInsn(DMUL)
                BinaryOperator.DIVIDE -> ctx.mv.visitInsn(DDIV)
                BinaryOperator.MODULO -> ctx.mv.visitInsn(DREM)
                else -> {}
            }
            else -> {}
        }
        boxTop(ctx, promotedType)
    }

    private fun compilePrimitiveComparison(ctx: MethodContext, op: BinaryOperator, leftType: IrType, rightType: IrType) {
        val promotedType = promoteType(leftType, rightType)
        val rightLocal = ctx.declareLocal("__cmp_right", "Ljava/lang/Object;")
        ctx.mv.visitVarInsn(ASTORE, rightLocal)
        unboxTop(ctx, leftType)
        convertTo(ctx, leftType, promotedType)
        ctx.mv.visitVarInsn(ALOAD, rightLocal)
        unboxTop(ctx, rightType)
        convertTo(ctx, rightType, promotedType)

        val trueLabel = Label()
        val endLabel = Label()

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
                ctx.mv.visitJumpInsn(jumpOp, trueLabel)
                ctx.mv.visitInsn(ICONST_0)
                ctx.mv.visitJumpInsn(GOTO, endLabel)
                ctx.mv.visitLabel(trueLabel)
                ctx.mv.visitInsn(ICONST_1)
            }
            IrLongType, IrFloatType, IrDoubleType -> {
                val cmpInsn = when (promotedType) {
                    IrLongType -> LCMP
                    IrFloatType -> FCMPG
                    else -> DCMPG
                }
                ctx.mv.visitInsn(cmpInsn)
                val jumpOp = when (op) {
                    BinaryOperator.EQUALS -> IFEQ
                    BinaryOperator.NOT_EQUALS -> IFNE
                    BinaryOperator.LESS -> IFLT
                    BinaryOperator.LESS_EQUAL -> IFLE
                    BinaryOperator.GREATER -> IFGT
                    BinaryOperator.GREATER_EQUAL -> IFGE
                    else -> IFEQ
                }
                ctx.mv.visitJumpInsn(jumpOp, trueLabel)
                ctx.mv.visitInsn(ICONST_0)
                ctx.mv.visitJumpInsn(GOTO, endLabel)
                ctx.mv.visitLabel(trueLabel)
                ctx.mv.visitInsn(ICONST_1)
            }
            else -> {}
        }

        ctx.mv.visitLabel(endLabel)
        boxTop(ctx, IrBoolType)
    }

    private fun compileObjectEquals(ctx: MethodContext, op: BinaryOperator) {
        ctx.mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
        if (op == BinaryOperator.NOT_EQUALS) {
            val trueLabel = Label()
            val endLabel = Label()
            ctx.mv.visitJumpInsn(IFNE, trueLabel)
            ctx.mv.visitInsn(ICONST_1)
            ctx.mv.visitJumpInsn(GOTO, endLabel)
            ctx.mv.visitLabel(trueLabel)
            ctx.mv.visitInsn(ICONST_0)
            ctx.mv.visitLabel(endLabel)
        }
        boxTop(ctx, IrBoolType)
    }

    private fun compilePrimitiveBitwise(ctx: MethodContext, op: BinaryOperator, leftType: IrType, rightType: IrType) {
        val promotedType = promoteType(leftType, rightType)
        val rightLocal = ctx.declareLocal("__bitwise_right", "Ljava/lang/Object;")
        ctx.mv.visitVarInsn(ASTORE, rightLocal)
        unboxTop(ctx, leftType)
        convertTo(ctx, leftType, promotedType)
        ctx.mv.visitVarInsn(ALOAD, rightLocal)
        unboxTop(ctx, rightType)
        convertTo(ctx, rightType, promotedType)
        when (promotedType) {
            IrIntType -> when (op) {
                BinaryOperator.BIT_AND -> ctx.mv.visitInsn(IAND)
                BinaryOperator.BIT_OR -> ctx.mv.visitInsn(IOR)
                BinaryOperator.BIT_XOR -> ctx.mv.visitInsn(IXOR)
                BinaryOperator.SHL -> ctx.mv.visitInsn(ISHL)
                BinaryOperator.SHR -> ctx.mv.visitInsn(ISHR)
                else -> {}
            }
            IrLongType -> when (op) {
                BinaryOperator.BIT_AND -> ctx.mv.visitInsn(LAND)
                BinaryOperator.BIT_OR -> ctx.mv.visitInsn(LOR)
                BinaryOperator.BIT_XOR -> ctx.mv.visitInsn(LXOR)
                BinaryOperator.SHL -> ctx.mv.visitInsn(LSHL)
                BinaryOperator.SHR -> ctx.mv.visitInsn(LSHR)
                else -> {}
            }
            else -> {}
        }
        boxTop(ctx, promotedType)
    }

    private fun compileStringConcat(ctx: MethodContext) {
        ctx.mv.visitTypeInsn(NEW, "java/lang/StringBuilder")
        ctx.mv.visitInsn(DUP)
        ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
        ctx.mv.visitInsn(SWAP)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
        ctx.mv.visitInsn(SWAP)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
    }

    private fun compileLogicalOp(ctx: MethodContext, op: BinaryOperator) {
        ctx.mv.visitInsn(SWAP)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
        ctx.mv.visitInsn(SWAP)
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)

        if (op == BinaryOperator.AND) {
            val falseLabel = Label()
            val endLabel = Label()
            ctx.mv.visitJumpInsn(IFEQ, falseLabel)
            ctx.mv.visitInsn(ICONST_1)
            ctx.mv.visitJumpInsn(GOTO, endLabel)
            ctx.mv.visitLabel(falseLabel)
            ctx.mv.visitInsn(ICONST_0)
            ctx.mv.visitLabel(endLabel)
        } else {
            val trueLabel = Label()
            val endLabel = Label()
            ctx.mv.visitJumpInsn(IFNE, trueLabel)
            ctx.mv.visitInsn(ICONST_0)
            ctx.mv.visitJumpInsn(GOTO, endLabel)
            ctx.mv.visitLabel(trueLabel)
            ctx.mv.visitInsn(ICONST_1)
            ctx.mv.visitLabel(endLabel)
        }
        boxTop(ctx, IrBoolType)
    }

    private fun compileBinaryOpFallback(ctx: MethodContext, op: BinaryOperator) {
        when (op) {
            BinaryOperator.PLUS -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "add", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.MINUS -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "sub", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.MULTIPLY -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "mul", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.DIVIDE -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "div", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.MODULO -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "mod", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.EQUALS -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            BinaryOperator.NOT_EQUALS -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "notEquals", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            BinaryOperator.LESS -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "lessThan", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            BinaryOperator.LESS_EQUAL -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "lessEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            BinaryOperator.GREATER -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "greaterThan", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            BinaryOperator.GREATER_EQUAL -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "greaterEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            BinaryOperator.BIT_AND -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitAnd", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.BIT_OR -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitOr", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.BIT_XOR -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitXor", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.SHL -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "shl", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.SHR -> ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "shr", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            BinaryOperator.AND, BinaryOperator.OR -> {
                compileLogicalOp(ctx, op)
            }
        }
    }

    private fun unboxTop(ctx: MethodContext, type: IrType) {
        when (type) {
            IrIntType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)
            IrLongType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false)
            IrFloatType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false)
            IrDoubleType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false)
            IrBoolType -> ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
            else -> {}
        }
    }

    private fun boxTop(ctx: MethodContext, type: IrType) {
        when (type) {
            IrIntType -> ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
            IrLongType -> ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
            IrFloatType -> ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false)
            IrDoubleType -> ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false)
            IrBoolType -> ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false)
            else -> {}
        }
    }

    private fun convertTo(ctx: MethodContext, from: IrType, to: IrType) {
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

    private fun promoteType(a: IrType, b: IrType): IrType {
        val order = listOf(IrIntType, IrLongType, IrFloatType, IrDoubleType)
        val ai = order.indexOf(a)
        val bi = order.indexOf(b)
        if (ai == -1 && bi == -1) return IrObjectType
        if (ai == -1) return b
        if (bi == -1) return a
        return if (ai >= bi) a else b
    }

    private fun inferType(expr: IrExpression): IrType = when (expr) {
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

    private fun compileStringInterpolation(ctx: MethodContext, expr: IrStringInterpolation) {
        ctx.mv.visitTypeInsn(NEW, "java/lang/StringBuilder")
        ctx.mv.visitInsn(DUP)
        ctx.mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)

        for (part in expr.parts) {
            when (part) {
                is IrLiteralPart -> {
                    ctx.mv.visitLdcInsn(part.text)
                    ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
                }
                is IrExpressionPart -> {
                    compileExpression(ctx, part.expr)
                    ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
                }
            }
        }

        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
    }

    private fun compileStoreTarget(ctx: MethodContext, target: IrExpression) {
        when (target) {
            is IrIdentifier -> {
                ctx.mv.visitVarInsn(ASTORE, ctx.getLocal(target.name))
            }
            is IrFieldAccess -> {
                compileExpression(ctx, target.receiver)
                if (ctx.eventClassName != null && isEventVariable(ctx, target.receiver)) {
                    val setterName = "set${target.fieldName.replaceFirstChar { it.uppercase() }}"
                    ctx.mv.visitInsn(SWAP)
                    ctx.mv.visitMethodInsn(INVOKEVIRTUAL, ctx.eventClassName, setterName, "(Ljava/lang/Object;)V", false)
                } else {
                    ctx.mv.visitTypeInsn(CHECKCAST, "java/util/Map")
                    ctx.mv.visitLdcInsn(target.fieldName)
                    ctx.mv.visitInsn(SWAP)
                    ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true)
                    ctx.mv.visitInsn(POP)
                }
            }
            is IrIndexAccess -> {
                compileExpression(ctx, target.receiver)
                compileExpression(ctx, target.index)
                ctx.mv.visitInsn(SWAP)
                ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true)
                ctx.mv.visitInsn(POP)
            }
            else -> throw ScriptCompileError("Invalid assignment target")
        }
    }

    private fun checkCostLimit(ctx: MethodContext, message: String) {
        if (ctx.currentCost > ctx.costLimit) {
            ctx.mv.visitLdcInsn(message)
            ctx.mv.visitMethodInsn(
                INVOKESTATIC,
                TYPE_SANDBOX,
                "throwLimitExceeded",
                "(Ljava/lang/String;)V",
                false
            )
        }
    }

    private fun generateGetEventTypes(cw: ClassVisitor, internalName: String, handlers: List<IrHandler>) {
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

    private fun generateGetCostLimits(cw: ClassVisitor, internalName: String, handlers: List<IrHandler>) {
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
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true)
            mv.visitInsn(POP)
        }
        mv.visitInsn(ARETURN)
        mv.visitMaxs(3, 1)
        mv.visitEnd()
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    private fun isEventVariable(ctx: MethodContext, expr: IrExpression): Boolean {
        if (expr !is IrIdentifier) return false
        return expr.name == "event" || expr.name == ctx.eventParamName
    }

    companion object {
        const val TYPE_HANDLER_BASE = "kaptor/runtime/ScriptHandlerBase"
        const val TYPE_SANDBOX = "kaptor/runtime/ScriptSandbox"
    }
}

class MethodContext(
    val mv: MethodVisitor,
    val costLimit: Int,
    val eventClassName: String? = null,
    val eventParamName: String? = null
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

    fun consumeCost(amount: Int, description: String) {
        currentCost += amount
        maxStack = maxOf(maxStack, 4)
    }
}

data class CompiledHandler(
    val eventType: String,
    val hookType: HookType,
    val costLimit: Int
)

data class CompiledScript(
    val className: String,
    val bytecode: ByteArray,
    val eventTypes: List<String>,
    val handlers: List<CompiledHandler>
)

class ScriptCompileError(message: String) : RuntimeException(message)
