package allyouneed.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat

/**
 * IEC 80000-13 (binary) short-amount formatting, base 1024.
 * Prefixes: Ki, Mi, Gi, Ti, Pi, Ei, Zi, Yi.
 */
object IecFormat {
    private const val BASE = 1024
    private val BI_BASE = BigInteger.valueOf(BASE.toLong())
    private val UNITS = arrayOf("Ki", "Mi", "Gi", "Ti", "Pi", "Ei", "Zi", "Yi")

    @JvmStatic
    @JvmOverloads
    fun format(number: Long, width: Int = Int.MAX_VALUE): String {
        require(number >= 0) { "amount must be non-negative" }
        return format(BigInteger.valueOf(number), width)
    }

    @JvmStatic
    @JvmOverloads
    fun format(number: BigInteger, width: Int = Int.MAX_VALUE): String {
        require(number.signum() >= 0) { "amount must be non-negative" }

        if (number < BI_BASE) {
            val plain = number.toString()
            return if (plain.length <= width) plain else plain.take(width)
        }

        if (width < Int.MAX_VALUE / 2) {
            return formatWithWidth(number, width)
        }

        return formatExact(number)
    }

    /** Bytes label: plain count if &lt; 1 KiB, else IEC binary (e.g. `4Ki`, `256Mi`). */
    @JvmStatic
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "∞"
        return formatBytes(BigInteger.valueOf(bytes))
    }

    @JvmStatic
    fun formatBytes(bytes: BigInteger): String {
        if (bytes.signum() < 0) return "∞"
        if (bytes.signum() == 0) return "0"
        if (bytes < BI_BASE) return bytes.toString()
        return formatExact(bytes)
    }

    @JvmStatic
    fun formatFull(amount: Long): String = formatFull(BigInteger.valueOf(amount))

    @JvmStatic
    fun formatFull(amount: BigInteger): String =
        NumberFormat.getNumberInstance().format(amount)

    @JvmStatic
    fun saturateToLong(amount: BigInteger): Long = SiFormat.saturateToLong(amount)

    private fun formatExact(number: BigInteger): String {
        var value = number
        var unit = -1
        while (value >= BI_BASE && unit < UNITS.lastIndex) {
            value = value.divide(BI_BASE)
            unit++
        }
        return if (unit < 0) value.toString() else value.toString() + UNITS[unit]
    }

    private fun formatWithWidth(number: BigInteger, width: Int): String {
        var numberSize = number.toString().length
        if (numberSize <= width) {
            return number.toString()
        }

        var base = number
        var last = base.multiply(BI_BASE)
        var exponent = -1
        var postFix = ""

        while (numberSize > width) {
            last = base
            base = base.divide(BI_BASE)
            exponent++
            if (exponent >= UNITS.size) {
                return base.toString().take(maxOf(1, width - UNITS.last().length)) + UNITS.last()
            }
            numberSize = base.toString().length + UNITS[exponent].length
            postFix = UNITS[exponent]
        }

        var withPrecision = formatFractional(last, postFix)
        var withoutPrecision = base.toString() + postFix
        var slim = if (withPrecision.length <= width) withPrecision else withoutPrecision

        if (slim.length > width) {
            while (slim.length > width && exponent + 1 < UNITS.size) {
                last = base
                base = base.divide(BI_BASE)
                exponent++
                postFix = UNITS[exponent]
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

    private fun formatFractional(lastTimesBase: BigInteger, postFix: String): String {
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
