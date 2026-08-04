package allyouneed.util

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.roundToInt
import java.math.RoundingMode as JavaRounding

data class IntegerFormat(
    /** 每个单位的基数，一般是 1000 或 1024 */
    val base: Int,
    /** 每级单位的后缀，从无缩放开始，类似 `["", "k", "M", "G"]` */
    val postfixes: List<String>,
    /** 目标文本长度，输出可以比其短但不能比其长，0 为无限制 */
    val width: Int = 0,
    /** 最小值，当数字小于该值时，显示为 [minDisplay] */
    val min: BigInteger? = null,
    /** 最大值，当数字大于该值时，显示为 [maxDisplay] */
    val max: BigInteger? = null,
    /** 最小值显示的替代文本 */
    val minDisplay: String = "MIN",
    /** 最大值显示的替代文本 */
    val maxDisplay: String = "MAX",
    /**
     * 允许负数（不允许时负数会被显示为 [errDisplay]）
     * - 显示负数时会先将 width 减 1 后格式化绝对值，最后添加上负号
     */
    val allowNegative: Boolean = false,
    /**
     * 在正整数前添加正号，格式化输出类似 -1 0 +1
     * - 与负数相同，会先将 width 减 1 后格式化绝对值，最后添加上正号
     */
    val showPositiveSign: Boolean = false,
    /** 数值格式化错误时显示的内容（格式化错误可能是因为宽度不够） */
    val errDisplay: String = "ERR",
    /**
     * 超过最大单位时是否允许使用 +1 +2 的形式表示，每 +1 就是乘一次 base
     * - 开启后：最大单位设置为 G 时 1T 会被表示为 1G+1
     * - 不开启：最大单位设置为 G 时 1T 会被表示为 1000G
     * - 如果设置的 max 值不够大就不会出现超过最大单位被提升而是直接变成 [maxDisplay]
     * - +k 的 k 没有限制，除非出现 1G+100 但最大宽度设定为 5 导致输出 [errDisplay]
     */
    val allowPlus: Boolean = false,
    /**
     * 进入从当前级别进入下一级单位的阈值
     * - 设置为 500 时 500M 会显示为 0.5G
     * - 设置为 1500 是 1000M 仍然显示为 1000M，直到 1500M 才显示为 1.5G
     * - 0 表示大于等于基数时提升一级
     * - 每次提升时将数字除以 base 然后与 [threshold] 判定是否需要再次提升
     * - [allowPlus] 状态下可以无限提升，否则只能提升到最大单位
     * - 如果 format 后出现长度不符合 [width] 则再尝试提升一级，如果再次失败则直接输出 [errDisplay]
     */
    val threshold: Int = 0,
    /**
     * 是否允许省略为 0 的小数，类似 1.00E -> 1E 或 2.30M -> 2.3M
     * - 可能出现 1.999M -> 2.00M -> 2M 的情况，关闭后会显示为 2.00M
     */
    val allowOmitDecimal: Boolean = false,
    /** 是否允许小数无前缀 0，类似 0.3G -> .3G */
    val allowOmitLeadingZero: Boolean = false,
    /** 最长小数位数，默认为自动：round(log10(base)) */
    val maxDecimalPlaces: Int = -1,
    /** 舍入模式，永远对绝对值进行舍入，不考虑符号 */
    val rounding: Rounding = Rounding.HalfUp,
) {
    enum class Rounding {
        Up, Down, HalfUp, HalfDown, HalfEven;

        fun toJava(): JavaRounding = when (this) {
            Up -> JavaRounding.UP
            Down -> JavaRounding.DOWN
            HalfUp -> JavaRounding.HALF_UP
            HalfDown -> JavaRounding.HALF_DOWN
            HalfEven -> JavaRounding.HALF_EVEN
        }
    }

    private val biBase: BigInteger = BigInteger.valueOf(base.toLong())
    private val promoteThreshold: BigInteger = BigInteger.valueOf((if (threshold <= 0) base else threshold).toLong())
    private val maxDecimal: Int = if (maxDecimalPlaces >= 0) {
        maxDecimalPlaces
    } else {
        kotlin.math.log10(base.toDouble()).roundToInt().coerceAtLeast(0)
    }

    init {
        require(base >= 2) { "base must be >= 2" }
        require(postfixes.isNotEmpty()) { "postfixes must not be empty" }
        require(width >= 0) { "width must be >= 0" }
        require(threshold >= 0) { "threshold must be >= 0" }
        if (width > 0) {
            if (min != null && minDisplay.length > width) {
                throw IllegalArgumentException("minDisplay length exceeds width")
            }
            if (max != null && maxDisplay.length > width) {
                throw IllegalArgumentException("maxDisplay length exceeds width")
            }
            if (errDisplay.length > width) {
                throw IllegalArgumentException("errDisplay length exceeds width")
            }
        }
    }

    fun format(value: Int): String = format(BigInteger.valueOf(value.toLong()))

    fun format(value: Long): String = format(BigInteger.valueOf(value))

    fun format(value: BigInteger): String {
        if (min != null && value < min) return minDisplay
        if (max != null && value > max) return maxDisplay
        val abs = value.abs()
        return when (value.signum()) {
            -1 -> {
                if (!allowNegative) return errDisplay
                formatSigned('-', abs)
            }

            1 -> {
                if (showPositiveSign) {
                    formatSigned('+', abs)
                } else {
                    formatAbsolute(value, width) ?: errDisplay
                }
            }

            else -> {
                formatAbsolute(value, width) ?: errDisplay
            }
        }
    }

    private fun formatSigned(sign: Char, abs: BigInteger): String {
        val bodyW = when {
            width <= 0 -> 0
            width == 1 -> return errDisplay
            else -> width - 1
        }
        val body = formatAbsolute(abs, bodyW) ?: return errDisplay
        val result = sign + body
        if (width > 0 && result.length > width) return errDisplay
        return result
    }

    /**
     * @param bodyWidth 0 = unlimited
     */
    private fun formatAbsolute(abs: BigInteger, bodyWidth: Int): String? {
        // Natural promotion by threshold
        var level = 0
        var scaled = abs
        while (shouldPromote(scaled, level)) {
            scaled = scaled.divide(biBase)
            level++
        }

        // Try rendering; if too long under width, promote further then reduce decimals
        var attemptLevel = level
        val maxExtraPromotes = if (bodyWidth > 0) 64 else 0
        for (extra in 0..maxExtraPromotes) {
            if (extra > 0) {
                if (!canPromoteLevel(attemptLevel)) break
                attemptLevel++
            }
            val rendered = renderAtLevel(abs, attemptLevel, bodyWidth) ?: continue
            if (bodyWidth <= 0 || rendered.length <= bodyWidth) {
                return rendered
            }
        }

        // Last resort: force more promotions while possible
        while (canPromoteLevel(attemptLevel)) {
            attemptLevel++
            val rendered = renderAtLevel(abs, attemptLevel, bodyWidth) ?: continue
            if (bodyWidth <= 0 || rendered.length <= bodyWidth) {
                return rendered
            }
        }

        return null
    }

    private fun shouldPromote(scaledAtLevel: BigInteger, level: Int): Boolean {
        if (scaledAtLevel < promoteThreshold) return false
        return canPromoteLevel(level)
    }

    private fun canPromoteLevel(level: Int): Boolean {
        // level is current index; promoting goes to level+1
        if (allowPlus) return true
        return level + 1 <= postfixes.lastIndex
    }

    private fun unitString(level: Int): String {
        if (level <= postfixes.lastIndex) {
            return postfixes[level]
        }
        // allowPlus overflow: lastUnit + "+" + k
        val k = level - postfixes.lastIndex
        return postfixes.last() + "+" + k
    }

    /**
     * value = abs / base^level, with fractional part from remainder.
     * After rounding carry, may re-promote (e.g. 999.999k → 1M).
     */
    private fun renderAtLevel(abs: BigInteger, level: Int, bodyWidth: Int): String? {
        if (level < 0) return null

        val divisor = biBase.pow(level)
        val intPart = abs.divide(divisor)
        val remainder = abs.remainder(divisor)

        val fracStart = if (maxDecimal <= 0 || remainder.signum() == 0) 0 else maxDecimal

        for (fracDigits in fracStart downTo 0) {
            val parts = computeParts(intPart, remainder, divisor, fracDigits)
            var outInt = parts.first
            var outFrac = parts.second // digits string, may be empty
            var outLevel = level

            // Rounding carry may reach threshold (e.g. 999.9k → 1000k → 1M)
            while (outFrac.isEmpty() && outInt >= promoteThreshold && canPromoteLevel(outLevel)) {
                val divRem = outInt.divideAndRemainder(biBase)
                outInt = divRem[0]
                outLevel++
                if (divRem[1].signum() != 0) {
                    // 1500 → 1.5 at next unit
                    val remDigits = maxDecimal.coerceAtLeast(1)
                    val scale = BigInteger.TEN.pow(remDigits)
                    val fracScaled = divRem[1].multiply(scale).divide(biBase)
                    outFrac = fracScaled.toString().padStart(remDigits, '0')
                    if (allowOmitDecimal) {
                        outFrac = outFrac.trimEnd('0')
                    }
                    break
                }
            }

            val unit = unitString(outLevel)
            val mantissa = formatMantissa(outInt, outFrac)
            val text = mantissa + unit
            if (bodyWidth <= 0 || text.length <= bodyWidth) {
                return text
            }
        }

        val intOnly = intPart.toString() + unitString(level)
        if (bodyWidth <= 0 || intOnly.length <= bodyWidth) {
            return intOnly
        }
        return null
    }

    /** @return (integer part, fractional digit string without trailing zeros if omit) */
    private fun computeParts(
        intPart: BigInteger,
        remainder: BigInteger,
        divisor: BigInteger,
        fracDigits: Int,
    ): Pair<BigInteger, String> {
        if (remainder.signum() == 0 || divisor == BigInteger.ONE) {
            return intPart to ""
        }

        // Round remainder / divisor to fracDigits (0 = round into integer only)
        val scale = if (fracDigits <= 0) BigInteger.ONE else BigInteger.TEN.pow(fracDigits)
        val numer = remainder.multiply(scale)
        val bd = BigDecimal(numer).divide(BigDecimal(divisor), (fracDigits + 2).coerceAtLeast(2), rounding.toJava())
        var fracScaled = bd.setScale(0, rounding.toJava()).toBigInteger()

        var intOut = intPart
        if (fracScaled >= scale) {
            intOut = intOut.add(BigInteger.ONE)
            fracScaled = BigInteger.ZERO
        }

        if (fracDigits <= 0 || fracScaled.signum() == 0) {
            return intOut to ""
        }

        var fracStr = fracScaled.toString().padStart(fracDigits, '0')
        if (allowOmitDecimal) {
            fracStr = fracStr.trimEnd('0')
        }
        return intOut to fracStr
    }

    private fun formatMantissa(intOut: BigInteger, fracStr: String): String {
        if (fracStr.isEmpty()) {
            return intOut.toString()
        }
        return if (intOut.signum() == 0 && allowOmitLeadingZero) {
            ".$fracStr"
        } else {
            "$intOut.$fracStr"
        }
    }

    companion object {
        @JvmField
        val SiPostfixes = listOf("", "k", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q")

        @JvmField
        val IecPostfixes = listOf("", "Ki", "Mi", "Gi", "Ti", "Pi", "Ei", "Zi", "Yi", "Ri", "Qi")

        /** SI decimal prefixes, base 1000. */
        @JvmField
        val SI = IntegerFormat(
            base = 1000,
            postfixes = SiPostfixes,
            allowOmitDecimal = true,
        )

        /** IEC 80000-13 binary prefixes, base 1024. */
        @JvmField
        val IEC = IntegerFormat(
            base = 1024,
            postfixes = IecPostfixes,
            allowOmitDecimal = true,
        )

        /** SI with a fixed slot width (AE2 readable-number style). */
        @JvmStatic
        fun si(width: Int): IntegerFormat = SI.copy(
            width = width,
            allowOmitDecimal = true,
            allowOmitLeadingZero = width in 1..4,
        )

        /** IEC with a fixed slot width. */
        @JvmStatic
        fun iec(width: Int): IntegerFormat = IEC.copy(
            width = width,
            allowOmitDecimal = true,
            allowOmitLeadingZero = width in 1..4,
        )
    }
}
