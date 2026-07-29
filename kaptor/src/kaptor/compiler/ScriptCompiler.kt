package kaptor.compiler

import kaptor.ir.*
import kaptor.ast.BinaryOperator
import kaptor.ast.HookType
import kaptor.ast.UnaryOperator
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ScriptCompiler {
    private var classCounter = 0

    fun resetCounter() {
        classCounter = 0
    }

    fun compile(ir: IrScriptFile, scriptName: String, eventClassMap: Map<String, String> = emptyMap()): CompiledScript {
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

        cw.visitSource("$scriptName.script", null)

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
                    0L -> ctx.mv.visitInsn(LCONST_0)
                    1L -> ctx.mv.visitInsn(LCONST_1)
                    else -> ctx.mv.visitLdcInsn(expr.value)
                }
                ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
            }
            is IrFloatLiteral -> {
                ctx.mv.visitLdcInsn(expr.value)
                ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false)
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
                compileBinaryOp(ctx, expr.operator)
            }
            is IrUnaryExpression -> {
                compileExpression(ctx, expr.operand)
                compileUnaryOp(ctx, expr.operator)
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

    private fun compileBinaryOp(ctx: MethodContext, op: BinaryOperator) {
        when (op) {
            BinaryOperator.PLUS -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "add", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.MINUS -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "sub", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.MULTIPLY -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "mul", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.DIVIDE -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "div", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.MODULO -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "mod", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.EQUALS -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            }
            BinaryOperator.NOT_EQUALS -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "notEquals", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            }
            BinaryOperator.LESS -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "lessThan", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            }
            BinaryOperator.LESS_EQUAL -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "lessEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            }
            BinaryOperator.GREATER -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "greaterThan", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            }
            BinaryOperator.GREATER_EQUAL -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "greaterEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            }
            BinaryOperator.AND -> {
                val falseLabel = Label()
                val endLabel = Label()
                ctx.mv.visitJumpInsn(IFEQ, falseLabel)
                ctx.mv.visitInsn(POP)
                compileBooleanResult(ctx, true)
                ctx.mv.visitJumpInsn(GOTO, endLabel)
                ctx.mv.visitLabel(falseLabel)
                compileBooleanResult(ctx, false)
                ctx.mv.visitLabel(endLabel)
            }
            BinaryOperator.OR -> {
                val trueLabel = Label()
                val endLabel = Label()
                ctx.mv.visitJumpInsn(IFNE, trueLabel)
                ctx.mv.visitInsn(POP)
                compileBooleanResult(ctx, false)
                ctx.mv.visitJumpInsn(GOTO, endLabel)
                ctx.mv.visitLabel(trueLabel)
                compileBooleanResult(ctx, true)
                ctx.mv.visitLabel(endLabel)
            }
            BinaryOperator.BIT_AND -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitAnd", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.BIT_OR -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitOr", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.BIT_XOR -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitXor", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.SHL -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "shl", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            BinaryOperator.SHR -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "shr", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
        }
    }

    private fun compileUnaryOp(ctx: MethodContext, op: UnaryOperator) {
        when (op) {
            UnaryOperator.MINUS -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "neg", "(Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
            UnaryOperator.NOT -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "not", "(Ljava/lang/Object;)Ljava/lang/Boolean;", false)
            }
            UnaryOperator.BIT_NOT -> {
                ctx.mv.visitMethodInsn(INVOKESTATIC, "kaptor/runtime/ScriptRuntime", "bitNot", "(Ljava/lang/Object;)Ljava/lang/Object;", false)
            }
        }
    }

    private fun compileBooleanResult(ctx: MethodContext, value: Boolean) {
        ctx.mv.visitInsn(if (value) ICONST_1 else ICONST_0)
        ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false)
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
