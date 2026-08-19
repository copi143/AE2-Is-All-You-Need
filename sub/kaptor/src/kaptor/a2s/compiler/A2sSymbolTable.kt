package kaptor.a2s.compiler

import kaptor.a2s.ir.*
import kaptor.a2s.ir.ifUnknown

/**
 * 符号表：事件字段类型、函数签名，以及类型推断。
 */
class A2sSymbolTable(
    val events: List<A2sEventDecl>,
    val functions: List<A2sFunctionDecl>,
    val topLevelVars: List<A2sVarDecl> = emptyList(),
) {
    private val eventFields: Map<String, Map<String, A2sType>> =
        events.associate { it.name to it.params.associate { p -> p.name to p.type } }

    private val functionSigs: Map<String, A2sFunctionDecl> = functions.associateBy { it.name }

    private val topLevelVarTypes: Map<String, A2sType> =
        topLevelVars.associate { it.name to (it.type ?: inferLiteralType(it.initializer)) }

    private fun inferLiteralType(expr: A2sExpr?): A2sType = when (expr) {
        is A2sBigIntLiteral -> A2sBigInt
        is A2sRationalLiteral -> A2sRational
        is A2sI32Literal -> A2sI32
        is A2sI64Literal -> A2sI64
        is A2sF32Literal -> A2sF32
        is A2sF64Literal -> A2sF64
        is A2sBoolLiteral -> A2sBoolean
        is A2sStringLiteral -> A2sString
        is A2sStringInterpolation -> A2sString
        is A2sResourceRef -> A2sAny
        else -> A2sUnknown
    }

    fun isTopLevelVar(name: String): Boolean = topLevelVarTypes.containsKey(name)

    fun topLevelVarType(name: String): A2sType = topLevelVarTypes[name] ?: A2sUnknown

    fun eventFieldType(eventType: String, field: String): A2sType =
        eventFields[eventType]?.get(field) ?: A2sUnknown

    fun eventFieldTypes(eventType: String): Map<String, A2sType> =
        eventFields[eventType] ?: emptyMap()

    fun functionReturnType(name: String): A2sType =
        functionSigs[name]?.returnType ?: A2sUnknown

    fun functionParamCount(name: String): Int =
        functionSigs[name]?.params?.size ?: -1

    /** 推断表达式类型。局部变量类型由 [locals] 提供（name → type）。 */
    fun inferType(expr: A2sExpr, locals: Map<String, A2sType>): A2sType = when (expr) {
        is A2sBigIntLiteral -> A2sBigInt
        is A2sRationalLiteral -> A2sRational
        is A2sI32Literal -> A2sI32
        is A2sI64Literal -> A2sI64
        is A2sF32Literal -> A2sF32
        is A2sF64Literal -> A2sF64
        is A2sBoolLiteral -> A2sBoolean
        is A2sStringLiteral -> A2sString
        is A2sNullLiteral -> A2sUnknown
        is A2sStringInterpolation -> A2sString
        is A2sIdentifier -> locals[expr.name] ?: topLevelVarType(expr.name)
        is A2sResourceRef -> A2sAny
        is A2sLambda -> A2sLambdaType
        is A2sIfExpr -> A2sUnit
        is A2sWhenExpr -> A2sUnit
        is A2sElvis -> inferType(expr.left, locals).ifUnknown { inferType(expr.right, locals) }
        is A2sNotNull -> inferType(expr.expr, locals)
        is A2sFieldAccess -> inferFieldAccessType(expr, locals)
        is A2sMethodCall -> A2sUnknown
        is A2sCall -> inferCallType(expr)
        is A2sIndexAccess -> A2sUnknown
        is A2sBinary -> inferBinaryType(expr, locals)
        is A2sUnary -> inferType(expr.operand, locals)
    }

    private fun inferFieldAccessType(expr: A2sFieldAccess, locals: Map<String, A2sType>): A2sType {
        val receiverType = inferType(expr.receiver, locals)
        if (receiverType is A2sEventType) {
            return eventFieldType(receiverType.eventName, expr.fieldName)
        }
        return A2sUnknown
    }

    private fun inferCallType(expr: A2sCall): A2sType {
        return when (expr.name) {
            "println" -> A2sUnit
            "listOf" -> A2sListType(A2sAny)
            "toInt", "toI64" -> A2sAny
            "len" -> A2sI32
            else -> functionReturnType(expr.name)
        }
    }

    private fun inferBinaryType(expr: A2sBinary, locals: Map<String, A2sType>): A2sType {
        val left = inferType(expr.left, locals)
        val right = inferType(expr.right, locals)
        return when (expr.op) {
            A2sBinaryOp.EQUALS, A2sBinaryOp.NOT_EQUALS,
            A2sBinaryOp.LESS, A2sBinaryOp.LESS_EQUAL,
            A2sBinaryOp.GREATER, A2sBinaryOp.GREATER_EQUAL,
            A2sBinaryOp.AND, A2sBinaryOp.OR,
            -> A2sBoolean

            A2sBinaryOp.PLUS -> if (left == A2sString || right == A2sString) A2sString else A2sTypeCodegen.promoteNumeric(left, right)
            A2sBinaryOp.DIVIDE -> if (A2sTypeCodegen.promoteNumeric(left, right) == A2sBigInt) A2sRational else A2sTypeCodegen.promoteNumeric(left, right)
            A2sBinaryOp.RANGE -> A2sAny
            else -> A2sTypeCodegen.promoteNumeric(left, right)
        }
    }
}
