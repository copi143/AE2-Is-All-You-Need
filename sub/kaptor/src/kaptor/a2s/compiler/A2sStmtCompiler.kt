package kaptor.a2s.compiler

import kaptor.a2s.ir.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*

/**
 * 语句编译器：把 A2sStmt 编译为字节码。
 */
class A2sStmtCompiler(
    private val symbols: A2sSymbolTable,
    private val exprCompiler: A2sExprCompiler,
) {
    /** 编译单个语句。事件处理器/函数体由外层方法包裹。 */
    fun compile(ctx: A2sCompileContext, stmt: A2sStmt) {
        when (stmt) {
            is A2sVarDecl -> compileVarDecl(ctx, stmt)
            is A2sAssign -> compileAssign(ctx, stmt)
            is A2sExprStmt -> {
                exprCompiler.compile(ctx, stmt.expr)
                ctx.mv.visitInsn(POP)
            }

            is A2sIf -> compileIf(ctx, stmt)
            is A2sWhen -> compileWhen(ctx, stmt)
            is A2sFor -> compileFor(ctx, stmt)
            is A2sWhile -> compileWhile(ctx, stmt)
            is A2sReturn -> compileReturn(ctx, stmt)
            is A2sBreak -> {
                emitFinally(ctx, ctx.finallyFramesLeavingLoop())
                ctx.mv.visitJumpInsn(GOTO, ctx.breakLabel())
            }
            is A2sContinue -> {
                emitFinally(ctx, ctx.finallyFramesLeavingLoop())
                ctx.mv.visitJumpInsn(GOTO, ctx.continueLabel())
            }
            is A2sThrow -> compileThrow(ctx, stmt)

            is A2sTry -> compileTry(ctx, stmt)
            is A2sPost -> compilePost(ctx, stmt)
        }
    }

    private fun compileVarDecl(ctx: A2sCompileContext, stmt: A2sVarDecl) {
        val initializer = stmt.initializer
        if (initializer != null) {
            exprCompiler.compile(ctx, initializer)
        } else {
            ctx.mv.visitInsn(ACONST_NULL)
        }
        val type = stmt.type ?: initializer?.let { symbols.inferType(it, ctx.localTypes()) } ?: A2sUnknown
        ctx.declareLocal(stmt.name, type, stmt.mutable)
        ctx.storeVariable(stmt.name)
    }

    private fun compileAssign(ctx: A2sCompileContext, stmt: A2sAssign) {
        if (stmt.target is A2sIdentifier) {
            val name = (stmt.target as A2sIdentifier).name
            when {
                ctx.hasLocal(name) -> {
                    if (!ctx.isMutableLocal(name)) {
                        throw A2sCompileError("无法赋值给 val 变量: $name")
                    }
                    exprCompiler.compile(ctx, stmt.value)
                    ctx.storeVariable(name)
                }
                ctx.isEventField(name) -> {
                    ctx.mv.visitVarInsn(ALOAD, ctx.scriptObjSlot)
                    exprCompiler.compile(ctx, stmt.value)
                    val fieldType = ctx.eventFieldType(name)
                    val desc = A2sTypeCodegen.boxedDescriptor(fieldType)
                    ctx.mv.visitFieldInsn(PUTFIELD, ctx.className, name, desc)
                }
                symbols.isTopLevelVar(name) -> {
                    if (!symbols.isTopLevelVarMutable(name)) {
                        throw A2sCompileError("无法赋值给 val 变量: $name")
                    }
                    ctx.mv.visitVarInsn(ALOAD, ctx.scriptObjSlot)
                    exprCompiler.compile(ctx, stmt.value)
                    val desc = A2sTypeCodegen.boxedDescriptor(symbols.topLevelVarType(name))
                    ctx.mv.visitFieldInsn(PUTFIELD, ctx.className, name, desc)
                }
                else -> throw A2sCompileError("未定义的变量: $name")
            }
            return
        }
        // 字段/索引赋值：简化为运行时辅助
        when (val target = stmt.target) {
            is A2sFieldAccess -> {
                val receiverType = symbols.inferType(target.receiver, ctx.localTypes())
                exprCompiler.compile(ctx, target.receiver)
                ctx.mv.visitLdcInsn(target.fieldName)
                exprCompiler.compile(ctx, stmt.value)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "setField", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V", false)
            }

            else -> throw A2sCompileError("不支持的赋值目标")
        }
    }

    private fun compileIf(ctx: A2sCompileContext, stmt: A2sIf) {
        val elseLabel = Label()
        val endLabel = Label()
        exprCompiler.compile(ctx, stmt.condition)
        A2sTypeCodegen.unbox(ctx.mv, A2sBoolean)
        ctx.mv.visitJumpInsn(IFEQ, elseLabel)
        compileBlock(ctx, stmt.thenBody)
        if (stmt.elseBody != null) {
            ctx.mv.visitJumpInsn(GOTO, endLabel)
            ctx.mv.visitLabel(elseLabel)
            compileBlock(ctx, stmt.elseBody)
            ctx.mv.visitLabel(endLabel)
        } else {
            ctx.mv.visitLabel(elseLabel)
        }
    }

    private fun compileWhen(ctx: A2sCompileContext, stmt: A2sWhen) {
        val endLabel = Label()
        val branches = stmt.entries.filterNot { it.isElse }
        val elseBody = stmt.entries.firstOrNull { it.isElse }?.body

        // 朴素方案：逐分支 if (subject == c1 || subject == c2) { body; goto end }
        for (entry in branches) {
            val skipLabel = Label()
            val bodyLabel = Label()
            var first = true
            for (cond in entry.conditions) {
                if (!first) {
                    ctx.mv.visitJumpInsn(IFNE, bodyLabel)
                }
                first = false
                exprCompiler.compile(ctx, stmt.subject)
                exprCompiler.compile(ctx, cond)
                ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_RUNTIME, "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
            }
            ctx.mv.visitJumpInsn(IFEQ, skipLabel)
            ctx.mv.visitLabel(bodyLabel)
            compileBlock(ctx, entry.body)
            ctx.mv.visitJumpInsn(GOTO, endLabel)
            ctx.mv.visitLabel(skipLabel)
        }
        if (elseBody != null) {
            compileBlock(ctx, elseBody)
        }
        ctx.mv.visitLabel(endLabel)
    }

    private fun compileFor(ctx: A2sCompileContext, stmt: A2sFor) {
        // 简化：for (x in iterable) → 通过运行时迭代
        // 编译 iterable，转 Iterator
        exprCompiler.compile(ctx, stmt.iterable)
        ctx.mv.visitTypeInsn(CHECKCAST, "java/lang/Iterable")
        ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true)
        val iterLocal = ctx.declareLocal("__iter", A2sAny)
        ctx.mv.visitVarInsn(ASTORE, iterLocal)

        val startLabel = Label()
        val endLabel = Label()
        ctx.pushLoop(startLabel, endLabel)

        ctx.mv.visitLabel(startLabel)
        ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_SANDBOX, "tickLoop", "()V", false)
        ctx.mv.visitVarInsn(ALOAD, iterLocal)
        ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true)
        ctx.mv.visitJumpInsn(IFEQ, endLabel)

        ctx.mv.visitVarInsn(ALOAD, iterLocal)
        ctx.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true)
        ctx.declareLocal(stmt.variable, A2sAny, mutable = true)
        ctx.storeVariable(stmt.variable)

        compileBlock(ctx, stmt.body)
        ctx.mv.visitJumpInsn(GOTO, startLabel)
        ctx.mv.visitLabel(endLabel)

        ctx.popLoop()
    }

    private fun compileWhile(ctx: A2sCompileContext, stmt: A2sWhile) {
        val startLabel = Label()
        val endLabel = Label()
        ctx.pushLoop(startLabel, endLabel)

        ctx.mv.visitLabel(startLabel)
        ctx.mv.visitMethodInsn(INVOKESTATIC, TYPE_SANDBOX, "tickLoop", "()V", false)
        exprCompiler.compile(ctx, stmt.condition)
        A2sTypeCodegen.unbox(ctx.mv, A2sBoolean)
        ctx.mv.visitJumpInsn(IFEQ, endLabel)
        compileBlock(ctx, stmt.body)
        ctx.mv.visitJumpInsn(GOTO, startLabel)
        ctx.mv.visitLabel(endLabel)

        ctx.popLoop()
    }

    private fun compileReturn(ctx: A2sCompileContext, stmt: A2sReturn) {
        if (stmt.value != null) {
            exprCompiler.compile(ctx, stmt.value)
            val frames = ctx.finallyFrames()
            if (frames.isNotEmpty()) {
                val tmp = ctx.allocateTemp()
                ctx.mv.visitVarInsn(ASTORE, tmp)
                emitFinally(ctx, frames.asReversed())
                ctx.mv.visitVarInsn(ALOAD, tmp)
            }
            ctx.mv.visitInsn(ARETURN)
        } else {
            emitFinally(ctx, ctx.finallyFrames().asReversed())
            ctx.mv.visitInsn(RETURN)
        }
    }

    private fun compileThrow(ctx: A2sCompileContext, stmt: A2sThrow) {
        exprCompiler.compile(ctx, stmt.expr)
        ctx.mv.visitTypeInsn(CHECKCAST, "java/lang/Throwable")
        ctx.mv.visitInsn(ATHROW)
    }

    private fun emitFinally(ctx: A2sCompileContext, frames: List<A2sFinallyFrame>) {
        for (frame in frames) {
            compileBlock(ctx, frame.body)
        }
    }

    fun compileAsValue(ctx: A2sCompileContext, stmts: List<A2sStmt>) {
        if (stmts.isEmpty()) {
            ctx.mv.visitInsn(ACONST_NULL)
            return
        }
        for (i in 0 until stmts.lastIndex) compile(ctx, stmts[i])
        when (val last = stmts.last()) {
            is A2sExprStmt -> exprCompiler.compile(ctx, last.expr)
            else -> {
                compile(ctx, last)
                ctx.mv.visitInsn(ACONST_NULL)
            }
        }
    }

    private fun compileTry(ctx: A2sCompileContext, stmt: A2sTry) {
        val tryStart = Label()
        val tryEnd = Label()
        val finallyLabel = if (stmt.finallyBody != null) Label() else null
        val doneLabel = Label()
        if (stmt.finallyBody != null) ctx.pushFinally(stmt.finallyBody)

        // catch handlers
        val handlerLabels = stmt.catches.map { Label() }
        // uncaught handler（用于执行 finally 后 rethrow）
        val uncaughtLabel = if (stmt.finallyBody != null) Label() else null

        // 注册 tryCatchBlock：try start → try end → handler
        for (h in handlerLabels) {
            ctx.mv.visitTryCatchBlock(tryStart, tryEnd, h, "java/lang/Throwable")
        }
        // uncaught 异常（finally 场景下需要执行 finally 再 rethrow）
        if (uncaughtLabel != null) {
            ctx.mv.visitTryCatchBlock(tryStart, tryEnd, uncaughtLabel, "java/lang/Throwable")
        } else if (stmt.catches.isEmpty() && stmt.finallyBody == null) {
            // 无 catch 无 finally：只是 try block，注册一个 handler 但不会触发
            // （实际不需要，因为无 catch 无 finally，try 块就是普通代码）
        }

        // try body
        ctx.mv.visitLabel(tryStart)
        compileBlock(ctx, stmt.body)
        ctx.mv.visitLabel(tryEnd)

        // 正常路径结束后执行 finally（若有）
        if (finallyLabel != null) {
            ctx.mv.visitJumpInsn(GOTO, finallyLabel)
        } else {
            ctx.mv.visitJumpInsn(GOTO, doneLabel)
        }

        // catch handlers
        for ((i, catch) in stmt.catches.withIndex()) {
            ctx.mv.visitLabel(handlerLabels[i])
            // catch 参数存入局部槽
            val catchSlot = ctx.declareLocal(catch.paramName, A2sAny)
            ctx.mv.visitVarInsn(ASTORE, catchSlot)
            // catch body
            compileBlock(ctx, catch.body)
            // catch body 执行完后执行 finally（若有）
            if (finallyLabel != null) {
                ctx.mv.visitJumpInsn(GOTO, finallyLabel)
            } else {
                ctx.mv.visitJumpInsn(GOTO, doneLabel)
            }
        }

        // uncaught handler：执行 finally 后 rethrow
        if (uncaughtLabel != null) {
            ctx.mv.visitLabel(uncaughtLabel)
            val tmpSlot = ctx.declareLocal("__ex", A2sAny)
            ctx.mv.visitVarInsn(ASTORE, tmpSlot)
            compileBlock(ctx, stmt.finallyBody!!)
            ctx.mv.visitVarInsn(ALOAD, tmpSlot)
            ctx.mv.visitTypeInsn(CHECKCAST, "java/lang/Throwable")
            ctx.mv.visitInsn(ATHROW)
        }

        // finally
        if (finallyLabel != null) {
            ctx.mv.visitLabel(finallyLabel)
            compileBlock(ctx, stmt.finallyBody!!)
            ctx.mv.visitJumpInsn(GOTO, doneLabel)
        }

        ctx.mv.visitLabel(doneLabel)
        if (stmt.finallyBody != null) ctx.popFinally()
    }

    private fun compilePost(ctx: A2sCompileContext, stmt: A2sPost) {
        ctx.mv.visitVarInsn(ALOAD, ctx.scriptObjSlot)
        ctx.mv.visitLdcInsn(stmt.eventType)
        ctx.mv.visitLdcInsn(stmt.arguments.size)
        ctx.mv.visitTypeInsn(ANEWARRAY, "java/lang/Object")
        for ((i, arg) in stmt.arguments.withIndex()) {
            ctx.mv.visitInsn(DUP)
            ctx.mv.visitLdcInsn(i)
            exprCompiler.compile(ctx, arg)
            ctx.mv.visitInsn(AASTORE)
        }
        ctx.mv.visitMethodInsn(
            INVOKESTATIC, TYPE_RUNTIME, "postEvent",
            "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)V", false
        )
    }

    private fun compileBlock(ctx: A2sCompileContext, stmts: List<A2sStmt>) {
        for (stmt in stmts) compile(ctx, stmt)
    }
}
