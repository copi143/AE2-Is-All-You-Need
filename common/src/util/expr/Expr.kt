package allyouneed.util.expr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 浮点数学表达式（公式）的不可变 AST。
 *
 * - 求值：实现 `(Map<String, Double>) -> Double`，可当 lambda 直接调用；
 * - 序列化：[toString] 输出最小括号的中缀文本，配合 [Formula.parse] 无损往返；
 * - 组合：运算符重载（算术）、[substitute]/[bind]（代入）、`then`（函数复合）。
 *
 * 约定：负数常量统一表示为 `Neg(Const(正数))`，[Const] 本身只持有非负值，
 * 这样中缀文本与 AST 能结构化往返。
 */
sealed class Expr : (Map<String, Double>) -> Double {

    companion object {
        const val DEFAULT_VARIABLE = "x"
    }

    // ------------------------------------------------------------
    // 求值
    // ------------------------------------------------------------

    fun eval(env: Map<String, Double>): Double = when (this) {
        is Const -> value
        is Var -> env[name] ?: throw IllegalArgumentException("Undefined variable '$name'")
        is Neg -> -operand.eval(env)
        is Add -> l.eval(env) + r.eval(env)
        is Sub -> l.eval(env) - r.eval(env)
        is Mul -> l.eval(env) * r.eval(env)
        is Div -> l.eval(env) / r.eval(env)
        is Pow -> base.eval(env).pow(exp.eval(env))
        is Call -> {
            val builtin = FUNCTIONS[name] ?: throw IllegalArgumentException("Unknown function '$name'")
            if (args.size != builtin.arity) {
                throw IllegalArgumentException("Function '$name' expects ${builtin.arity} argument(s), got ${args.size}")
            }
            builtin.fn(args.map { it.eval(env) }.toDoubleArray())
        }
    }

    override fun invoke(env: Map<String, Double>): Double = eval(env)

    operator fun invoke(vararg bindings: Pair<String, Double>): Double = eval(bindings.toMap())

    operator fun invoke(): Double = eval(emptyMap())

    operator fun invoke(value: Double): Double {
        val vars = variables()
        val name = if (vars.size == 1) vars.single() else DEFAULT_VARIABLE
        return eval(mapOf(name to value))
    }

    // ------------------------------------------------------------
    // 变量与变换
    // ------------------------------------------------------------

    fun variables(): Set<String> = when (this) {
        is Const -> emptySet()
        is Var -> setOf(name)
        is Neg -> operand.variables()
        is Add -> l.variables() + r.variables()
        is Sub -> l.variables() + r.variables()
        is Mul -> l.variables() + r.variables()
        is Div -> l.variables() + r.variables()
        is Pow -> base.variables() + exp.variables()
        is Call -> args.flatMapTo(linkedSetOf()) { it.variables() }
    }

    fun substitute(name: String, replacement: Expr): Expr = when (this) {
        is Const -> this
        is Var -> if (this.name == name) replacement else this
        is Neg -> Neg(operand.substitute(name, replacement))
        is Add -> Add(l.substitute(name, replacement), r.substitute(name, replacement))
        is Sub -> Sub(l.substitute(name, replacement), r.substitute(name, replacement))
        is Mul -> Mul(l.substitute(name, replacement), r.substitute(name, replacement))
        is Div -> Div(l.substitute(name, replacement), r.substitute(name, replacement))
        is Pow -> Pow(base.substitute(name, replacement), exp.substitute(name, replacement))
        is Call -> Call(this.name, args.map { it.substitute(name, replacement) })
    }

    fun bind(bindings: Map<String, Double>): Expr =
        bindings.entries.fold(this) { e, entry -> e.substitute(entry.key, const(entry.value)) }

    fun bind(vararg bindings: Pair<String, Double>): Expr = bind(bindings.toMap())

    // ------------------------------------------------------------
    // 运算符（代码 DSL）
    // ------------------------------------------------------------

    operator fun plus(other: Expr): Expr = Add(this, other)
    operator fun plus(other: Number): Expr = Add(this, const(other.toDouble()))
    operator fun minus(other: Expr): Expr = Sub(this, other)
    operator fun minus(other: Number): Expr = Sub(this, const(other.toDouble()))
    operator fun times(other: Expr): Expr = Mul(this, other)
    operator fun times(other: Number): Expr = Mul(this, const(other.toDouble()))
    operator fun div(other: Expr): Expr = Div(this, other)
    operator fun div(other: Number): Expr = Div(this, const(other.toDouble()))
    infix fun pow(other: Expr): Expr = Pow(this, other)
    infix fun pow(other: Number): Expr = Pow(this, const(other.toDouble()))
    operator fun unaryMinus(): Expr = Neg(this)
}

data class Const(val value: Double) : Expr() {
    override fun toString(): String = formatNumber(value)
}

data class Var(val name: String) : Expr() {
    override fun toString(): String = name
}

data class Neg(val operand: Expr) : Expr() {
    override fun toString(): String = "-" + operand.wrapIf(operand.prec() < 3 || operand is Neg)
}

data class Add(val l: Expr, val r: Expr) : Expr() {
    override fun toString(): String = "${l.wrapIf(l.prec() < 1)} + ${r.wrapIf(r.prec() <= 1 || r is Neg)}"
}

data class Sub(val l: Expr, val r: Expr) : Expr() {
    override fun toString(): String = "${l.wrapIf(l.prec() < 1)} - ${r.wrapIf(r.prec() <= 1 || r is Neg)}"
}

data class Mul(val l: Expr, val r: Expr) : Expr() {
    override fun toString(): String = "${l.wrapIf(l.prec() < 2)} * ${r.wrapIf(r.prec() <= 2 || r is Neg)}"
}

data class Div(val l: Expr, val r: Expr) : Expr() {
    override fun toString(): String = "${l.wrapIf(l.prec() < 2)} / ${r.wrapIf(r.prec() <= 2 || r is Neg)}"
}

data class Pow(val base: Expr, val exp: Expr) : Expr() {
    override fun toString(): String = "${base.wrapIf(base.prec() <= 4)}^${exp.wrapIf(exp.prec() < 4)}"
}

data class Call(val name: String, val args: List<Expr>) : Expr() {
    override fun toString(): String = "$name(${args.joinToString(", ")})"
}

// ------------------------------------------------------------
// 辅助
// ------------------------------------------------------------

/** 把数值规范化为表达式：负数转成 `Neg(Const(正数))`，保证文本往返。 */
internal fun const(v: Double): Expr = if (v < 0.0) Neg(Const(-v)) else Const(v)

/** 运算符优先级：`+ -`(1) `< * /`(2) `< 一元负`(3) `< ^`(4) `< 原子`(5)。 */
private fun Expr.prec(): Int = when (this) {
    is Add, is Sub -> 1
    is Mul, is Div -> 2
    is Neg -> 3
    is Pow -> 4
    is Const, is Var, is Call -> 5
}

private fun Expr.wrapIf(cond: Boolean): String = if (cond) "($this)" else toString()

private fun formatNumber(v: Double): String {
    if (v == Math.floor(v) && !v.isInfinite() && Math.abs(v) < 9.007199254740992E15) {
        return v.toLong().toString()
    }
    return v.toString()
}

private data class Builtin(val arity: Int, val fn: (DoubleArray) -> Double)

private val FUNCTIONS: Map<String, Builtin> = mapOf(
    "sqrt" to Builtin(1) { sqrt(it[0]) },
    "abs" to Builtin(1) { abs(it[0]) },
    "sin" to Builtin(1) { sin(it[0]) },
    "cos" to Builtin(1) { cos(it[0]) },
    "tan" to Builtin(1) { tan(it[0]) },
    "log" to Builtin(1) { ln(it[0]) },
    "log10" to Builtin(1) { log10(it[0]) },
    "exp" to Builtin(1) { exp(it[0]) },
    "floor" to Builtin(1) { floor(it[0]) },
    "ceil" to Builtin(1) { ceil(it[0]) },
    "round" to Builtin(1) { round(it[0]) },
    "min" to Builtin(2) { min(it[0], it[1]) },
    "max" to Builtin(2) { max(it[0], it[1]) },
)

// 数字在左侧的运算符：`2 * x` 等
operator fun Number.plus(other: Expr): Expr = Add(const(this.toDouble()), other)
operator fun Number.minus(other: Expr): Expr = Sub(const(this.toDouble()), other)
operator fun Number.times(other: Expr): Expr = Mul(const(this.toDouble()), other)
operator fun Number.div(other: Expr): Expr = Div(const(this.toDouble()), other)
