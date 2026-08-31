package allyouneed.util.expr

/**
 * 中缀表达式解析器，把文本解析为 [Expr]。
 *
 * 文法（优先级从低到高）：
 * ```
 * expr    := term (('+' | '-') term)*
 * term    := unary (('*' | '/') unary)*
 * unary   := ('+' | '-') unary | power
 * power   := primary ('^' unary)?        // 右结合
 * primary := 数字 | 标识符 | 标识符 '(' 参数 ')' | '(' expr ')'
 * ```
 */
internal class FormulaParser(private val src: String) {

    private var pos = 0

    fun parse(): Expr {
        val e = expr()
        skipWs()
        if (pos < src.length) fail("unexpected '${src[pos]}'")
        return e
    }

    private fun expr(): Expr {
        var left = term()
        skipWs()
        while (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
            val op = src[pos++]
            val right = term()
            left = if (op == '+') Add(left, right) else Sub(left, right)
            skipWs()
        }
        return left
    }

    private fun term(): Expr {
        var left = unary()
        skipWs()
        while (pos < src.length && (src[pos] == '*' || src[pos] == '/')) {
            val op = src[pos++]
            val right = unary()
            left = if (op == '*') Mul(left, right) else Div(left, right)
            skipWs()
        }
        return left
    }

    private fun unary(): Expr {
        skipWs()
        if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
            val op = src[pos++]
            val operand = unary()
            return if (op == '-') Neg(operand) else operand
        }
        return power()
    }

    private fun power(): Expr {
        val base = primary()
        skipWs()
        if (pos < src.length && src[pos] == '^') {
            pos++
            return Pow(base, unary())
        }
        return base
    }

    private fun primary(): Expr {
        skipWs()
        if (pos >= src.length) fail("unexpected end of expression")
        val c = src[pos]
        return when {
            c == '(' -> {
                pos++
                val e = expr()
                skipWs()
                if (pos >= src.length || src[pos] != ')') fail("expected ')'")
                pos++
                e
            }
            c.isDigit() || c == '.' -> number()
            c.isLetter() || c == '_' -> identifier()
            else -> fail("unexpected character '$c'")
        }
    }

    private fun identifier(): Expr {
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
        val name = src.substring(start, pos)
        skipWs()
        if (pos < src.length && src[pos] == '(') {
            pos++
            val args = mutableListOf<Expr>()
            skipWs()
            if (pos < src.length && src[pos] == ')') {
                pos++
                return Call(name, args)
            }
            while (true) {
                args.add(expr())
                skipWs()
                when {
                    pos < src.length && src[pos] == ',' -> pos++
                    pos < src.length && src[pos] == ')' -> {
                        pos++
                        break
                    }
                    else -> fail("expected ',' or ')'")
                }
            }
            return Call(name, args)
        }
        return Var(name)
    }

    private fun number(): Expr {
        val start = pos
        while (pos < src.length && src[pos].isDigit()) pos++
        if (pos < src.length && src[pos] == '.') {
            pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            pos++
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        return Const(src.substring(start, pos).toDouble())
    }

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun fail(msg: String): Nothing =
        throw IllegalArgumentException("$msg at position $pos in \"$src\"")
}
