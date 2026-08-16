package kaptor.ir

sealed interface IrNode {
    val cost: Int
}

data class IrScriptFile(val handlers: List<IrHandler>) : IrNode {
    override val cost: Int get() = handlers.sumOf { it.cost }
}

data class IrHandler(
    val eventType: String,
    val hookType: HookType,
    val paramName: String?,
    val body: List<IrInstruction>,
    val costLimit: Int,
    val line: Int = 0,
    val col: Int = 0
) : IrNode {
    override val cost: Int get() = body.sumOf { it.cost }
}

sealed interface IrInstruction : IrNode {
    fun mergeWith(other: IrInstruction): IrInstruction? = null
}

data class IrValDecl(
    val name: String,
    val initializer: IrExpression,
    override val cost: Int = 1,
) : IrInstruction

data class IrVarDecl(
    val name: String,
    val initializer: IrExpression?,
    override val cost: Int = 1,
) : IrInstruction

data class IrAssignment(
    val target: IrExpression,
    val value: IrExpression,
    override val cost: Int = 1,
) : IrInstruction {
    override fun mergeWith(other: IrInstruction): IrInstruction? {
        if (other is IrAssignment && other.target == target) {
            return IrAssignment(target, other.value, cost + other.cost)
        }
        return null
    }
}

data class IrExpressionStatement(
    val expr: IrExpression,
    override val cost: Int = expr.cost,
) : IrInstruction {
    override fun mergeWith(other: IrInstruction): IrInstruction? {
        if (other is IrExpressionStatement && expr.canMergeWith(other.expr)) {
            return IrExpressionStatement(IrMergedExpression(listOf(expr, other.expr)), cost + other.cost)
        }
        return null
    }
}

data class IrIfStatement(
    val condition: IrExpression,
    val thenBranch: List<IrInstruction>,
    val elseBranch: List<IrInstruction>?,
    override val cost: Int = 3 + thenBranch.sumOf { it.cost } + (elseBranch?.sumOf { it.cost } ?: 0)
) : IrInstruction

data class IrWhileStatement(
    val condition: IrExpression,
    val body: List<IrInstruction>,
    override val cost: Int = 5
) : IrInstruction

data class IrForStatement(
    val variable: String,
    val iterable: IrExpression,
    val body: List<IrInstruction>,
    override val cost: Int = 5
) : IrInstruction

data class IrReturnStatement(
    val value: IrExpression?,
    override val cost: Int = 2
) : IrInstruction

data class IrBreakStatement(
    override val cost: Int = 1
) : IrInstruction

data class IrContinueStatement(
    override val cost: Int = 1
) : IrInstruction

sealed interface IrExpression : IrNode {
    fun canMergeWith(other: IrExpression): Boolean = false
}

data class IrStringLiteral(
    val value: String,
    override val cost: Int = 1
) : IrExpression

data class IrIntLiteral(
    val value: Int,
    override val cost: Int = 1
) : IrExpression

data class IrLongLiteral(
    val value: Long,
    override val cost: Int = 1
) : IrExpression

data class IrFloatLiteral(
    val value: Double,
    val numericType: IrType = IrDoubleType,
    override val cost: Int = 1
) : IrExpression

data class IrBoolLiteral(
    val value: Boolean,
    override val cost: Int = 1
) : IrExpression

data class IrNullLiteral(
    override val cost: Int = 1
) : IrExpression

data class IrIdentifier(
    val name: String,
    val type: IrType = IrObjectType,
    override val cost: Int = 1
) : IrExpression {
    override fun canMergeWith(other: IrExpression): Boolean = other is IrIdentifier && other.name == name
}

data class IrStringInterpolation(
    val parts: List<IrInterpolationPart>,
    override val cost: Int = parts.sumOf { it.cost }
) : IrExpression

sealed interface IrInterpolationPart : IrNode
data class IrLiteralPart(val text: String) : IrInterpolationPart {
    override val cost: Int = 1
}

data class IrExpressionPart(val expr: IrExpression) : IrInterpolationPart {
    override val cost: Int = expr.cost
}

data class IrFieldAccess(
    val receiver: IrExpression,
    val fieldName: String,
    val fieldType: IrType = IrObjectType,
    override val cost: Int = 2 + receiver.cost
) : IrExpression {
    override fun canMergeWith(other: IrExpression): Boolean =
        other is IrFieldAccess && receiver == other.receiver && fieldName == other.fieldName
}

data class IrMethodCall(
    val receiver: IrExpression,
    val methodName: String,
    val arguments: List<IrExpression>,
    override val cost: Int = 3 + receiver.cost + arguments.sumOf { it.cost }
) : IrExpression

data class IrFunctionCall(
    val name: String,
    val arguments: List<IrExpression>,
    override val cost: Int = 3 + arguments.sumOf { it.cost }
) : IrExpression

data class IrBinaryExpression(
    val left: IrExpression,
    val operator: BinaryOperator,
    val right: IrExpression,
    val resultType: IrType = IrObjectType,
    override val cost: Int = 1 + left.cost + right.cost
) : IrExpression {
    override fun canMergeWith(other: IrExpression): Boolean = false
}

data class IrUnaryExpression(
    val operator: UnaryOperator,
    val operand: IrExpression,
    val resultType: IrType = IrObjectType,
    override val cost: Int = 1 + operand.cost
) : IrExpression

data class IrIndexAccess(
    val receiver: IrExpression,
    val index: IrExpression,
    override val cost: Int = 2 + receiver.cost + index.cost
) : IrExpression

data class IrMergedExpression(
    val expressions: List<IrExpression>,
    override val cost: Int = expressions.sumOf { it.cost }
) : IrExpression {
    override fun canMergeWith(other: IrExpression): Boolean = true
}

enum class HookType {
    ON, BEFORE, AFTER
}

enum class BinaryOperator {
    PLUS, MINUS, MULTIPLY, DIVIDE, MODULO,
    EQUALS, NOT_EQUALS, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
    AND, OR,
    BIT_AND, BIT_OR, BIT_XOR,
    SHL, SHR
}

enum class UnaryOperator {
    MINUS, NOT, BIT_NOT
}
