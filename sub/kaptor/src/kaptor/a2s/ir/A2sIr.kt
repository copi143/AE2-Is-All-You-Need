package kaptor.a2s.ir

/**
 * a2s 中间表示（IR）。
 *
 * 结构：A2sScriptFile 包含若干顶层声明（事件声明、函数声明、val/var、事件处理器）。
 * 事件处理器 body 是语句列表，语句和表达式与 kaptor 类似，但针对 a2s 类型系统扩展。
 */
data class A2sScriptFile(
    val events: List<A2sEventDecl>,
    val functions: List<A2sFunctionDecl>,
    val topLevelVars: List<A2sVarDecl>,
    val handlers: List<A2sHandler>,
)

// ── 顶层声明 ──

data class A2sEventDecl(
    val name: String,
    val params: List<A2sParam>,
    val methods: List<A2sFunctionDecl>,
)

data class A2sParam(
    val name: String,
    val type: A2sType,
)

data class A2sFunctionDecl(
    val name: String,
    val params: List<A2sParam>,
    val returnType: A2sType?,
    val body: A2sFunctionBody,
)

sealed interface A2sFunctionBody {
    /** 表达式体：`fun f() = expr` */
    data class Expr(val expr: A2sExpr) : A2sFunctionBody

    /** 块体：`fun f() { ... }` */
    data class Block(val statements: List<A2sStmt>) : A2sFunctionBody
}

enum class A2sHookType { ON, BEFORE, AFTER }

data class A2sHandler(
    val eventType: String,
    val hookType: A2sHookType,
    val paramName: String?,
    val body: List<A2sStmt>,
)

// ── 语句 ──

sealed interface A2sStmt

data class A2sVarDecl(
    val name: String,
    val type: A2sType?,
    val initializer: A2sExpr?,
    val mutable: Boolean = false,
) : A2sStmt

data class A2sAssign(
    val target: A2sExpr,
    val value: A2sExpr,
) : A2sStmt

data class A2sExprStmt(val expr: A2sExpr) : A2sStmt

data class A2sIf(
    val condition: A2sExpr,
    val thenBody: List<A2sStmt>,
    val elseBody: List<A2sStmt>?,
) : A2sStmt

data class A2sWhen(
    val subject: A2sExpr,
    val entries: List<A2sWhenEntry>,
) : A2sStmt

data class A2sWhenEntry(
    val conditions: List<A2sExpr>,
    val body: List<A2sStmt>,
    val isElse: Boolean = false,
)

data class A2sFor(
    val variable: String,
    val iterable: A2sExpr,
    val body: List<A2sStmt>,
) : A2sStmt

data class A2sWhile(
    val condition: A2sExpr,
    val body: List<A2sStmt>,
) : A2sStmt

data class A2sReturn(val value: A2sExpr?) : A2sStmt

object A2sBreak : A2sStmt
object A2sContinue : A2sStmt

data class A2sThrow(val expr: A2sExpr) : A2sStmt

data class A2sTry(
    val body: List<A2sStmt>,
    val catches: List<A2sCatch>,
    val finallyBody: List<A2sStmt>?,
) : A2sStmt

data class A2sCatch(
    val paramName: String,
    val body: List<A2sStmt>,
)

data class A2sPost(
    val eventType: String,
    val arguments: List<A2sExpr>,
) : A2sStmt

// ── 表达式 ──

sealed interface A2sExpr {
    var type: A2sType
}

data class A2sBigIntLiteral(val value: String) : A2sExpr {
    override var type: A2sType = A2sBigInt
}

data class A2sRationalLiteral(val value: String) : A2sExpr {
    override var type: A2sType = A2sRational
}

data class A2sI32Literal(val value: Int) : A2sExpr {
    override var type: A2sType = A2sI32
}

data class A2sI64Literal(val value: Long) : A2sExpr {
    override var type: A2sType = A2sI64
}

data class A2sF32Literal(val value: Float) : A2sExpr {
    override var type: A2sType = A2sF32
}

data class A2sF64Literal(val value: Double) : A2sExpr {
    override var type: A2sType = A2sF64
}

data class A2sBoolLiteral(val value: Boolean) : A2sExpr {
    override var type: A2sType = A2sBoolean
}

data class A2sStringLiteral(val value: String) : A2sExpr {
    override var type: A2sType = A2sString
}

object A2sNullLiteral : A2sExpr {
    override var type: A2sType = A2sUnknown
}

data class A2sIdentifier(val name: String) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** 资源引用（反引号）：`` `diamond` ``、`` `item|minecraft:diamond` `` */
data class A2sResourceRef(
    val raw: String,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** 字段访问：`obj.field` / `obj?.field` */
data class A2sFieldAccess(
    val receiver: A2sExpr,
    val fieldName: String,
    val safe: Boolean = false,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** 方法调用：`obj.method(args)` */
data class A2sMethodCall(
    val receiver: A2sExpr,
    val methodName: String,
    val arguments: List<A2sExpr>,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** 函数调用：`foo(args)` */
data class A2sCall(
    val name: String,
    val arguments: List<A2sExpr>,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** 索引访问：`list[i]` */
data class A2sIndexAccess(
    val receiver: A2sExpr,
    val index: A2sExpr,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

enum class A2sBinaryOp {
    PLUS, MINUS, MULTIPLY, DIVIDE, MODULO,
    EQUALS, NOT_EQUALS, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
    AND, OR,
    RANGE,
}

data class A2sBinary(
    val left: A2sExpr,
    val op: A2sBinaryOp,
    val right: A2sExpr,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

enum class A2sUnaryOp { MINUS, NOT }

data class A2sUnary(
    val op: A2sUnaryOp,
    val operand: A2sExpr,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** 字符串模板部分 */
sealed interface A2sStrPart

data class A2sStrText(val text: String) : A2sStrPart
data class A2sStrExpr(val expr: A2sExpr) : A2sStrPart

data class A2sStringInterpolation(val parts: List<A2sStrPart>) : A2sExpr {
    override var type: A2sType = A2sString
}

/** lambda：`{ e, f -> ... }`，参数必须显式起名 */
data class A2sLambda(
    val params: List<A2sParam>,
    val body: List<A2sStmt>,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** if 表达式（语法上是表达式，语句位置由 visitor 转为 A2sIf） */
data class A2sIfExpr(
    val condition: A2sExpr,
    val thenBody: List<A2sStmt>,
    val elseBody: List<A2sStmt>?,
) : A2sExpr {
    override var type: A2sType = A2sUnit
}

/** when 表达式（语法上是表达式，语句位置由 visitor 转为 A2sWhen） */
data class A2sWhenExpr(
    val subject: A2sExpr,
    val entries: List<A2sWhenEntry>,
) : A2sExpr {
    override var type: A2sType = A2sUnit
}

/** elvis `a ?: b`：若 a 为 null 则取 b，否则取 a。右结合。 */
data class A2sElvis(
    val left: A2sExpr,
    val right: A2sExpr,
) : A2sExpr {
    override var type: A2sType = A2sUnknown
}

/** 非空断言 `a!!`：a 为 null 则抛 NullPointerException，否则返回 a。 */
data class A2sNotNull(val expr: A2sExpr) : A2sExpr {
    override var type: A2sType = A2sUnknown
}
