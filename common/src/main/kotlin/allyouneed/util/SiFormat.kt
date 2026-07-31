package allyouneed.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat

/**
 * SI (decimal) short-amount formatting, base 1000.
 * Prefixes: k M G T P E Z Y R Q (10^3 … 10^30).
 * Values strictly above 999Q display as `999Q+`; use [formatFull] for exact text.
 */
object SiFormat {
    private const val BASE = 1000
    private val POSTFIXES = "kMGTPEZYRQ".toCharArray()
    private val BI_BASE = BigInteger.valueOf(BASE.toLong())
    private val THRESHOLD_999Q: BigInteger =
        BigInteger.valueOf(999).multiply(BI_BASE.pow(POSTFIXES.size))

    @JvmStatic
    fun format(number: Long, width: Int): String {
        require(number >= 0) { "amount must be non-negative" }
        return format(BigInteger.valueOf(number), width)
    }

    @JvmStatic
    fun format(number: BigInteger, width: Int): String {
        require(number.signum() >= 0) { "amount must be non-negative" }
        if (number > THRESHOLD_999Q) {
            return when {
                width >= 5 -> "999Q+"
                width >= 4 -> "999Q"
                else -> "Q+"
            }
        }

        var numberSize = number.toString().length
        if (numberSize <= width) {
            return number.toString()
        }

        var base = number
        var last = base.multiply(BI_BASE)
        var exponent = -1
        var postFix = '\u0000'

        while (numberSize > width) {
            last = base
            base = base.divide(BI_BASE)
            exponent++
            if (exponent >= POSTFIXES.size) {
                return "999Q+"
            }
            numberSize = base.toString().length + 1
            postFix = POSTFIXES[exponent]
        }

        var withPrecision = formatFractional(last, postFix)
        var withoutPrecision = base.toString() + postFix
        var slim = if (withPrecision.length <= width) withPrecision else withoutPrecision

        if (slim.length > width) {
            while (slim.length > width && exponent + 1 < POSTFIXES.size) {
                last = base
                base = base.divide(BI_BASE)
                exponent++
                postFix = POSTFIXES[exponent]
                withPrecision = formatFractional(last, postFix)
                withoutPrecision = base.toString() + postFix
                slim = if (withPrecision.length <= width) withPrecision else withoutPrecision
            }
            if (slim.length > width) {
                slim = withoutPrecision.takeIf { it.length <= width } ?: (base.toString() + postFix)
                if (slim.length > width) {
                    slim = slim.take(width)
                }
            }
        }
        return slim
    }

    @JvmStatic
    fun format(number: Double, width: Int): String {
        require(number >= 0) { "amount must be non-negative" }
        if (number.isNaN() || number.isInfinite()) return "???"
        if (number <= Long.MAX_VALUE.toDouble() && number == Math.rint(number)) {
            return format(number.toLong(), width)
        }
        if (number > Long.MAX_VALUE.toDouble()) {
            return format(BigDecimal.valueOf(number).toBigInteger(), width)
        }

        val integerDigits = maxOf(0, kotlin.math.log10(number).toInt() + 1)
        val fractionalDigits = width - integerDigits - 1
        val minFractional = Math.pow(10.0, -fractionalDigits.toDouble())
        val fractional = number - kotlin.math.floor(number)

        if (fractional < 1e-9 || integerDigits > width - 1) {
            return format(number.toLong(), width)
        }
        if (fractional + 1e-9 < minFractional && integerDigits - 1 <= width) {
            return "~" + format(number.toLong(), width - 1)
        }
        val fmt = decimalFormat()
        fmt.maximumFractionDigits = maxOf(0, fractionalDigits)
        return fmt.format(number)
    }

    @JvmStatic
    fun formatFull(amount: Long): String = formatFull(BigInteger.valueOf(amount))

    @JvmStatic
    fun formatFull(amount: BigInteger): String =
        NumberFormat.getNumberInstance().format(amount)

    @JvmStatic
    fun saturateToLong(amount: BigInteger): Long {
        if (amount.signum() < 0) return 0L
        if (amount.bitLength() > 63) return Long.MAX_VALUE
        return amount.toLong()
    }

    private fun formatFractional(lastTimesBase: BigInteger, postFix: Char): String {
        val value = BigDecimal(lastTimesBase)
            .divide(BigDecimal.valueOf(BASE.toLong()), 1, RoundingMode.DOWN)
        val fmt = decimalFormat()
        fmt.maximumFractionDigits = 1
        return fmt.format(value) + postFix
    }

    private fun decimalFormat(): DecimalFormat {
        val symbols = DecimalFormatSymbols.getInstance()
        return DecimalFormat(".#;0.#").apply {
            isDecimalSeparatorAlwaysShown = false
            decimalFormatSymbols = symbols
            roundingMode = RoundingMode.DOWN
        }
    }
}
