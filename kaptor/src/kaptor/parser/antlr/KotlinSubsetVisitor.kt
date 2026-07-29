package kaptor.parser.antlr

import kaptor.ast.*
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

class KotlinSubsetVisitor : KotlinParserBaseVisitor<AstNode>() {

    override fun visitKotlinFile(ctx: KotlinParser.KotlinFileContext): ScriptFile {
        val imports = ctx.importList().importHeader().map { parseImportHeader(it) }
        val topLevelDecls = ctx.topLevelObject().mapNotNull { parseTopLevelObject(it) }

        return ScriptFile(
            imports = imports,
            handlers = topLevelDecls.filterIsInstance<EventHandler>(),
            line = ctx.start.line,
            col = ctx.start.charPositionInLine
        )
    }

    private fun parseTopLevelObject(ctx: KotlinParser.TopLevelObjectContext): AstNode? {
        val decl = ctx.declaration()
        return when {
            decl?.propertyDeclaration() != null -> parsePropertyDeclaration(decl.propertyDeclaration())
            decl?.functionDeclaration() != null -> parseFunctionDeclaration(decl.functionDeclaration())
            else -> null
        }
    }

    private fun parseImportHeader(ctx: KotlinParser.ImportHeaderContext): ImportDecl {
        return ImportDecl(ctx.identifier().text, ctx.start.line, ctx.start.charPositionInLine)
    }

    private fun parseStatements(ctx: KotlinParser.StatementsContext): List<Statement> {
        return ctx.statement().mapNotNull { parseStatement(it) }
    }

    private fun parseStatement(ctx: KotlinParser.StatementContext): Statement? {
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
            if (expr is Expression) return ExpressionStatement(expr, ctx.start.line, ctx.start.charPositionInLine)
        }
        return null
    }

    private fun parsePropertyDeclaration(ctx: KotlinParser.PropertyDeclarationContext): Statement {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val isVar = ctx.VAR() != null
        val varDecl = ctx.variableDeclaration()
        val name = varDecl?.simpleIdentifier()?.text ?: return ExpressionStatement(NullLiteral(line, col), line, col)
        val type = varDecl?.type()?.let { parseType(it) }
        val value = ctx.expression()?.let { parseExpression(it) as? Expression }

        return if (isVar) {
            VarDecl(name, type, value, line, col)
        } else {
            ValDecl(name, type, value ?: NullLiteral(line, col), line, col)
        }
    }

    private fun parseFunctionDeclaration(ctx: KotlinParser.FunctionDeclarationContext): Statement {
        return ExpressionStatement(NullLiteral(ctx.start.line, ctx.start.charPositionInLine), ctx.start.line, ctx.start.charPositionInLine)
    }

    private fun parseAssignment(ctx: KotlinParser.AssignmentContext): Assignment {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val target = when {
            ctx.directlyAssignableExpression() != null -> parseDirectlyAssignableExpression(ctx.directlyAssignableExpression())
            ctx.assignableExpression() != null -> parseAssignableExpression(ctx.assignableExpression())
            else -> NullLiteral(line, col)
        }
        val value = parseExpression(ctx.expression())
        return Assignment(target as Expression, value as Expression, line, col)
    }

    private fun parseDirectlyAssignableExpression(ctx: KotlinParser.DirectlyAssignableExpressionContext): AstNode {
        return when {
            ctx.simpleIdentifier() != null -> Identifier(ctx.simpleIdentifier().text, ctx.start.line, ctx.start.charPositionInLine)
            ctx.postfixUnaryExpression() != null -> parsePostfixUnaryExpression(ctx.postfixUnaryExpression())
            else -> NullLiteral(ctx.start.line, ctx.start.charPositionInLine)
        }
    }

    private fun parseAssignableExpression(ctx: KotlinParser.AssignableExpressionContext): AstNode {
        return when {
            ctx.prefixUnaryExpression() != null -> parsePrefixUnaryExpression(ctx.prefixUnaryExpression())
            else -> NullLiteral(ctx.start.line, ctx.start.charPositionInLine)
        }
    }

    private fun parseLoopStatement(ctx: KotlinParser.LoopStatementContext): Statement {
        return when {
            ctx.forStatement() != null -> parseForStatement(ctx.forStatement())
            ctx.whileStatement() != null -> parseWhileStatement(ctx.whileStatement())
            ctx.doWhileStatement() != null -> {
                val dw = ctx.doWhileStatement()
                val body = dw.controlStructureBody()?.let { parseControlStructureBody(it) } ?: emptyList()
                val cond = parseExpression(dw.expression()) as Expression
                val line = dw.start.line
                val col = dw.start.charPositionInLine
                val stmts = mutableListOf<Statement>()
                stmts.addAll(body)
                stmts.add(WhileStatement(cond, body, line, col))
                ExpressionStatement(FunctionCall("_block", stmts.map { NullLiteral(it.line, it.col) }, line, col), line, col)
            }
            else -> ExpressionStatement(NullLiteral(ctx.start.line, ctx.start.charPositionInLine), ctx.start.line, ctx.start.charPositionInLine)
        }
    }

    private fun parseForStatement(ctx: KotlinParser.ForStatementContext): ForStatement {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val variable = ctx.variableDeclaration().simpleIdentifier().text
        val iterable = parseExpression(ctx.expression()) as Expression
        val body = ctx.controlStructureBody()?.let { parseControlStructureBody(it) } ?: emptyList()
        return ForStatement(variable, iterable, body, line, col)
    }

    private fun parseWhileStatement(ctx: KotlinParser.WhileStatementContext): WhileStatement {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val condition = parseExpression(ctx.expression()) as Expression
        val body = ctx.controlStructureBody()?.let { parseControlStructureBody(it) } ?: emptyList()
        return WhileStatement(condition, body, line, col)
    }

    override fun visitIfExpression(ctx: KotlinParser.IfExpressionContext): AstNode {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val condition = parseExpression(ctx.expression()) as Expression
        val thenBlock = ctx.controlStructureBody(0)?.let { parseControlStructureBody(it) } ?: emptyList()
        val elseBlock = if (ctx.controlStructureBody().size > 1) {
            parseControlStructureBody(ctx.controlStructureBody(1))
        } else null
        return IfStatement(condition, thenBlock, elseBlock, line, col)
    }

    private fun parseControlStructureBody(ctx: KotlinParser.ControlStructureBodyContext): List<Statement> {
        return if (ctx.block() != null) {
            parseStatements(ctx.block().statements())
        } else if (ctx.statement() != null) {
            listOfNotNull(parseStatement(ctx.statement()))
        } else emptyList()
    }

    override fun visitWhenExpression(ctx: KotlinParser.WhenExpressionContext): AstNode {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val branches = ctx.whenEntry().mapNotNull { parseWhenEntry(it) }
        if (branches.isEmpty()) return ExpressionStatement(NullLiteral(line, col), line, col)
        var result: Statement = branches.last()
        for (i in branches.size - 2 downTo 0) {
            val ifStmt = branches[i] as IfStatement
            result = IfStatement(ifStmt.condition, ifStmt.thenBranch, listOf(result), ifStmt.line, ifStmt.col)
        }
        return result
    }

    private fun parseWhenEntry(ctx: KotlinParser.WhenEntryContext): Statement? {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val body = if (ctx.controlStructureBody() != null) {
            parseControlStructureBody(ctx.controlStructureBody())
        } else emptyList()
        val conditions = ctx.whenCondition()?.map { parseWhenCondition(it) } ?: return null
        val condExpr = if (conditions.size == 1) conditions[0]
        else conditions.reduce { a, b -> BinaryExpression(a, BinaryOperator.OR, b, a.line, a.col) }
        return IfStatement(condExpr, body, null, line, col)
    }

    private fun parseWhenCondition(ctx: KotlinParser.WhenConditionContext): Expression {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        return when {
            ctx.expression() != null -> parseExpression(ctx.expression()) as Expression
            ctx.typeTest() != null -> {
                val tt = ctx.typeTest()
                val isNegated = tt.isOperator().NOT_IS() != null
                val typeName = parseType(tt.type())
                val call = FunctionCall("_isType", listOf(
                    Identifier("it", line, col), StringLiteral(typeName, line, col)
                ), line, col)
                if (isNegated) UnaryExpression(UnaryOperator.NOT, call, line, col) else call
            }
            ctx.rangeTest() != null -> {
                val rt = ctx.rangeTest()
                val isNegated = rt.inOperator().NOT_IN() != null
                val expr = parseExpression(rt.expression()) as Expression
                val call = FunctionCall("_contains", listOf(
                    Identifier("it", line, col), expr
                ), line, col)
                if (isNegated) UnaryExpression(UnaryOperator.NOT, call, line, col) else call
            }
            else -> BoolLiteral(true, line, col)
        }
    }

    private fun parseJumpExpression(ctx: KotlinParser.JumpExpressionContext): Statement? {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        return when {
            ctx.RETURN() != null || ctx.RETURN_AT() != null -> {
                val value = ctx.expression()?.let { parseExpression(it) as? Expression }
                ReturnStatement(value, line, col)
            }
            ctx.BREAK() != null || ctx.BREAK_AT() != null -> null
            ctx.CONTINUE() != null || ctx.CONTINUE_AT() != null -> null
            ctx.THROW() != null -> null
            else -> null
        }
    }

    // ---------- expression hierarchy ----------

    private fun parseExpression(ctx: KotlinParser.ExpressionContext): AstNode {
        return parseDisjunction(ctx.disjunction())
    }

    private fun parseDisjunction(ctx: KotlinParser.DisjunctionContext): AstNode {
        var left = parseConjunction(ctx.conjunction(0))
        for (i in 1 until ctx.conjunction().size) {
            val right = parseConjunction(ctx.conjunction(i))
            left = BinaryExpression(left as Expression, BinaryOperator.OR, right as Expression, left.line, left.col)
        }
        return left
    }

    private fun parseConjunction(ctx: KotlinParser.ConjunctionContext): AstNode {
        var left = parseEquality(ctx.equality(0))
        for (i in 1 until ctx.equality().size) {
            val right = parseEquality(ctx.equality(i))
            left = BinaryExpression(left as Expression, BinaryOperator.AND, right as Expression, left.line, left.col)
        }
        return left
    }

    private fun parseEquality(ctx: KotlinParser.EqualityContext): AstNode {
        var left = parseComparison(ctx.comparison(0))
        var i = 1
        for (op in ctx.equalityOperator()) {
            val right = parseComparison(ctx.comparison(i))
            val binOp = when {
                op.EQEQ() != null -> BinaryOperator.EQUALS
                op.EXCL_EQEQ() != null || op.EXCL_EQ() != null -> BinaryOperator.NOT_EQUALS
                else -> BinaryOperator.EQUALS
            }
            left = BinaryExpression(left as Expression, binOp, right as Expression, left.line, left.col)
            i++
        }
        return left
    }

    private fun parseComparison(ctx: KotlinParser.ComparisonContext): AstNode {
        var left = parseGenericCallLikeComparison(ctx.genericCallLikeComparison(0))
        var i = 1
        for (op in ctx.comparisonOperator()) {
            val right = parseGenericCallLikeComparison(ctx.genericCallLikeComparison(i))
            val binOp = when {
                op.LANGLE() != null -> BinaryOperator.LESS
                op.RANGLE() != null -> BinaryOperator.GREATER
                op.LE() != null -> BinaryOperator.LESS_EQUAL
                op.GE() != null -> BinaryOperator.GREATER_EQUAL
                else -> BinaryOperator.LESS
            }
            left = BinaryExpression(left as Expression, binOp, right as Expression, left.line, left.col)
            i++
        }
        return left
    }

    private fun parseGenericCallLikeComparison(ctx: KotlinParser.GenericCallLikeComparisonContext): AstNode {
        var expr = parseInfixOperation(ctx.infixOperation())
        for (cs in ctx.callSuffix()) {
            val args = parseCallSuffix(cs)
            expr = when (expr) {
                is Identifier -> FunctionCall(expr.name, args, expr.line, expr.col)
                is FieldAccess -> MethodCall(expr.receiver, expr.fieldName, args, expr.line, expr.col)
                else -> MethodCall(expr as Expression, "_call", args, ctx.start.line, ctx.start.charPositionInLine)
            }
        }
        return expr
    }

    private fun parseCallSuffix(ctx: KotlinParser.CallSuffixContext): List<Expression> {
        val valueArgs = ctx.valueArguments()
        return if (valueArgs != null) {
            valueArgs.valueArgument().mapNotNull { arg ->
                parseExpression(arg.expression()) as? Expression
            }
        } else emptyList()
    }

    private fun parseInfixOperation(ctx: KotlinParser.InfixOperationContext): AstNode {
        var left = parseElvisExpression(ctx.elvisExpression(0))
        if (ctx.inOperator().isNotEmpty() && ctx.elvisExpression().size > 1) {
            val right = parseElvisExpression(ctx.elvisExpression(1))
            left = FunctionCall("_contains", listOf(right as Expression, left as Expression),
                ctx.start.line, ctx.start.charPositionInLine)
        } else if (!ctx.isOperator().isEmpty() && ctx.type().isNotEmpty()) {
            val typeName = parseType(ctx.type(0))
            left = FunctionCall("_isType", listOf(left as Expression,
                StringLiteral(typeName, ctx.start.line, ctx.start.charPositionInLine)),
                ctx.start.line, ctx.start.charPositionInLine)
        }
        return left
    }

    private fun parseElvisExpression(ctx: KotlinParser.ElvisExpressionContext): AstNode {
        var left = parseInfixFunctionCall(ctx.infixFunctionCall(0))
        for (i in 1 until ctx.infixFunctionCall().size) {
            val right = parseInfixFunctionCall(ctx.infixFunctionCall(i))
            left = FunctionCall("_elvis", listOf(left as Expression, right as Expression),
                ctx.start.line, ctx.start.charPositionInLine)
        }
        return left
    }

    private fun parseInfixFunctionCall(ctx: KotlinParser.InfixFunctionCallContext): AstNode {
        var left = parseRangeExpression(ctx.rangeExpression(0))
        for (i in 0 until ctx.simpleIdentifier().size) {
            val name = ctx.simpleIdentifier(i).text
            val right = parseRangeExpression(ctx.rangeExpression(i + 1))
            left = MethodCall(left as Expression, name, listOf(right as Expression),
                ctx.start.line, ctx.start.charPositionInLine)
        }
        return left
    }

    private fun parseRangeExpression(ctx: KotlinParser.RangeExpressionContext): AstNode {
        var left = parseAdditiveExpression(ctx.additiveExpression(0))
        for (i in 1 until ctx.additiveExpression().size) {
            val right = parseAdditiveExpression(ctx.additiveExpression(i))
            left = FunctionCall("_rangeTo", listOf(left as Expression, right as Expression),
                ctx.start.line, ctx.start.charPositionInLine)
        }
        return left
    }

    private fun parseAdditiveExpression(ctx: KotlinParser.AdditiveExpressionContext): AstNode {
        var left = parseMultiplicativeExpression(ctx.multiplicativeExpression(0))
        var i = 1
        for (op in ctx.additiveOperator()) {
            val right = parseMultiplicativeExpression(ctx.multiplicativeExpression(i))
            val binOp = if (op.ADD() != null) BinaryOperator.PLUS else BinaryOperator.MINUS
            left = BinaryExpression(left as Expression, binOp, right as Expression, left.line, left.col)
            i++
        }
        return left
    }

    private fun parseMultiplicativeExpression(ctx: KotlinParser.MultiplicativeExpressionContext): AstNode {
        var left = parseAsExpression(ctx.asExpression(0))
        var i = 1
        for (op in ctx.multiplicativeOperator()) {
            val right = parseAsExpression(ctx.asExpression(i))
            val binOp = when {
                op.MULT() != null -> BinaryOperator.MULTIPLY
                op.DIV() != null -> BinaryOperator.DIVIDE
                op.MOD() != null -> BinaryOperator.MODULO
                else -> BinaryOperator.MULTIPLY
            }
            left = BinaryExpression(left as Expression, binOp, right as Expression, left.line, left.col)
            i++
        }
        return left
    }

    private fun parseAsExpression(ctx: KotlinParser.AsExpressionContext): AstNode {
        var expr = parsePrefixUnaryExpression(ctx.prefixUnaryExpression())
        if (ctx.asOperator().isNotEmpty() && ctx.type().isNotEmpty()) {
            expr = FunctionCall("_as", listOf(expr as Expression,
                StringLiteral(parseType(ctx.type(0)), ctx.start.line, ctx.start.charPositionInLine)),
                ctx.start.line, ctx.start.charPositionInLine)
        }
        return expr
    }

    private fun parsePrefixUnaryExpression(ctx: KotlinParser.PrefixUnaryExpressionContext): AstNode {
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
                expr = UnaryExpression(unaryOp, expr as Expression, ctx.start.line, ctx.start.charPositionInLine)
            }
        }
        return expr
    }

    private fun parsePostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext): AstNode {
        var expr = parsePrimaryExpression(ctx.primaryExpression())

        for (suffix in ctx.postfixUnarySuffix()) {
            expr = when {
                suffix.callSuffix() != null -> {
                    val args = parseCallSuffix(suffix.callSuffix())
                    when (expr) {
                        is Identifier -> FunctionCall(expr.name, args, expr.line, expr.col)
                        is FieldAccess -> MethodCall(expr.receiver, expr.fieldName, args, expr.line, expr.col)
                        else -> MethodCall(expr as Expression, "_call", args, ctx.start.line, ctx.start.charPositionInLine)
                    }
                }
                suffix.indexingSuffix() != null -> {
                    val index = parseExpression(suffix.indexingSuffix().expression(0)) as Expression
                    IndexAccess(expr as Expression, index, ctx.start.line, ctx.start.charPositionInLine)
                }
                suffix.navigationSuffix() != null -> {
                    val nav = suffix.navigationSuffix()
                    val member = when {
                        nav.simpleIdentifier() != null ->
                            Identifier(nav.simpleIdentifier().text, nav.simpleIdentifier().start.line, nav.simpleIdentifier().start.charPositionInLine)
                        nav.parenthesizedExpression() != null -> parseExpression(nav.parenthesizedExpression().expression())
                        else -> null
                    }
                    if (member != null) {
                        when (member) {
                            is Identifier -> FieldAccess(expr as Expression, member.name, member.line, member.col)
                            else -> MethodCall(expr as Expression, "_member", listOf(member as Expression), ctx.start.line, ctx.start.charPositionInLine)
                        }
                    } else expr
                }
                suffix.postfixUnaryOperator() != null -> {
                    val op = suffix.postfixUnaryOperator()
                    when {
                        op.INCR() != null -> Assignment(expr as Expression, BinaryExpression(expr as Expression, BinaryOperator.PLUS,
                            IntLiteral(1, ctx.start.line, ctx.start.charPositionInLine), ctx.start.line, ctx.start.charPositionInLine),
                            ctx.start.line, ctx.start.charPositionInLine)
                        op.DECR() != null -> Assignment(expr as Expression, BinaryExpression(expr as Expression, BinaryOperator.MINUS,
                            IntLiteral(1, ctx.start.line, ctx.start.charPositionInLine), ctx.start.line, ctx.start.charPositionInLine),
                            ctx.start.line, ctx.start.charPositionInLine)
                        op.excl() != null -> FunctionCall("_assertNotNull", listOf(expr as Expression),
                            ctx.start.line, ctx.start.charPositionInLine)
                        else -> expr
                    }
                }
                else -> expr
            }
        }
        return expr
    }

    private fun parsePrimaryExpression(ctx: KotlinParser.PrimaryExpressionContext): AstNode {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        return when {
            ctx.parenthesizedExpression() != null -> parseExpression(ctx.parenthesizedExpression().expression())
            ctx.simpleIdentifier() != null -> Identifier(ctx.simpleIdentifier().text, line, col)
            ctx.literalConstant() != null -> parseLiteralConstant(ctx.literalConstant())
            ctx.stringLiteral() != null -> parseStringLiteral(ctx.stringLiteral())
            ctx.thisExpression() != null -> {
                if (ctx.thisExpression().THIS_AT() != null)
                    Identifier("this@" + ctx.thisExpression().THIS_AT().text.removePrefix("this@"), line, col)
                else Identifier("this", line, col)
            }
            ctx.ifExpression() != null -> visitIfExpression(ctx.ifExpression())
            ctx.whenExpression() != null -> visitWhenExpression(ctx.whenExpression())
            ctx.functionLiteral() != null -> {
                val lit = ctx.functionLiteral()
                if (lit.lambdaLiteral() != null) {
                    val ll = lit.lambdaLiteral()
                    val params = if (ll.lambdaParameters() != null)
                        ll.lambdaParameters().lambdaParameter().map { it.variableDeclaration().simpleIdentifier().text }
                    else emptyList()
                    FunctionCall("_lambda", listOf(StringLiteral(params.joinToString(","), line, col)), line, col)
                } else {
                    FunctionCall("_lambda", listOf(StringLiteral("", line, col)), line, col)
                }
            }
            ctx.jumpExpression() != null -> {
                parseJumpExpression(ctx.jumpExpression()) ?: NullLiteral(line, col)
            }
            ctx.collectionLiteral() != null -> {
                val exprs = ctx.collectionLiteral().expression().map { parseExpression(it) as Expression }
                FunctionCall("listOf", exprs, line, col)
            }
            else -> NullLiteral(line, col)
        }
    }

    private fun parseLiteralConstant(ctx: KotlinParser.LiteralConstantContext): AstNode {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        return when {
            ctx.IntegerLiteral() != null -> IntLiteral(ctx.IntegerLiteral().text.toLong(), line, col)
            ctx.LongLiteral() != null -> IntLiteral(ctx.LongLiteral().text.dropLast(1).toLong(), line, col)
            ctx.HexLiteral() != null -> IntLiteral(ctx.HexLiteral().text.removePrefix("0x").removePrefix("0X").toLong(16), line, col)
            ctx.BinLiteral() != null -> IntLiteral(ctx.BinLiteral().text.removePrefix("0b").removePrefix("0B").toLong(2), line, col)
            ctx.UnsignedLiteral() != null -> IntLiteral(ctx.UnsignedLiteral().text.dropLast(1).toLong(), line, col)
            ctx.RealLiteral() != null -> {
                val text = ctx.RealLiteral().text
                if (text.endsWith("f") || text.endsWith("F")) FloatLiteral(text.dropLast(1).toDouble(), line, col)
                else FloatLiteral(text.toDouble(), line, col)
            }
            ctx.BooleanLiteral() != null -> BoolLiteral(ctx.BooleanLiteral().text == "true", line, col)
            ctx.CharacterLiteral() != null -> IntLiteral(ctx.CharacterLiteral().text[1].code.toLong(), line, col)
            ctx.NullLiteral() != null -> NullLiteral(line, col)
            else -> NullLiteral(line, col)
        }
    }

    private fun parseStringLiteral(ctx: KotlinParser.StringLiteralContext): AstNode {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        return when {
            ctx.lineStringLiteral() != null -> parseLineStringLiteral(ctx.lineStringLiteral())
            ctx.multiLineStringLiteral() != null -> parseMultiLineStringLiteral(ctx.multiLineStringLiteral())
            else -> StringLiteral("", line, col)
        }
    }

    private fun parseLineStringLiteral(ctx: KotlinParser.LineStringLiteralContext): AstNode {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val parts = mutableListOf<InterpolationPart>()

        for (content in ctx.lineStringContent()) {
            when {
                content.LineStrText() != null -> parts.add(LiteralPart(content.LineStrText().text))
                content.LineStrEscapedChar() != null -> parts.add(LiteralPart(unescape(content.LineStrEscapedChar().text)))
                content.LineStrRef() != null -> {
                    val name = content.LineStrRef().text.removePrefix("$")
                    parts.add(ExpressionPart(Identifier(name, line, col)))
                }
            }
        }
        for (expr in ctx.lineStringExpression()) {
            val e = parseExpression(expr.expression())
            if (e is Expression) parts.add(ExpressionPart(e))
        }

        if (parts.size == 1 && parts[0] is LiteralPart) {
            return StringLiteral((parts[0] as LiteralPart).text, line, col)
        }
        return StringInterpolation(parts, line, col)
    }

    private fun parseMultiLineStringLiteral(ctx: KotlinParser.MultiLineStringLiteralContext): AstNode {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val parts = mutableListOf<InterpolationPart>()

        for (content in ctx.multiLineStringContent()) {
            when {
                content.MultiLineStrText() != null -> parts.add(LiteralPart(content.MultiLineStrText().text))
                content.MultiLineStringQuote() != null -> parts.add(LiteralPart("\""))
                content.MultiLineStrRef() != null -> {
                    val name = content.MultiLineStrRef().text.removePrefix("$")
                    parts.add(ExpressionPart(Identifier(name, line, col)))
                }
            }
        }
        for (expr in ctx.multiLineStringExpression()) {
            val e = parseExpression(expr.expression())
            if (e is Expression) parts.add(ExpressionPart(e))
        }

        if (parts.size == 1 && parts[0] is LiteralPart) {
            return StringLiteral((parts[0] as LiteralPart).text, line, col)
        }
        return StringInterpolation(parts, line, col)
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

    override fun visit(tree: org.antlr.v4.runtime.tree.ParseTree?): AstNode? {
        return super.visit(tree)
    }
}
