package kaptor.ir

import kaptor.ast.*

class ScriptLowering {
    fun lower(file: ScriptFile): IrScriptFile {
        val handlers = file.handlers.map { lowerHandler(it) }
        return IrScriptFile(handlers)
    }

    private fun lowerHandler(handler: EventHandler): IrHandler {
        val body = handler.body.map { lowerStatement(it) }
        return IrHandler(
            eventType = handler.eventType,
            hookType = handler.hookType,
            paramName = handler.paramName,
            body = mergeInstructions(body),
            costLimit = handler.costLimit
        )
    }

    private fun lowerStatement(stmt: Statement): IrInstruction {
        return when (stmt) {
            is ValDecl -> IrValDecl(stmt.name, lowerExpression(stmt.initializer))
            is VarDecl -> IrVarDecl(stmt.name, stmt.initializer?.let { lowerExpression(it) })
            is Assignment -> IrAssignment(lowerExpression(stmt.target), lowerExpression(stmt.value))
            is ExpressionStatement -> IrExpressionStatement(lowerExpression(stmt.expr))
            is IfStatement -> IrIfStatement(
                condition = lowerExpression(stmt.condition),
                thenBranch = mergeInstructions(stmt.thenBranch.map { lowerStatement(it) }),
                elseBranch = stmt.elseBranch?.let { mergeInstructions(it.map { s -> lowerStatement(s) }) }
            )
            is WhileStatement -> IrWhileStatement(
                condition = lowerExpression(stmt.condition),
                body = mergeInstructions(stmt.body.map { lowerStatement(it) })
            )
            is ForStatement -> IrForStatement(
                variable = stmt.variable,
                iterable = lowerExpression(stmt.iterable),
                body = mergeInstructions(stmt.body.map { lowerStatement(it) })
            )
            is ReturnStatement -> IrReturnStatement(stmt.value?.let { lowerExpression(it) })
            is BreakStatement -> IrBreakStatement()
            is ContinueStatement -> IrContinueStatement()
        }
    }

    fun lowerExpression(expr: Expression): IrExpression {
        return when (expr) {
            is StringLiteral -> IrStringLiteral(expr.value)
            is IntLiteral -> IrIntLiteral(expr.value)
            is FloatLiteral -> IrFloatLiteral(expr.value)
            is BoolLiteral -> IrBoolLiteral(expr.value)
            is NullLiteral -> IrNullLiteral()
            is Identifier -> IrIdentifier(expr.name)
            is StringInterpolation -> IrStringInterpolation(
                expr.parts.map { part ->
                    when (part) {
                        is LiteralPart -> IrLiteralPart(part.text)
                        is ExpressionPart -> IrExpressionPart(lowerExpression(part.expr))
                    }
                }
            )
            is FieldAccess -> IrFieldAccess(lowerExpression(expr.receiver), expr.fieldName)
            is MethodCall -> IrMethodCall(
                receiver = lowerExpression(expr.receiver),
                methodName = expr.methodName,
                arguments = expr.arguments.map { lowerExpression(it) }
            )
            is FunctionCall -> IrFunctionCall(
                name = expr.name,
                arguments = expr.arguments.map { lowerExpression(it) }
            )
            is BinaryExpression -> IrBinaryExpression(
                left = lowerExpression(expr.left),
                operator = expr.operator,
                right = lowerExpression(expr.right)
            )
            is UnaryExpression -> IrUnaryExpression(
                operator = expr.operator,
                operand = lowerExpression(expr.operand)
            )
            is ParenExpression -> lowerExpression(expr.inner)
            is IndexAccess -> IrIndexAccess(
                receiver = lowerExpression(expr.receiver),
                index = lowerExpression(expr.index)
            )
        }
    }

    private fun mergeInstructions(instructions: List<IrInstruction>): List<IrInstruction> {
        if (instructions.isEmpty()) return emptyList()

        val merged = mutableListOf<IrInstruction>()
        var current = instructions[0]

        for (i in 1 until instructions.size) {
            val next = instructions[i]
            val mergedInstr = current.mergeWith(next)
            if (mergedInstr != null && shouldMerge(current, next, mergedInstr)) {
                current = mergedInstr
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return merged
    }

    private fun shouldMerge(
        a: IrInstruction,
        b: IrInstruction,
        merged: IrInstruction
    ): Boolean {
        if (merged.cost > MAX_MERGE_COST) return false
        if (merged.cost > (a.cost + b.cost)) return false
        if (a is IrIfStatement || a is IrWhileStatement || a is IrForStatement) return false
        if (b is IrIfStatement || b is IrWhileStatement || b is IrForStatement) return false
        return true
    }

    companion object {
        const val MAX_MERGE_COST = 10
    }
}
