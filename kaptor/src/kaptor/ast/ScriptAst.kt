package kaptor.ast

sealed interface AstNode {
    val line: Int
    val col: Int
}

data class ScriptFile(
    val imports: List<ImportDecl>,
    val handlers: List<EventHandler>,
    override val line: Int = 0,
    override val col: Int = 0
) : AstNode

data class ImportDecl(
    val path: String,
    override val line: Int = 0,
    override val col: Int = 0
) : AstNode

data class EventHandler(
    val eventType: String,
    val hookType: HookType,
    val paramName: String?,
    val body: List<Statement>,
    val costLimit: Int = 1000,
    override val line: Int = 0,
    override val col: Int = 0
) : AstNode

enum class HookType {
    ON, BEFORE, AFTER
}

sealed interface Statement : AstNode

data class ValDecl(
    val name: String,
    val type: String?,
    val initializer: Expression,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class VarDecl(
    val name: String,
    val type: String?,
    val initializer: Expression?,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class Assignment(
    val target: Expression,
    val value: Expression,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class ExpressionStatement(
    val expr: Expression,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class IfStatement(
    val condition: Expression,
    val thenBranch: List<Statement>,
    val elseBranch: List<Statement>?,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class WhileStatement(
    val condition: Expression,
    val body: List<Statement>,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class ForStatement(
    val variable: String,
    val iterable: Expression,
    val body: List<Statement>,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class ReturnStatement(
    val value: Expression?,
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class BreakStatement(
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

data class ContinueStatement(
    override val line: Int = 0,
    override val col: Int = 0
) : Statement

sealed interface Expression : AstNode

data class StringLiteral(
    val value: String,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class IntLiteral(
    val value: Long,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class FloatLiteral(
    val value: Double,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class BoolLiteral(
    val value: Boolean,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class NullLiteral(
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class Identifier(
    val name: String,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class StringInterpolation(
    val parts: List<InterpolationPart>,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

sealed interface InterpolationPart
data class LiteralPart(val text: String) : InterpolationPart
data class ExpressionPart(val expr: Expression) : InterpolationPart

data class FieldAccess(
    val receiver: Expression,
    val fieldName: String,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class MethodCall(
    val receiver: Expression,
    val methodName: String,
    val arguments: List<Expression>,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class FunctionCall(
    val name: String,
    val arguments: List<Expression>,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class BinaryExpression(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class UnaryExpression(
    val operator: UnaryOperator,
    val operand: Expression,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class ParenExpression(
    val inner: Expression,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

data class IndexAccess(
    val receiver: Expression,
    val index: Expression,
    override val line: Int = 0,
    override val col: Int = 0
) : Expression

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
