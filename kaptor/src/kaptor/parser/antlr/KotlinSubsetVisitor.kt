package kaptor.parser.antlr

import kaptor.ir.*

class KotlinSubsetVisitor(
    private val declaredEvents: Map<String, Map<String, IrType>> = emptyMap()
) : KotlinParserBaseVisitor<IrNode>() {

    private val variableTypes = mutableMapOf<String, IrType>()
    private var currentEventFields: Map<String, IrType>? = null

    // ── top level ─────────────────────────────────────────────────

    override fun visitKotlinFile(ctx: KotlinParser.KotlinFileContext): IrScriptFile {
        val handlers = mutableListOf<IrHandler>()
        for (topLevelObj in ctx.topLevelObject()) {
            val decl = topLevelObj.declaration() ?: continue
            if (decl.classDeclaration() != null) {
                handlers.addAll(parseClassDeclarationForHandlers(decl.classDeclaration()))
            }
        }
        return IrScriptFile(handlers)
    }

    private fun parseClassDeclarationForHandlers(ctx: KotlinParser.ClassDeclarationContext): List<IrHandler> {
        val handlers = mutableListOf<IrHandler>()
        val classBody = ctx.classBody() ?: return handlers
        for (member in classBody.classMemberDeclarations().classMemberDeclaration()) {
            val init = member.anonymousInitializer() ?: continue
            val block = init.block() ?: continue
            val statements = block.statements()
            if (statements != null) {
                for (stmt in statements.statement()) {
                    val handler = tryParseEventRegistration(stmt)
                    if (handler != null) handlers.add(handler)
                }
            }
        }
        return handlers
    }

    override fun visitScript(ctx: KotlinParser.ScriptContext): IrScriptFile {
        val handlers = mutableListOf<IrHandler>()
        for (stmt in ctx.statement()) {
            val handler = tryParseEventRegistration(stmt)
            if (handler != null) handlers.add(handler)
        }
        return IrScriptFile(handlers)
    }

    // ── event registration ───────────────────────────────────────

    private fun tryParseEventRegistration(stmt: KotlinParser.StatementContext): IrHandler? {
        val expr = stmt.expression() ?: return null
        val postfixExpr = findPostfixUnaryExpression(expr) ?: return null
        val primary = postfixExpr.primaryExpression() ?: return null
        val funcName = primary.simpleIdentifier()?.text ?: return null
        val hookType = when (funcName) {
            "on" -> HookType.ON
            "before" -> HookType.BEFORE
            "after" -> HookType.AFTER
            else -> return null
        }
        val suffixes = postfixExpr.postfixUnarySuffix()
        if (suffixes.isEmpty()) return null
        val cs = suffixes[0].callSuffix() ?: return null
        val valueArgs = cs.valueArguments() ?: return null
        val firstArg = valueArgs.valueArgument().firstOrNull() ?: return null
        val exprText = firstArg.expression()?.text?.trim()
        val eventType = exprText?.removeSurrounding("\"") ?: return null
        if (eventType.isEmpty()) return null
        val annLambda = cs.annotatedLambda() ?: return null
        val lambdaLit = annLambda.lambdaLiteral() ?: return null
        val params = if (lambdaLit.lambdaParameters() != null) {
            lambdaLit.lambdaParameters().lambdaParameter().mapNotNull {
                it.variableDeclaration()?.simpleIdentifier()?.text
            }
        } else emptyList()
        val paramName = params.firstOrNull()

        currentEventFields = declaredEvents[eventType]
        variableTypes.clear()
        if (paramName != null) {
            variableTypes[paramName] = IrObjectType
        }

        val body = lambdaLit.statements()?.let { parseStatements(it) } ?: emptyList()

        return IrHandler(
            eventType = eventType,
            hookType = hookType,
            paramName = paramName,
            body = body,
            costLimit = 1000,
            line = stmt.start.line,
            col = stmt.start.charPositionInLine
        )
    }

    private fun findPostfixUnaryExpression(expr: KotlinParser.ExpressionContext): KotlinParser.PostfixUnaryExpressionContext? {
        val disj = expr.disjunction() ?: return null
        val conj = disj.conjunction(0) ?: return null
        val eq = conj.equality(0) ?: return null
        val cmp = eq.comparison(0) ?: return null
        val gcc = cmp.genericCallLikeComparison(0) ?: return null
        val infixOp = gcc.infixOperation() ?: return null
        val elvis = infixOp.elvisExpression(0) ?: return null
        val infixFn = elvis.infixFunctionCall(0) ?: return null
        val range = infixFn.rangeExpression(0) ?: return null
        val additive = range.additiveExpression(0) ?: return null
        val mult = additive.multiplicativeExpression(0) ?: return null
        val asExpr = mult.asExpression(0) ?: return null
        val prefix = asExpr.prefixUnaryExpression() ?: return null
        return prefix.postfixUnaryExpression()
    }

    // ── statements ───────────────────────────────────────────────

    private fun parseStatements(ctx: KotlinParser.StatementsContext): List<IrInstruction> {
        return ctx.statement().mapNotNull { parseStatement(it) }
    }

    private fun parseStatement(ctx: KotlinParser.StatementContext): IrInstruction? {
        if (ctx.declaration() != null) {
            val decl = ctx.declaration()
            if (decl.propertyDeclaration() != null) return parsePropertyDeclaration(decl.propertyDeclaration())
            if (decl.functionDeclaration() != null) return parseFunctionDeclaration(decl.functionDeclaration())
            return null
        }
        if (ctx.assignment() != null) return parseAssignment(ctx.assignment())
        if (ctx.loopStatement() != null) return parseLoopStatement(ctx.loopStatement())
        if (ctx.expression() != null) {
            val expr = parseExpression(ctx.expression())
            if (expr is IrExpression) return IrExpressionStatement(expr, expr.cost)
        }
        return null
    }

    private fun parsePropertyDeclaration(ctx: KotlinParser.PropertyDeclarationContext): IrInstruction {
        val isVar = ctx.VAR() != null
        val varDecl = ctx.variableDeclaration()
        val name = varDecl?.simpleIdentifier()?.text ?: return IrExpressionStatement(IrNullLiteral(), 1)
        val value = ctx.expression()?.let { parseExpression(it) as? IrExpression }
        val resolved = value ?: IrNullLiteral()
        variableTypes[name] = inferType(resolved)
        return if (isVar) {
            IrVarDecl(name, resolved, 1)
        } else {
            IrValDecl(name, resolved, 1)
        }
    }

    private fun parseFunctionDeclaration(ctx: KotlinParser.FunctionDeclarationContext): IrInstruction {
        return IrExpressionStatement(IrNullLiteral(), 1)
    }

    private fun parseAssignment(ctx: KotlinParser.AssignmentContext): IrAssignment {
        val target = when {
            ctx.directlyAssignableExpression() != null -> parseDirectlyAssignableExpression(ctx.directlyAssignableExpression())
            ctx.assignableExpression() != null -> parseAssignableExpression(ctx.assignableExpression())
            else -> IrNullLiteral()
        }
        val value = parseExpression(ctx.expression())
        val valueExpr = value as IrExpression
        if (target is IrIdentifier) {
            variableTypes[target.name] = inferType(valueExpr)
        }
        return IrAssignment(target as IrExpression, valueExpr, 1)
    }

    private fun parseDirectlyAssignableExpression(ctx: KotlinParser.DirectlyAssignableExpressionContext): IrNode {
        return when {
            ctx.simpleIdentifier() != null -> {
                val name = ctx.simpleIdentifier().text
                IrIdentifier(name, variableTypes[name] ?: IrObjectType)
            }

            ctx.postfixUnaryExpression() != null -> parsePostfixUnaryExpression(ctx.postfixUnaryExpression())
            else -> IrNullLiteral()
        }
    }

    private fun parseAssignableExpression(ctx: KotlinParser.AssignableExpressionContext): IrNode {
        return when {
            ctx.prefixUnaryExpression() != null -> parsePrefixUnaryExpression(ctx.prefixUnaryExpression())
            else -> IrNullLiteral()
        }
    }

    private fun parseLoopStatement(ctx: KotlinParser.LoopStatementContext): IrInstruction {
        return when {
            ctx.forStatement() != null -> parseForStatement(ctx.forStatement())
            ctx.whileStatement() != null -> parseWhileStatement(ctx.whileStatement())
            ctx.doWhileStatement() != null -> {
                val dw = ctx.doWhileStatement()
                val body = dw.controlStructureBody()?.let { parseControlStructureBody(it) } ?: emptyList()
                val cond = parseExpression(dw.expression()) as IrExpression
                IrWhileStatement(cond, body, 5)
            }

            else -> IrExpressionStatement(IrNullLiteral(), 1)
        }
    }

    private fun parseForStatement(ctx: KotlinParser.ForStatementContext): IrForStatement {
        val variable = ctx.variableDeclaration().simpleIdentifier().text
        val iterable = parseExpression(ctx.expression()) as IrExpression
        val body = ctx.controlStructureBody()?.let { parseControlStructureBody(it) } ?: emptyList()
        variableTypes[variable] = IrObjectType
        return IrForStatement(variable, iterable, body, 5)
    }

    private fun parseWhileStatement(ctx: KotlinParser.WhileStatementContext): IrWhileStatement {
        val condition = parseExpression(ctx.expression()) as IrExpression
        val body = ctx.controlStructureBody()?.let { parseControlStructureBody(it) } ?: emptyList()
        return IrWhileStatement(condition, body, 5)
    }

    override fun visitIfExpression(ctx: KotlinParser.IfExpressionContext): IrNode {
        val condition = parseExpression(ctx.expression()) as IrExpression
        val thenBlock = ctx.controlStructureBody(0)?.let { parseControlStructureBody(it) } ?: emptyList()
        val elseBlock = if (ctx.controlStructureBody().size > 1) {
            parseControlStructureBody(ctx.controlStructureBody(1))
        } else null
        return IrIfStatement(
            condition,
            thenBlock,
            elseBlock,
            3 + thenBlock.sumOf { it.cost } + (elseBlock?.sumOf { it.cost } ?: 0))
    }

    private fun parseControlStructureBody(ctx: KotlinParser.ControlStructureBodyContext): List<IrInstruction> {
        return if (ctx.block() != null) {
            parseStatements(ctx.block().statements())
        } else if (ctx.statement() != null) {
            listOfNotNull(parseStatement(ctx.statement()))
        } else emptyList()
    }

    override fun visitWhenExpression(ctx: KotlinParser.WhenExpressionContext): IrNode {
        val branches = ctx.whenEntry().mapNotNull { parseWhenEntry(it) }
        if (branches.isEmpty()) return IrExpressionStatement(IrNullLiteral(), 1)
        var result: IrInstruction = branches.last()
        for (i in branches.size - 2 downTo 0) {
            val ifStmt = branches[i] as IrIfStatement
            result = IrIfStatement(
                ifStmt.condition,
                ifStmt.thenBranch,
                listOf(result),
                3 + ifStmt.thenBranch.sumOf { it.cost } + result.cost)
        }
        return result
    }

    private fun parseWhenEntry(ctx: KotlinParser.WhenEntryContext): IrInstruction? {
        val body = if (ctx.controlStructureBody() != null) {
            parseControlStructureBody(ctx.controlStructureBody())
        } else emptyList()
        val conditions = ctx.whenCondition()?.map { parseWhenCondition(it) } ?: return null
        val condExpr = if (conditions.size == 1) conditions[0]
        else conditions.reduce { a, b -> IrBinaryExpression(a, BinaryOperator.OR, b, IrBoolType, 1) }
        return IrIfStatement(condExpr, body, null, 3 + body.sumOf { it.cost })
    }

    private fun parseWhenCondition(ctx: KotlinParser.WhenConditionContext): IrExpression {
        return when {
            ctx.expression() != null -> parseExpression(ctx.expression()) as IrExpression
            ctx.typeTest() != null -> {
                val tt = ctx.typeTest()
                val isNegated = tt.isOperator().NOT_IS() != null
                val typeName = parseType(tt.type())
                val call = IrFunctionCall("_isType", listOf(IrIdentifier("it"), IrStringLiteral(typeName)))
                if (isNegated) IrUnaryExpression(UnaryOperator.NOT, call, IrBoolType, 1) else call
            }

            ctx.rangeTest() != null -> {
                val rt = ctx.rangeTest()
                val isNegated = rt.inOperator().NOT_IN() != null
                val expr = parseExpression(rt.expression()) as IrExpression
                val call = IrFunctionCall("_contains", listOf(IrIdentifier("it"), expr))
                if (isNegated) IrUnaryExpression(UnaryOperator.NOT, call, IrBoolType, 1) else call
            }

            else -> IrBoolLiteral(true)
        }
    }

    private fun parseJumpExpression(ctx: KotlinParser.JumpExpressionContext): IrInstruction? {
        return when {
            ctx.RETURN() != null || ctx.RETURN_AT() != null -> {
                val value = ctx.expression()?.let { parseExpression(it) as? IrExpression }
                IrReturnStatement(value, 2)
            }

            ctx.BREAK() != null || ctx.BREAK_AT() != null -> null
            ctx.CONTINUE() != null || ctx.CONTINUE_AT() != null -> null
            ctx.THROW() != null -> null
            else -> null
        }
    }

    // ── expressions ──────────────────────────────────────────────

    private fun parseExpression(ctx: KotlinParser.ExpressionContext): IrNode {
        return parseDisjunction(ctx.disjunction())
    }

    private fun parseDisjunction(ctx: KotlinParser.DisjunctionContext): IrNode {
        var left = parseConjunction(ctx.conjunction(0))
        for (i in 1 until ctx.conjunction().size) {
            val right = parseConjunction(ctx.conjunction(i))
            left = IrBinaryExpression(left as IrExpression, BinaryOperator.OR, right as IrExpression, IrBoolType, 1)
        }
        return left
    }

    private fun parseConjunction(ctx: KotlinParser.ConjunctionContext): IrNode {
        var left = parseEquality(ctx.equality(0))
        for (i in 1 until ctx.equality().size) {
            val right = parseEquality(ctx.equality(i))
            left = IrBinaryExpression(left as IrExpression, BinaryOperator.AND, right as IrExpression, IrBoolType, 1)
        }
        return left
    }

    private fun parseEquality(ctx: KotlinParser.EqualityContext): IrNode {
        var left = parseComparison(ctx.comparison(0))
        var i = 1
        for (op in ctx.equalityOperator()) {
            val right = parseComparison(ctx.comparison(i))
            val lt = inferType(left as IrExpression)
            val rt = inferType(right as IrExpression)
            val binOp = when {
                op.EQEQ() != null -> BinaryOperator.EQUALS
                op.EXCL_EQEQ() != null || op.EXCL_EQ() != null -> BinaryOperator.NOT_EQUALS
                else -> BinaryOperator.EQUALS
            }
            val resultType = resolveEqualityType(lt, rt)
            left = IrBinaryExpression(left as IrExpression, binOp, right as IrExpression, resultType, 1)
            i++
        }
        return left
    }

    private fun parseComparison(ctx: KotlinParser.ComparisonContext): IrNode {
        var left = parseGenericCallLikeComparison(ctx.genericCallLikeComparison(0))
        var i = 1
        for (op in ctx.comparisonOperator()) {
            val right = parseGenericCallLikeComparison(ctx.genericCallLikeComparison(i))
            val lt = inferType(left as IrExpression)
            val rt = inferType(right as IrExpression)
            val binOp = when {
                op.LANGLE() != null -> BinaryOperator.LESS
                op.RANGLE() != null -> BinaryOperator.GREATER
                op.LE() != null -> BinaryOperator.LESS_EQUAL
                op.GE() != null -> BinaryOperator.GREATER_EQUAL
                else -> BinaryOperator.LESS
            }
            left = IrBinaryExpression(left as IrExpression, binOp, right as IrExpression, IrBoolType, 1)
            i++
        }
        return left
    }

    private fun parseGenericCallLikeComparison(ctx: KotlinParser.GenericCallLikeComparisonContext): IrNode {
        var expr = parseInfixOperation(ctx.infixOperation())
        for (cs in ctx.callSuffix()) {
            val args = parseCallSuffix(cs)
            expr = when (expr) {
                is IrIdentifier -> IrFunctionCall(expr.name, args)
                is IrFieldAccess -> IrMethodCall(expr.receiver, expr.fieldName, args)
                else -> IrMethodCall(expr as IrExpression, "_call", args)
            }
        }
        return expr
    }

    private fun parseCallSuffix(ctx: KotlinParser.CallSuffixContext): List<IrExpression> {
        val valueArgs = ctx.valueArguments()
        return if (valueArgs != null) {
            valueArgs.valueArgument().mapNotNull { arg ->
                parseExpression(arg.expression()) as? IrExpression
            }
        } else emptyList()
    }

    private fun parseInfixOperation(ctx: KotlinParser.InfixOperationContext): IrNode {
        var left = parseElvisExpression(ctx.elvisExpression(0))
        if (ctx.inOperator().isNotEmpty() && ctx.elvisExpression().size > 1) {
            val right = parseElvisExpression(ctx.elvisExpression(1))
            left = IrFunctionCall("_contains", listOf(right as IrExpression, left as IrExpression))
        } else if (!ctx.isOperator().isEmpty() && ctx.type().isNotEmpty()) {
            val typeName = parseType(ctx.type(0))
            left = IrFunctionCall("_isType", listOf(left as IrExpression, IrStringLiteral(typeName)))
        }
        return left
    }

    private fun parseElvisExpression(ctx: KotlinParser.ElvisExpressionContext): IrNode {
        var left = parseInfixFunctionCall(ctx.infixFunctionCall(0))
        for (i in 1 until ctx.infixFunctionCall().size) {
            val right = parseInfixFunctionCall(ctx.infixFunctionCall(i))
            left = IrFunctionCall("_elvis", listOf(left as IrExpression, right as IrExpression))
        }
        return left
    }

    private fun parseInfixFunctionCall(ctx: KotlinParser.InfixFunctionCallContext): IrNode {
        var left = parseRangeExpression(ctx.rangeExpression(0))
        for (i in 0 until ctx.simpleIdentifier().size) {
            val name = ctx.simpleIdentifier(i).text
            val right = parseRangeExpression(ctx.rangeExpression(i + 1))
            left = IrMethodCall(left as IrExpression, name, listOf(right as IrExpression))
        }
        return left
    }

    private fun parseRangeExpression(ctx: KotlinParser.RangeExpressionContext): IrNode {
        var left = parseAdditiveExpression(ctx.additiveExpression(0))
        for (i in 1 until ctx.additiveExpression().size) {
            val right = parseAdditiveExpression(ctx.additiveExpression(i))
            left = IrFunctionCall("_rangeTo", listOf(left as IrExpression, right as IrExpression))
        }
        return left
    }

    private fun parseAdditiveExpression(ctx: KotlinParser.AdditiveExpressionContext): IrNode {
        var left = parseMultiplicativeExpression(ctx.multiplicativeExpression(0))
        var i = 1
        for (op in ctx.additiveOperator()) {
            val right = parseMultiplicativeExpression(ctx.multiplicativeExpression(i))
            val lt = inferType(left as IrExpression)
            val rt = inferType(right as IrExpression)
            val binOp = if (op.ADD() != null) BinaryOperator.PLUS else BinaryOperator.MINUS
            val resultType = resolveBinaryOpType(lt, rt, binOp)
            left = IrBinaryExpression(left as IrExpression, binOp, right as IrExpression, resultType, 1)
            i++
        }
        return left
    }

    private fun parseMultiplicativeExpression(ctx: KotlinParser.MultiplicativeExpressionContext): IrNode {
        var left = parseAsExpression(ctx.asExpression(0))
        var i = 1
        for (op in ctx.multiplicativeOperator()) {
            val right = parseAsExpression(ctx.asExpression(i))
            val lt = inferType(left as IrExpression)
            val rt = inferType(right as IrExpression)
            val binOp = when {
                op.MULT() != null -> BinaryOperator.MULTIPLY
                op.DIV() != null -> BinaryOperator.DIVIDE
                op.MOD() != null -> BinaryOperator.MODULO
                else -> BinaryOperator.MULTIPLY
            }
            val resultType = resolveBinaryOpType(lt, rt, binOp)
            left = IrBinaryExpression(left as IrExpression, binOp, right as IrExpression, resultType, 1)
            i++
        }
        return left
    }

    private fun parseAsExpression(ctx: KotlinParser.AsExpressionContext): IrNode {
        var expr = parsePrefixUnaryExpression(ctx.prefixUnaryExpression())
        if (ctx.asOperator().isNotEmpty() && ctx.type().isNotEmpty()) {
            expr = IrFunctionCall("_as", listOf(expr as IrExpression, IrStringLiteral(parseType(ctx.type(0)))))
        }
        return expr
    }

    private fun parsePrefixUnaryExpression(ctx: KotlinParser.PrefixUnaryExpressionContext): IrNode {
        var expr = parsePostfixUnaryExpression(ctx.postfixUnaryExpression())
        for (prefix in ctx.unaryPrefix()) {
            val opCtx = prefix.prefixUnaryOperator()
            if (opCtx == null) continue
            val unaryOp = when {
                opCtx.SUB() != null -> UnaryOperator.MINUS
                opCtx.excl() != null -> UnaryOperator.NOT
                else -> null
            }
            if (unaryOp != null) {
                val operandType = inferType(expr as IrExpression)
                val resultType = resolveUnaryOpType(operandType, unaryOp)
                expr = IrUnaryExpression(unaryOp, expr, resultType, 1)
            }
        }
        return expr
    }

    private fun parsePostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext): IrNode {
        var expr = parsePrimaryExpression(ctx.primaryExpression())

        for (suffix in ctx.postfixUnarySuffix()) {
            expr = when {
                suffix.callSuffix() != null -> {
                    val args = parseCallSuffix(suffix.callSuffix())
                    when (expr) {
                        is IrIdentifier -> IrFunctionCall(expr.name, args)
                        is IrFieldAccess -> IrMethodCall(expr.receiver, expr.fieldName, args)
                        else -> IrMethodCall(expr as IrExpression, "_call", args)
                    }
                }

                suffix.indexingSuffix() != null -> {
                    val index = parseExpression(suffix.indexingSuffix().expression(0)) as IrExpression
                    IrIndexAccess(expr as IrExpression, index, 2)
                }

                suffix.navigationSuffix() != null -> {
                    val nav = suffix.navigationSuffix()
                    val member = when {
                        nav.simpleIdentifier() != null -> IrIdentifier(nav.simpleIdentifier().text)
                        nav.parenthesizedExpression() != null -> parseExpression(
                            nav.parenthesizedExpression().expression()
                        )

                        else -> null
                    }
                    if (member != null) {
                        when (member) {
                            is IrIdentifier -> {
                                val fieldType = currentEventFields?.get(member.name) ?: IrObjectType
                                IrFieldAccess(expr as IrExpression, member.name, fieldType, 2)
                            }

                            else -> IrMethodCall(expr as IrExpression, "_member", listOf(member as IrExpression))
                        }
                    } else expr
                }

                suffix.postfixUnaryOperator() != null -> {
                    val op = suffix.postfixUnaryOperator()
                    when {
                        op.INCR() != null -> {
                            val operandType = inferType(expr as IrExpression)
                            val one = literalForType(operandType)
                            IrAssignment(expr, IrBinaryExpression(expr, BinaryOperator.PLUS, one, operandType, 1), 1)
                        }

                        op.DECR() != null -> {
                            val operandType = inferType(expr as IrExpression)
                            val one = literalForType(operandType)
                            IrAssignment(expr, IrBinaryExpression(expr, BinaryOperator.MINUS, one, operandType, 1), 1)
                        }

                        op.excl() != null -> IrFunctionCall("_assertNotNull", listOf(expr as IrExpression))
                        else -> expr
                    }
                }

                else -> expr
            }
        }
        return expr
    }

    private fun literalForType(type: IrType): IrExpression = when (type) {
        IrIntType -> IrIntLiteral(1)
        IrLongType -> IrLongLiteral(1L)
        IrFloatType -> IrFloatLiteral(1.0)
        IrDoubleType -> IrFloatLiteral(1.0)
        else -> IrIntLiteral(1)
    }

    private fun parsePrimaryExpression(ctx: KotlinParser.PrimaryExpressionContext): IrNode {
        return when {
            ctx.parenthesizedExpression() != null -> parseExpression(ctx.parenthesizedExpression().expression())
            ctx.simpleIdentifier() != null -> {
                val name = ctx.simpleIdentifier().text
                IrIdentifier(name, variableTypes[name] ?: IrObjectType)
            }

            ctx.literalConstant() != null -> parseLiteralConstant(ctx.literalConstant())
            ctx.stringLiteral() != null -> parseStringLiteral(ctx.stringLiteral())
            ctx.thisExpression() != null -> {
                if (ctx.thisExpression().THIS_AT() != null) IrIdentifier(
                    "this@" + ctx.thisExpression().THIS_AT().text.removePrefix("this@")
                )
                else IrIdentifier("this")
            }

            ctx.ifExpression() != null -> visitIfExpression(ctx.ifExpression())
            ctx.whenExpression() != null -> visitWhenExpression(ctx.whenExpression())
            ctx.functionLiteral() != null -> {
                val lit = ctx.functionLiteral()
                if (lit.lambdaLiteral() != null) {
                    val ll = lit.lambdaLiteral()
                    val params = if (ll.lambdaParameters() != null) ll.lambdaParameters().lambdaParameter()
                        .map { it.variableDeclaration().simpleIdentifier().text }
                    else emptyList()
                    IrFunctionCall("_lambda", listOf(IrStringLiteral(params.joinToString(","))))
                } else {
                    IrFunctionCall("_lambda", listOf(IrStringLiteral("")))
                }
            }

            ctx.jumpExpression() != null -> {
                parseJumpExpression(ctx.jumpExpression()) ?: IrNullLiteral()
            }

            ctx.collectionLiteral() != null -> {
                val exprs = ctx.collectionLiteral().expression().map { parseExpression(it) as IrExpression }
                IrFunctionCall("listOf", exprs)
            }

            else -> IrNullLiteral()
        }
    }

    private fun parseLiteralConstant(ctx: KotlinParser.LiteralConstantContext): IrNode {
        return when {
            ctx.IntegerLiteral() != null -> IrIntLiteral(ctx.IntegerLiteral().text.toInt())
            ctx.LongLiteral() != null -> IrLongLiteral(ctx.LongLiteral().text.dropLast(1).toLong())
            ctx.HexLiteral() != null -> {
                val v = ctx.HexLiteral().text.removePrefix("0x").removePrefix("0X").toLong(16)
                if (v in Int.MIN_VALUE..Int.MAX_VALUE) IrIntLiteral(v.toInt())
                else IrLongLiteral(v)
            }

            ctx.BinLiteral() != null -> {
                val v = ctx.BinLiteral().text.removePrefix("0b").removePrefix("0B").toLong(2)
                if (v in Int.MIN_VALUE..Int.MAX_VALUE) IrIntLiteral(v.toInt())
                else IrLongLiteral(v)
            }

            ctx.UnsignedLiteral() != null -> IrLongLiteral(ctx.UnsignedLiteral().text.dropLast(1).toLong())
            ctx.RealLiteral() != null -> {
                val text = ctx.RealLiteral().text
                if (text.endsWith("f") || text.endsWith("F")) IrFloatLiteral(text.dropLast(1).toDouble(), IrFloatType)
                else IrFloatLiteral(text.toDouble(), IrDoubleType)
            }

            ctx.BooleanLiteral() != null -> IrBoolLiteral(ctx.BooleanLiteral().text == "true")
            ctx.CharacterLiteral() != null -> IrIntLiteral(ctx.CharacterLiteral().text[1].code)
            ctx.NullLiteral() != null -> IrNullLiteral()
            else -> IrNullLiteral()
        }
    }

    private fun parseStringLiteral(ctx: KotlinParser.StringLiteralContext): IrNode {
        return when {
            ctx.lineStringLiteral() != null -> parseLineStringLiteral(ctx.lineStringLiteral())
            ctx.multiLineStringLiteral() != null -> parseMultiLineStringLiteral(ctx.multiLineStringLiteral())
            else -> IrStringLiteral("")
        }
    }

    private fun parseLineStringLiteral(ctx: KotlinParser.LineStringLiteralContext): IrNode {
        val parts = mutableListOf<IrInterpolationPart>()
        for (content in ctx.lineStringContent()) {
            when {
                content.LineStrText() != null -> parts.add(IrLiteralPart(content.LineStrText().text))
                content.LineStrEscapedChar() != null -> parts.add(IrLiteralPart(unescape(content.LineStrEscapedChar().text)))
                content.LineStrRef() != null -> {
                    val name = content.LineStrRef().text.removePrefix("$")
                    parts.add(IrExpressionPart(IrIdentifier(name)))
                }
            }
        }
        for (expr in ctx.lineStringExpression()) {
            val e = parseExpression(expr.expression())
            if (e is IrExpression) parts.add(IrExpressionPart(e))
        }
        if (parts.size == 1 && parts[0] is IrLiteralPart) {
            return IrStringLiteral((parts[0] as IrLiteralPart).text)
        }
        return IrStringInterpolation(parts)
    }

    private fun parseMultiLineStringLiteral(ctx: KotlinParser.MultiLineStringLiteralContext): IrNode {
        val parts = mutableListOf<IrInterpolationPart>()
        for (content in ctx.multiLineStringContent()) {
            when {
                content.MultiLineStrText() != null -> parts.add(IrLiteralPart(content.MultiLineStrText().text))
                content.MultiLineStringQuote() != null -> parts.add(IrLiteralPart("\""))
                content.MultiLineStrRef() != null -> {
                    val name = content.MultiLineStrRef().text.removePrefix("$")
                    parts.add(IrExpressionPart(IrIdentifier(name)))
                }
            }
        }
        for (expr in ctx.multiLineStringExpression()) {
            val e = parseExpression(expr.expression())
            if (e is IrExpression) parts.add(IrExpressionPart(e))
        }
        if (parts.size == 1 && parts[0] is IrLiteralPart) {
            return IrStringLiteral((parts[0] as IrLiteralPart).text)
        }
        return IrStringInterpolation(parts)
    }

    // ── type helpers ─────────────────────────────────────────────

    private fun inferType(expr: IrExpression): IrType {
        return when (expr) {
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
    }

    private fun resolveBinaryOpType(left: IrType, right: IrType, op: BinaryOperator): IrType {
        if (left == IrStringType || right == IrStringType) {
            return if (op == BinaryOperator.PLUS) IrStringType else IrBoolType
        }
        if (op == BinaryOperator.PLUS || op == BinaryOperator.MINUS || op == BinaryOperator.MULTIPLY || op == BinaryOperator.DIVIDE || op == BinaryOperator.MODULO) {
            return promoteType(left, right)
        }
        if (op == BinaryOperator.EQUALS || op == BinaryOperator.NOT_EQUALS || op == BinaryOperator.LESS || op == BinaryOperator.LESS_EQUAL || op == BinaryOperator.GREATER || op == BinaryOperator.GREATER_EQUAL) {
            return IrBoolType
        }
        if (op == BinaryOperator.AND || op == BinaryOperator.OR) {
            return IrBoolType
        }
        return promoteType(left, right)
    }

    private fun resolveEqualityType(left: IrType, right: IrType): IrType {
        if (left == IrStringType || right == IrStringType) return IrBoolType
        return IrBoolType
    }

    private fun resolveUnaryOpType(operand: IrType, op: UnaryOperator): IrType {
        return when (op) {
            UnaryOperator.MINUS -> operand
            UnaryOperator.NOT -> IrBoolType
            UnaryOperator.BIT_NOT -> operand
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

    private fun parseType(ctx: KotlinParser.TypeContext): String {
        val ref = ctx.typeReference()
        return if (ref != null) {
            when {
                ref.userType() != null -> ref.userType().text
                ref.DYNAMIC() != null -> "dynamic"
                else -> ref.text
            }
        } else "Any"
    }

    private fun unescape(text: String): String {
        if (!text.startsWith("\\")) return text
        return when (text) {
            "\\n" -> "\n"
            "\\t" -> "\t"
            "\\r" -> "\r"
            "\\\\" -> "\\"
            "\\\"" -> "\""
            "\\'" -> "'"
            "\\$" -> "$"
            else -> text
        }
    }

    override fun visit(tree: org.antlr.v4.runtime.tree.ParseTree?): IrNode? {
        return super.visit(tree)
    }
}
