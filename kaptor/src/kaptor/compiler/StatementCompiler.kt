package kaptor.compiler

import kaptor.ir.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

fun compileInstruction(ctx: MethodContext, instr: IrInstruction) {
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
    ctx.consumeCost(instr.cost)
    ctx.checkCostLimit("Cost exceeded in val declaration: ${instr.name}")
    ctx.declareLocal(instr.name, "Ljava/lang/Object;")
    compileExpression(ctx, instr.initializer)
    ctx.mv.visitVarInsn(ASTORE, ctx.getLocal(instr.name))
}

private fun compileVarDecl(ctx: MethodContext, instr: IrVarDecl) {
    ctx.consumeCost(instr.cost)
    ctx.checkCostLimit("Cost exceeded in var declaration: ${instr.name}")
    ctx.declareLocal(instr.name, "Ljava/lang/Object;")
    if (instr.initializer != null) {
        compileExpression(ctx, instr.initializer)
    } else {
        ctx.mv.visitInsn(ACONST_NULL)
    }
    ctx.mv.visitVarInsn(ASTORE, ctx.getLocal(instr.name))
}

private fun compileAssignment(ctx: MethodContext, instr: IrAssignment) {
    ctx.consumeCost(instr.cost)
    ctx.checkCostLimit("Cost exceeded in assignment")
    compileExpression(ctx, instr.value)
    compileStoreTarget(ctx, instr.target)
}

private fun compileExprStmt(ctx: MethodContext, instr: IrExpressionStatement) {
    ctx.consumeCost(instr.cost)
    ctx.checkCostLimit("Cost exceeded in expression statement")
    compileExpression(ctx, instr.expr)
    ctx.mv.visitInsn(POP)
}

private fun compileIf(ctx: MethodContext, instr: IrIfStatement) {
    val mv = ctx.mv
    ctx.consumeCost(3)
    ctx.checkCostLimit("Cost exceeded in if statement")

    val elseLabel = Label()
    val endLabel = Label()

    compileExpression(ctx, instr.condition)
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
    mv.visitJumpInsn(IFEQ, elseLabel)

    for (stmt in instr.thenBranch) {
        compileInstruction(ctx, stmt)
    }

    if (instr.elseBranch != null) {
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(elseLabel)
        for (stmt in instr.elseBranch) {
            compileInstruction(ctx, stmt)
        }
        mv.visitLabel(endLabel)
    } else {
        mv.visitLabel(elseLabel)
    }
}

private fun compileWhile(ctx: MethodContext, instr: IrWhileStatement) {
    val mv = ctx.mv
    val startLabel = Label()
    val endLabel = Label()

    ctx.loopEndLabels.add(endLabel)
    ctx.loopStartLabels.add(startLabel)

    mv.visitLabel(startLabel)
    ctx.consumeCost(5)
    ctx.checkCostLimit("Cost exceeded in while loop")

    compileExpression(ctx, instr.condition)
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
    mv.visitJumpInsn(IFEQ, endLabel)

    for (stmt in instr.body) {
        compileInstruction(ctx, stmt)
    }

    mv.visitJumpInsn(GOTO, startLabel)
    mv.visitLabel(endLabel)

    ctx.loopEndLabels.removeLast()
    ctx.loopStartLabels.removeLast()
}

private fun compileFor(ctx: MethodContext, instr: IrForStatement) {
    val mv = ctx.mv
    val startLabel = Label()
    val endLabel = Label()

    ctx.loopEndLabels.add(endLabel)
    ctx.loopStartLabels.add(startLabel)

    ctx.consumeCost(5)
    ctx.checkCostLimit("Cost exceeded in for loop")

    compileExpression(ctx, instr.iterable)

    ctx.declareLocal("__iter_${instr.variable}", "Ljava/util/Iterator;")
    mv.visitTypeInsn(CHECKCAST, "java/lang/Iterable")
    mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true)
    mv.visitVarInsn(ASTORE, ctx.getLocal("__iter_${instr.variable}"))

    mv.visitLabel(startLabel)
    mv.visitVarInsn(ALOAD, ctx.getLocal("__iter_${instr.variable}"))
    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true)
    mv.visitJumpInsn(IFEQ, endLabel)

    mv.visitVarInsn(ALOAD, ctx.getLocal("__iter_${instr.variable}"))
    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true)

    ctx.declareLocal(instr.variable, "Ljava/lang/Object;")
    mv.visitVarInsn(ASTORE, ctx.getLocal(instr.variable))

    for (stmt in instr.body) {
        compileInstruction(ctx, stmt)
    }

    mv.visitJumpInsn(GOTO, startLabel)
    mv.visitLabel(endLabel)

    ctx.loopEndLabels.removeLast()
    ctx.loopStartLabels.removeLast()
}

private fun compileReturn(ctx: MethodContext, instr: IrReturnStatement) {
    ctx.consumeCost(instr.cost)
    if (instr.value != null) {
        compileExpression(ctx, instr.value)
        ctx.mv.visitInsn(ARETURN)
    } else {
        ctx.mv.visitInsn(RETURN)
    }
}

private fun compileBreak(ctx: MethodContext) {
    ctx.consumeCost(1)
    if (ctx.loopEndLabels.isEmpty()) {
        throw ScriptCompileError("break outside of loop")
    }
    ctx.mv.visitJumpInsn(GOTO, ctx.loopEndLabels.last())
}

private fun compileContinue(ctx: MethodContext) {
    ctx.consumeCost(1)
    if (ctx.loopStartLabels.isEmpty()) {
        throw ScriptCompileError("continue outside of loop")
    }
    ctx.mv.visitJumpInsn(GOTO, ctx.loopStartLabels.last())
}

private fun compileStoreTarget(ctx: MethodContext, target: IrExpression) {
    val mv = ctx.mv
    when (target) {
        is IrIdentifier -> {
            mv.visitVarInsn(ASTORE, ctx.getLocal(target.name))
        }

        is IrFieldAccess -> {
            compileExpression(ctx, target.receiver)
            if (ctx.eventClassName != null && ctx.isEventVariable(target.receiver)) {
                val setterName = "set${target.fieldName.replaceFirstChar { it.uppercase() }}"
                mv.visitInsn(SWAP)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL, ctx.eventClassName, setterName, "(Ljava/lang/Object;)V", false
                )
            } else {
                mv.visitTypeInsn(CHECKCAST, "java/util/Map")
                mv.visitLdcInsn(target.fieldName)
                mv.visitInsn(SWAP)
                mv.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/util/Map",
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    true
                )
                mv.visitInsn(POP)
            }
        }

        is IrIndexAccess -> {
            compileExpression(ctx, target.receiver)
            compileExpression(ctx, target.index)
            mv.visitInsn(SWAP)
            mv.visitMethodInsn(
                INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true
            )
            mv.visitInsn(POP)
        }

        else -> throw ScriptCompileError("Invalid assignment target")
    }
}
