package kaptor.a2s.parser

import kaptor.a2s.ir.*
import kaptor.a2s.resource.ResourceResolver
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor

/**
 * 将 ANTLR ParseTree 转换为 a2s IR。
 *
 * @param resolver 资源解析器，用于解析资源类型名与资源引用。可为 null（测试/纯语法转换时，
 *                 此时资源类型名退化为 A2sUnknown，资源引用退化为 A2sAny）。
 */
class A2sVisitor(private val resolver: ResourceResolver? = null) :
    AbstractParseTreeVisitor<Any?>(), A2sParserVisitor<Any?> {

    override fun visitScript(ctx: A2sParser.ScriptContext): A2sScriptFile {
        val events = mutableListOf<A2sEventDecl>()
        val functions = mutableListOf<A2sFunctionDecl>()
        val topLevelVars = mutableListOf<A2sVarDecl>()
        val handlers = mutableListOf<A2sHandler>()

        for (decl in ctx.topLevelDeclaration()) {
            when (val d = visit(decl)) {
                is A2sEventDecl -> events.add(d)
                is A2sFunctionDecl -> functions.add(d)
                is A2sVarDecl -> topLevelVars.add(d)
                is A2sHandler -> handlers.add(d)
            }
        }
        return A2sScriptFile(events, functions, topLevelVars, handlers)
    }

    override fun visitTopLevelDeclaration(ctx: A2sParser.TopLevelDeclarationContext): Any? {
        return when {
            ctx.valDecl() != null -> visit(ctx.valDecl())
            ctx.varDecl() != null -> visit(ctx.varDecl())
            ctx.funDecl() != null -> visit(ctx.funDecl())
            ctx.eventDecl() != null -> visit(ctx.eventDecl())
            ctx.eventHandler() != null -> visit(ctx.eventHandler())
            else -> null
        }
    }

    // ── 声明 ──

    override fun visitValDecl(ctx: A2sParser.ValDeclContext): A2sVarDecl {
        return A2sVarDecl(
            name = ctx.Identifier().text,
            type = ctx.type()?.let { visit(it) as A2sType },
            initializer = visit(ctx.expression()) as A2sExpr,
            mutable = false,
        )
    }

    override fun visitVarDecl(ctx: A2sParser.VarDeclContext): A2sVarDecl {
        return A2sVarDecl(
            name = ctx.Identifier().text,
            type = ctx.type()?.let { visit(it) as A2sType },
            initializer = visit(ctx.expression()) as A2sExpr,
            mutable = true,
        )
    }

    override fun visitFunDecl(ctx: A2sParser.FunDeclContext): A2sFunctionDecl {
        return A2sFunctionDecl(
            name = ctx.Identifier().text,
            params = ctx.funParams()?.funParam()?.map { visit(it) as A2sParam } ?: emptyList(),
            returnType = ctx.type()?.let { visit(it) as A2sType },
            body = visit(ctx.funBody()) as A2sFunctionBody,
        )
    }

    override fun visitFunParam(ctx: A2sParser.FunParamContext): A2sParam {
        return A2sParam(
            name = ctx.Identifier().text,
            type = visit(ctx.type()) as A2sType,
        )
    }

    override fun visitFunBody(ctx: A2sParser.FunBodyContext): A2sFunctionBody {
        return if (ctx.expression() != null) {
            A2sFunctionBody.Expr(visit(ctx.expression()) as A2sExpr)
        } else {
            A2sFunctionBody.Block(ctx.block().statement().map { visit(it) as A2sStmt })
        }
    }

    override fun visitEventDecl(ctx: A2sParser.EventDeclContext): A2sEventDecl {
        return A2sEventDecl(
            name = ctx.Identifier().text,
            params = ctx.eventParams()?.eventParam()?.map { visit(it) as A2sParam } ?: emptyList(),
            methods = ctx.funDecl().map { visit(it) as A2sFunctionDecl },
        )
    }

    override fun visitEventParam(ctx: A2sParser.EventParamContext): A2sParam {
        return A2sParam(
            name = ctx.Identifier().text,
            type = visit(ctx.type()) as A2sType,
        )
    }

    override fun visitEventHandler(ctx: A2sParser.EventHandlerContext): A2sHandler {
        val hookType = when (ctx.hookType().text) {
            "on" -> A2sHookType.ON
            "before" -> A2sHookType.BEFORE
            "after" -> A2sHookType.AFTER
            else -> A2sHookType.ON
        }
        val lambda = visit(ctx.lambda()) as A2sLambda
        return A2sHandler(
            eventType = ctx.Identifier().text,
            hookType = hookType,
            paramName = lambda.params.firstOrNull()?.name,
            body = lambda.body,
        )
    }

    override fun visitHookType(ctx: A2sParser.HookTypeContext): Any? = ctx.text

    // ── 语句 ──

    override fun visitStatement(ctx: A2sParser.StatementContext): Any? {
        return when {
            ctx.valDecl() != null -> visit(ctx.valDecl())
            ctx.varDecl() != null -> visit(ctx.varDecl())
            ctx.assignment() != null -> visit(ctx.assignment())
            ctx.postStatement() != null -> visit(ctx.postStatement())
            ctx.exprStatement() != null -> visit(ctx.exprStatement())
            ctx.forStatement() != null -> visit(ctx.forStatement())
            ctx.whileStatement() != null -> visit(ctx.whileStatement())
            ctx.returnStatement() != null -> visit(ctx.returnStatement())
            ctx.breakStatement() != null -> A2sBreak
            ctx.continueStatement() != null -> A2sContinue
            ctx.throwStatement() != null -> visit(ctx.throwStatement())
            ctx.tryStatement() != null -> visit(ctx.tryStatement())
            else -> null
        }
    }

    // ── 未展开的语法规则（visitor 内直接访问子节点，不经过这些 visit） ──

    override fun visitFunParams(ctx: A2sParser.FunParamsContext): Any? = null
    override fun visitEventParams(ctx: A2sParser.EventParamsContext): Any? = null
    override fun visitAssignOp(ctx: A2sParser.AssignOpContext): Any? = null
    override fun visitBreakStatement(ctx: A2sParser.BreakStatementContext): Any? = A2sBreak
    override fun visitContinueStatement(ctx: A2sParser.ContinueStatementContext): Any? = A2sContinue
    override fun visitFinallyBlock(ctx: A2sParser.FinallyBlockContext): Any? = null
    override fun visitPostfixSuffix(ctx: A2sParser.PostfixSuffixContext): Any? = null
    override fun visitCallSuffix(ctx: A2sParser.CallSuffixContext): Any? = null
    override fun visitIndexingSuffix(ctx: A2sParser.IndexingSuffixContext): Any? = null
    override fun visitExpressionList(ctx: A2sParser.ExpressionListContext): Any? = null
    override fun visitStringPart(ctx: A2sParser.StringPartContext): Any? = null
    override fun visitLambdaParams(ctx: A2sParser.LambdaParamsContext): Any? = null

    override fun visitBlock(ctx: A2sParser.BlockContext): List<A2sStmt> {
        return ctx.statement().mapNotNull { visit(it) as A2sStmt? }
    }

    override fun visitAssignment(ctx: A2sParser.AssignmentContext): A2sStmt {
        val target = visit(ctx.expression(0)) as A2sExpr
        val value = visit(ctx.expression(1)) as A2sExpr
        val op = ctx.assignOp().text
        return if (op == "=") {
            A2sAssign(target, value)
        } else {
            // 复合赋值 a op= b 展开为 a = a op b
            val binOp = when (op) {
                "+=" -> A2sBinaryOp.PLUS
                "-=" -> A2sBinaryOp.MINUS
                "*=" -> A2sBinaryOp.MULTIPLY
                "/=" -> A2sBinaryOp.DIVIDE
                "%=" -> A2sBinaryOp.MODULO
                else -> A2sBinaryOp.PLUS
            }
            A2sAssign(target, A2sBinary(target, binOp, value))
        }
    }

    override fun visitPostStatement(ctx: A2sParser.PostStatementContext): A2sPost {
        return A2sPost(
            eventType = ctx.Identifier().text,
            arguments = ctx.callSuffix().expressionList()?.expression()?.map { visit(it) as A2sExpr } ?: emptyList(),
        )
    }

    override fun visitExprStatement(ctx: A2sParser.ExprStatementContext): A2sStmt {
        val expr = visit(ctx.expression()) as A2sExpr
        return when (expr) {
            is A2sIfExpr -> A2sIf(expr.condition, expr.thenBody, expr.elseBody)
            is A2sWhenExpr -> A2sWhen(expr.subject, expr.entries)
            else -> A2sExprStmt(expr)
        }
    }

    override fun visitForStatement(ctx: A2sParser.ForStatementContext): A2sFor {
        return A2sFor(
            variable = ctx.Identifier().text,
            iterable = visit(ctx.expression()) as A2sExpr,
            body = visit(ctx.block()) as List<A2sStmt>,
        )
    }

    override fun visitWhileStatement(ctx: A2sParser.WhileStatementContext): A2sWhile {
        return A2sWhile(
            condition = visit(ctx.expression()) as A2sExpr,
            body = visit(ctx.block()) as List<A2sStmt>,
        )
    }

    override fun visitReturnStatement(ctx: A2sParser.ReturnStatementContext): A2sReturn {
        return A2sReturn(ctx.expression()?.let { visit(it) as A2sExpr })
    }

    override fun visitThrowStatement(ctx: A2sParser.ThrowStatementContext): A2sThrow {
        return A2sThrow(visit(ctx.expression()) as A2sExpr)
    }

    override fun visitTryStatement(ctx: A2sParser.TryStatementContext): A2sTry {
        return A2sTry(
            body = visit(ctx.block()) as List<A2sStmt>,
            catches = ctx.catchBlock().map { visit(it) as A2sCatch },
            finallyBody = ctx.finallyBlock()?.block()?.let { visit(it) as List<A2sStmt> },
        )
    }

    override fun visitCatchBlock(ctx: A2sParser.CatchBlockContext): A2sCatch {
        return A2sCatch(
            paramName = ctx.Identifier().text,
            body = visit(ctx.block()) as List<A2sStmt>,
        )
    }

    override fun visitControlBody(ctx: A2sParser.ControlBodyContext): List<A2sStmt> {
        return if (ctx.block() != null) {
            visit(ctx.block()) as List<A2sStmt>
        } else {
            listOfNotNull(visit(ctx.statement()) as A2sStmt?)
        }
    }

    // ── 类型 ──

    override fun visitType(ctx: A2sParser.TypeContext): A2sType {
        val base = visit(ctx.baseType()) as A2sType
        return if (ctx.QUEST() != null) A2sNullableType(base) else base
    }

    override fun visitBaseType(ctx: A2sParser.BaseTypeContext): A2sType {
        return when {
            ctx.primitiveType() != null -> visit(ctx.primitiveType()) as A2sType
            ctx.BIGINT() != null -> A2sBigInt
            ctx.RATIONAL() != null -> A2sRational
            ctx.STACK() != null -> A2sStack
            ctx.LIST() != null -> A2sListType(visit(ctx.type()) as A2sType)
            ctx.ANY() != null -> A2sAny
            ctx.UNIT() != null -> A2sUnit
            ctx.Identifier() != null -> resolveResourceType(ctx.Identifier().text)
            else -> A2sUnknown
        }
    }

    override fun visitPrimitiveType(ctx: A2sParser.PrimitiveTypeContext): A2sType {
        return when (ctx.text) {
            "i32" -> A2sI32
            "i64" -> A2sI64
            "u32" -> A2sU32
            "u64" -> A2sU64
            "f32" -> A2sF32
            "f64" -> A2sF64
            "Boolean" -> A2sBoolean
            "String" -> A2sString
            else -> A2sUnknown
        }
    }

    private fun resolveResourceType(name: String): A2sType {
        val keyType = resolver?.resolveKeyType(name)
        return if (keyType != null) A2sResourceType(keyType) else A2sUnknown
    }

    // ── 表达式 ──

    override fun visitExpression(ctx: A2sParser.ExpressionContext): A2sExpr = visit(ctx.elvis()) as A2sExpr

    override fun visitElvis(ctx: A2sParser.ElvisContext): A2sExpr {
        val left = visit(ctx.disjunction()) as A2sExpr
        return if (ctx.ELVIS() != null) {
            A2sElvis(left, visit(ctx.elvis()) as A2sExpr)
        } else {
            left
        }
    }

    override fun visitDisjunction(ctx: A2sParser.DisjunctionContext): A2sExpr {
        return if (ctx.disjunction() != null) {
            A2sBinary(visit(ctx.disjunction()) as A2sExpr, A2sBinaryOp.OR, visit(ctx.conjunction()) as A2sExpr)
        } else {
            visit(ctx.conjunction()) as A2sExpr
        }
    }

    override fun visitConjunction(ctx: A2sParser.ConjunctionContext): A2sExpr {
        return if (ctx.conjunction() != null) {
            A2sBinary(visit(ctx.conjunction()) as A2sExpr, A2sBinaryOp.AND, visit(ctx.equality()) as A2sExpr)
        } else {
            visit(ctx.equality()) as A2sExpr
        }
    }

    override fun visitEquality(ctx: A2sParser.EqualityContext): A2sExpr {
        return if (ctx.equality() != null) {
            val op = if (ctx.EQ() != null) A2sBinaryOp.EQUALS else A2sBinaryOp.NOT_EQUALS
            A2sBinary(visit(ctx.equality()) as A2sExpr, op, visit(ctx.comparison()) as A2sExpr)
        } else {
            visit(ctx.comparison()) as A2sExpr
        }
    }

    override fun visitComparison(ctx: A2sParser.ComparisonContext): A2sExpr {
        return if (ctx.comparison() != null) {
            val op = when {
                ctx.LT() != null -> A2sBinaryOp.LESS
                ctx.GT() != null -> A2sBinaryOp.GREATER
                ctx.LE() != null -> A2sBinaryOp.LESS_EQUAL
                else -> A2sBinaryOp.GREATER_EQUAL
            }
            A2sBinary(visit(ctx.comparison()) as A2sExpr, op, visit(ctx.range()) as A2sExpr)
        } else {
            visit(ctx.range()) as A2sExpr
        }
    }

    override fun visitRange(ctx: A2sParser.RangeContext): A2sExpr {
        return if (ctx.range() != null) {
            A2sBinary(visit(ctx.range()) as A2sExpr, A2sBinaryOp.RANGE, visit(ctx.additive()) as A2sExpr)
        } else {
            visit(ctx.additive()) as A2sExpr
        }
    }

    override fun visitAdditive(ctx: A2sParser.AdditiveContext): A2sExpr {
        return if (ctx.additive() != null) {
            val op = if (ctx.PLUS() != null) A2sBinaryOp.PLUS else A2sBinaryOp.MINUS
            A2sBinary(visit(ctx.additive()) as A2sExpr, op, visit(ctx.multiplicative()) as A2sExpr)
        } else {
            visit(ctx.multiplicative()) as A2sExpr
        }
    }

    override fun visitMultiplicative(ctx: A2sParser.MultiplicativeContext): A2sExpr {
        return if (ctx.multiplicative() != null) {
            val op = when {
                ctx.STAR() != null -> A2sBinaryOp.MULTIPLY
                ctx.SLASH() != null -> A2sBinaryOp.DIVIDE
                else -> A2sBinaryOp.MODULO
            }
            A2sBinary(visit(ctx.multiplicative()) as A2sExpr, op, visit(ctx.unary()) as A2sExpr)
        } else {
            visit(ctx.unary()) as A2sExpr
        }
    }

    override fun visitUnary(ctx: A2sParser.UnaryContext): A2sExpr {
        return if (ctx.unary() != null) {
            val op = if (ctx.MINUS() != null) A2sUnaryOp.MINUS else A2sUnaryOp.NOT
            A2sUnary(op, visit(ctx.unary()) as A2sExpr)
        } else {
            visit(ctx.postfix()) as A2sExpr
        }
    }

    override fun visitPostfix(ctx: A2sParser.PostfixContext): A2sExpr {
        var expr = visit(ctx.primary()) as A2sExpr
        for (suffix in ctx.postfixSuffix()) {
            expr = applySuffix(expr, suffix)
        }
        return expr
    }

    private fun applySuffix(receiver: A2sExpr, suffix: A2sParser.PostfixSuffixContext): A2sExpr {
        return when {
            suffix.DOT() != null -> A2sFieldAccess(receiver, suffix.Identifier().text)
            suffix.SAFE_DOT() != null -> A2sFieldAccess(receiver, suffix.Identifier().text, safe = true)
            suffix.NOT_NULL() != null -> A2sNotNull(receiver)
            suffix.callSuffix() != null -> {
                val args = suffix.callSuffix().expressionList()?.expression()?.map { visit(it) as A2sExpr } ?: emptyList()
                when (receiver) {
                    is A2sIdentifier -> A2sCall(receiver.name, args)
                    is A2sFieldAccess -> A2sMethodCall(receiver.receiver, receiver.fieldName, args)
                    else -> A2sMethodCall(receiver, receiverNameFor(receiver), args)
                }
            }

            suffix.indexingSuffix() != null -> A2sIndexAccess(
                receiver, visit(suffix.indexingSuffix().expression()) as A2sExpr
            )

            else -> receiver
        }
    }

    private fun receiverNameFor(receiver: A2sExpr): String = when (receiver) {
        is A2sFieldAccess -> receiver.fieldName
        is A2sIdentifier -> receiver.name
        else -> "call"
    }

    override fun visitPrimary(ctx: A2sParser.PrimaryContext): A2sExpr {
        return when {
            ctx.literal() != null -> visit(ctx.literal()) as A2sExpr
            ctx.Identifier() != null -> A2sIdentifier(ctx.Identifier().text)
            ctx.RESOURCE_REF() != null -> {
                val raw = ctx.RESOURCE_REF().text
                A2sResourceRef(raw.substring(1, raw.length - 1))
            }

            ctx.expression() != null -> visit(ctx.expression()) as A2sExpr
            ctx.lambda() != null -> visit(ctx.lambda()) as A2sExpr
            ctx.ifExpression() != null -> visit(ctx.ifExpression()) as A2sExpr
            ctx.whenExpression() != null -> visit(ctx.whenExpression()) as A2sExpr
            else -> A2sNullLiteral
        }
    }

    override fun visitLiteral(ctx: A2sParser.LiteralContext): A2sExpr {
        return when {
            ctx.IntegerLiteral() != null -> parseIntegerLiteral(ctx.IntegerLiteral().text)
            ctx.RealLiteral() != null -> parseRealLiteral(ctx.RealLiteral().text)
            ctx.TRUE() != null -> A2sBoolLiteral(true)
            ctx.FALSE() != null -> A2sBoolLiteral(false)
            ctx.NULL() != null -> A2sNullLiteral
            ctx.stringLiteral() != null -> visit(ctx.stringLiteral()) as A2sExpr
            else -> A2sNullLiteral
        }
    }

    private fun parseIntegerLiteral(text: String): A2sExpr {
        val suffix = INT_SUFFIX_REGEX.find(text)?.value
        val base = if (suffix != null) text.removeSuffix(suffix) else text
        val digits = base.replace("_", "")
        return when (suffix) {
            "_i32" -> A2sI32Literal(digits.toInt())
            "_i64" -> A2sI64Literal(digits.toLong())
            "_u32" -> A2sI32Literal(digits.toInt())
            "_u64" -> A2sI64Literal(digits.toLong())
            else -> A2sBigIntLiteral(digits)
        }
    }

    private fun parseRealLiteral(text: String): A2sExpr {
        val suffix = REAL_SUFFIX_REGEX.find(text)?.value
        val base = text.substringBefore('_')
        return when (suffix) {
            "_f32" -> A2sF32Literal(base.toFloat())
            "_f64" -> A2sF64Literal(base.toDouble())
            else -> A2sRationalLiteral(base)
        }
    }

    override fun visitStringLiteral(ctx: A2sParser.StringLiteralContext): A2sExpr {
        val parts = ctx.stringPart().map { part ->
            when {
                part.LineStrExprStart() != null -> A2sStrExpr(visit(part.expression()) as A2sExpr)
                part.LineStrRef() != null -> A2sStrExpr(A2sIdentifier(part.LineStrRef().text.substring(1)))
                part.LineStrEscapedDollar() != null -> A2sStrText("$")
                part.LineStrEscapedChar() != null -> A2sStrText(unescape(part.LineStrEscapedChar().text))
                else -> A2sStrText(part.LineStrText()?.text ?: "")
            }
        }
        // 纯文本且无插值 → 直接字符串字面量
        if (parts.all { it is A2sStrText }) {
            return A2sStringLiteral(parts.joinToString("") { (it as A2sStrText).text })
        }
        return A2sStringInterpolation(parts)
    }

    private fun unescape(text: String): String {
        // text 形如 "\n"、"\t" 等
        val ch = text.getOrNull(1) ?: return text
        return when (ch) {
            'n' -> "\n"
            't' -> "\t"
            'r' -> "\r"
            '\\' -> "\\"
            '"' -> "\""
            '$' -> "$"
            else -> ch.toString()
        }
    }

    override fun visitLambda(ctx: A2sParser.LambdaContext): A2sLambda {
        return A2sLambda(
            params = ctx.lambdaParams()?.lambdaParam()?.map { visit(it) as A2sParam } ?: emptyList(),
            body = ctx.statement().mapNotNull { visit(it) as A2sStmt? },
        )
    }

    override fun visitLambdaParam(ctx: A2sParser.LambdaParamContext): A2sParam {
        return A2sParam(
            name = ctx.Identifier().text,
            type = ctx.type()?.let { visit(it) as A2sType } ?: A2sUnknown,
        )
    }

    override fun visitIfExpression(ctx: A2sParser.IfExpressionContext): A2sIfExpr {
        val bodies = ctx.controlBody().map { visit(it) as List<A2sStmt> }
        return A2sIfExpr(
            condition = visit(ctx.expression()) as A2sExpr,
            thenBody = bodies[0],
            elseBody = bodies.getOrNull(1),
        )
    }

    override fun visitWhenExpression(ctx: A2sParser.WhenExpressionContext): A2sWhenExpr {
        return A2sWhenExpr(
            subject = visit(ctx.expression()) as A2sExpr,
            entries = ctx.whenEntry().map { visit(it) as A2sWhenEntry },
        )
    }

    override fun visitWhenEntry(ctx: A2sParser.WhenEntryContext): A2sWhenEntry {
        val isElse = ctx.ELSE() != null
        return A2sWhenEntry(
            conditions = if (isElse) emptyList() else ctx.expression().map { visit(it) as A2sExpr },
            body = visit(ctx.controlBody()) as List<A2sStmt>,
            isElse = isElse,
        )
    }

    // ── 默认：返回 children 聚合（不直接使用） ──

    override fun defaultResult(): Any? = null

    override fun aggregateResult(aggregate: Any?, nextResult: Any?): Any? = nextResult ?: aggregate

    companion object {
        private val INT_SUFFIX_REGEX = Regex("_(i32|i64|u32|u64)$")
        private val REAL_SUFFIX_REGEX = Regex("_(f32|f64)$")
    }
}
