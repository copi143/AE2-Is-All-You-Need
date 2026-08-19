package kaptor.a2s.runtime

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * 有理数（分数）。a2s 中小数默认类型的运行时表示。
 *
 * 以两个 BigInteger（分子/分母）表示，构造时立即约分，因此 `==` 可直接比较分子分母。
 * 分母恒为正，符号由分子承载。
 */
class Rational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) : Comparable<Rational> {

    fun add(other: Rational): Rational = of(
        numerator * other.denominator + other.numerator * denominator,
        denominator * other.denominator,
    )

    fun sub(other: Rational): Rational = of(
        numerator * other.denominator - other.numerator * denominator,
        denominator * other.denominator,
    )

    fun mul(other: Rational): Rational = of(
        numerator * other.numerator,
        denominator * other.denominator,
    )

    fun div(other: Rational): Rational {
        if (other.numerator.signum() == 0) throw ArithmeticException("division by zero")
        return of(
            numerator * other.denominator,
            denominator * other.numerator,
        )
    }

    fun mod(other: Rational): Rational {
        if (other.numerator.signum() == 0) throw ArithmeticException("modulo by zero")
        val a = numerator * other.denominator
        val b = denominator * other.numerator
        val q = floorDiv(a, b)
        return sub(other.mul(of(q, BigInteger.ONE)))
    }

    fun negate(): Rational = of(numerator.negate(), denominator)

    fun isZero(): Boolean = numerator.signum() == 0

    override fun compareTo(other: Rational): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun equals(other: Any?): Boolean =
        other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    /**
     * 显示格式：`157/50 (3.14)` —— 完整分数 + 括号内自适应精度小数。
     * 分母为 1 时只显示整数。
     */
    override fun toString(): String {
        if (denominator == BigInteger.ONE) return numerator.toString()
        val decimal = BigDecimal(numerator)
            .divide(BigDecimal(denominator), 8, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return "$numerator/$denominator ($decimal)"
    }

    companion object {
        val ZERO: Rational = of(BigInteger.ZERO, BigInteger.ONE)
        val ONE: Rational = of(BigInteger.ONE, BigInteger.ONE)

        @JvmStatic
        fun of(numerator: BigInteger): Rational = of(numerator, BigInteger.ONE)

        @JvmStatic
        fun of(numerator: Long): Rational = of(BigInteger.valueOf(numerator), BigInteger.ONE)

        @JvmStatic
        fun of(numerator: BigInteger, denominator: BigInteger): Rational {
            if (denominator.signum() == 0) throw ArithmeticException("division by zero")
            var n = numerator
            var d = denominator
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            val g = n.gcd(d)
            if (g != BigInteger.ONE) {
                n = n.divide(g)
                d = d.divide(g)
            }
            return Rational(n, d)
        }

        /**
         * 解析十进制小数串为有理数，避免经 f64 中转造成精度损失。
         * `"3.14"` → 157/50。
         */
        @JvmStatic
        fun fromDecimalString(s: String): Rational {
            val idx = s.indexOf('.')
            if (idx < 0) return of(BigInteger(s), BigInteger.ONE)
            val intPart = s.substring(0, idx)
            val fracPart = s.substring(idx + 1)
            val den = BigInteger.TEN.pow(fracPart.length)
            val num = BigInteger(intPart) * den + BigInteger(fracPart)
            return of(num, den)
        }

        private fun floorDiv(a: BigInteger, b: BigInteger): BigInteger {
            var q = a.divide(b)
            val r = a.remainder(b)
            if (r.signum() != 0 && ((r.signum() < 0) != (b.signum() < 0))) {
                q = q.subtract(BigInteger.ONE)
            }
            return q
        }
    }
}
