package allyouneed.util.expr

/**
 * 公式工具的入口：变量/常量构造、内置函数、文本解析与组合。
 */
object Formula {

    const val DEFAULT_VARIABLE = Expr.DEFAULT_VARIABLE

    fun variable(name: String): Expr = Var(name)

    fun constant(value: Double): Expr = const(value)

    fun parse(text: String): Expr = FormulaParser(text.trim()).parse()

    /** builder 风格：`formula { x * x + y }`。 */
    fun formula(block: FormulaScope.() -> Expr): Expr = FormulaScope().block()

    /** 函数复合 f(g(x))，要求外层为单变量。 */
    fun compose(outer: Expr, inner: Expr): Expr {
        val vars = outer.variables()
        return when {
            vars.isEmpty() -> outer
            vars.size == 1 -> outer.substitute(vars.single(), inner)
            else -> throw IllegalArgumentException("compose 需要单变量的外层表达式，发现变量 $vars")
        }
    }

    // ---- 一元函数 ----

    fun sqrt(x: Expr): Expr = Call("sqrt", listOf(x))
    fun sqrt(x: Number): Expr = sqrt(const(x.toDouble()))

    fun abs(x: Expr): Expr = Call("abs", listOf(x))
    fun abs(x: Number): Expr = abs(const(x.toDouble()))

    fun sin(x: Expr): Expr = Call("sin", listOf(x))
    fun sin(x: Number): Expr = sin(const(x.toDouble()))

    fun cos(x: Expr): Expr = Call("cos", listOf(x))
    fun cos(x: Number): Expr = cos(const(x.toDouble()))

    fun tan(x: Expr): Expr = Call("tan", listOf(x))
    fun tan(x: Number): Expr = tan(const(x.toDouble()))

    fun log(x: Expr): Expr = Call("log", listOf(x))
    fun log(x: Number): Expr = log(const(x.toDouble()))

    fun log10(x: Expr): Expr = Call("log10", listOf(x))
    fun log10(x: Number): Expr = log10(const(x.toDouble()))

    fun exp(x: Expr): Expr = Call("exp", listOf(x))
    fun exp(x: Number): Expr = exp(const(x.toDouble()))

    fun floor(x: Expr): Expr = Call("floor", listOf(x))
    fun floor(x: Number): Expr = floor(const(x.toDouble()))

    fun ceil(x: Expr): Expr = Call("ceil", listOf(x))
    fun ceil(x: Number): Expr = ceil(const(x.toDouble()))

    fun round(x: Expr): Expr = Call("round", listOf(x))
    fun round(x: Number): Expr = round(const(x.toDouble()))

    // ---- 二元函数 ----

    fun min(a: Expr, b: Expr): Expr = Call("min", listOf(a, b))
    fun min(a: Number, b: Expr): Expr = min(const(a.toDouble()), b)
    fun min(a: Expr, b: Number): Expr = min(a, const(b.toDouble()))
    fun min(a: Number, b: Number): Expr = min(const(a.toDouble()), const(b.toDouble()))

    fun max(a: Expr, b: Expr): Expr = Call("max", listOf(a, b))
    fun max(a: Number, b: Expr): Expr = max(const(a.toDouble()), b)
    fun max(a: Expr, b: Number): Expr = max(a, const(b.toDouble()))
    fun max(a: Number, b: Number): Expr = max(const(a.toDouble()), const(b.toDouble()))

    fun pow(base: Expr, exp: Expr): Expr = Pow(base, exp)
    fun pow(base: Number, exp: Expr): Expr = Pow(const(base.toDouble()), exp)
    fun pow(base: Expr, exp: Number): Expr = Pow(base, const(exp.toDouble()))
    fun pow(base: Number, exp: Number): Expr = Pow(const(base.toDouble()), const(exp.toDouble()))
}

/** [Formula.formula] 的构建作用域：提供 x/y/z 及按名取变量。 */
class FormulaScope {
    val x: Expr get() = Var("x")
    val y: Expr get() = Var("y")
    val z: Expr get() = Var("z")
    operator fun get(name: String): Expr = Var(name)
}

// ---- 一元函数的 getter 快捷写法：x.sqrt、x.abs 等 ----

val Expr.sqrt: Expr get() = Formula.sqrt(this)
val Expr.abs: Expr get() = Formula.abs(this)
val Expr.sin: Expr get() = Formula.sin(this)
val Expr.cos: Expr get() = Formula.cos(this)
val Expr.tan: Expr get() = Formula.tan(this)
val Expr.log: Expr get() = Formula.log(this)
val Expr.log10: Expr get() = Formula.log10(this)
val Expr.exp: Expr get() = Formula.exp(this)
val Expr.floor: Expr get() = Formula.floor(this)
val Expr.ceil: Expr get() = Formula.ceil(this)
val Expr.round: Expr get() = Formula.round(this)

// ---- 函数复合 ----

infix fun Expr.then(inner: Expr): Expr = Formula.compose(this, inner)
