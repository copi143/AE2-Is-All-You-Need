package allyouneed.util

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.roundToInt
import java.math.RoundingMode as JavaRounding

/**
 * 紧凑数值格式化（SI/IEC 词头）。
 *
 * 同一配置类按入参类型分派：
 * - [Int]/[Long]/[BigInteger]：整数快路径（无分母、不做降级）
 * - [BigDecimal]/[Double]/分子分母：有理数路径（可降级到 smaller 词头）
 *
 * 超过已命名最大/最小词头时是否使用 `G+1` / `m-1` 等形式，仅由 [MetricPrefix.largerUnlimited] /
 * [MetricPrefix.smallerUnlimited] 决定。
 */
data class MetricFormat(
    /** 每个单位的基数，一般是 1000 或 1024 */
    val base: Int,
    /** 每级的单位，参考 [MetricPrefix] 文档 */
    val mp: MetricPrefix,
    /** 目标文本长度，输出可以比其短但不能比其长，0 为无限制 */
    val width: Int = 0,
    /** 最小值，小于时显示 [minDisplay] */
    val min: BigInteger? = null,
    /** 最大值，大于时显示 [maxDisplay] */
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
     * 在正数前添加正号，格式化输出类似 -1 0 +1
     * - 与负数相同，会先将 width 减 1 后格式化绝对值，最后添加上正号
     */
    val showPositiveSign: Boolean = false,
    /** 数值格式化错误时显示的内容（格式化错误可能是因为宽度不够） */
    val errDisplay: String = "ERR",
    /**
     * 从当前级别进入更大一级单位的阈值
     * - 设置为 500 时 500M 会显示为 0.5G
     * - 设置为 1500 时 1000M 仍然显示为 1000M，直到 1500M 才显示为 1.5G
     * - 0 表示大于等于基数时提升一级
     * - 能否越过最大命名单位由 [MetricPrefix.largerUnlimited] 决定
     * - 若 format 后长度超过 [width] 则再尝试提升一级，仍失败则 [errDisplay]
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
    /**
     * 不可约有理数（den > 0，符号在 num）。
     * 作为统一比较与有理数路径的内核表示。
     */
    data class BigRational(val num: BigInteger, val den: BigInteger) : Comparable<BigRational> {
        init {
            require(den.signum() > 0) { "denominator must be positive" }
        }

        val signum: Int get() = num.signum()

        fun abs(): BigRational = if (num.signum() >= 0) this else BigRational(num.negate(), den)

        override fun compareTo(other: BigRational): Int =
            num.multiply(other.den).compareTo(other.num.multiply(den))

        companion object {
            val ZERO: BigRational = BigRational(BigInteger.ZERO, BigInteger.ONE)

            @JvmStatic
            fun of(value: BigInteger): BigRational = BigRational(value, BigInteger.ONE)

            @JvmStatic
            fun of(value: Long): BigRational = of(BigInteger.valueOf(value))

            @JvmStatic
            fun of(num: BigInteger, den: BigInteger): BigRational {
                require(den.signum() != 0) { "denominator must be non-zero" }
                if (num.signum() == 0) return ZERO
                var n = num
                var d = den
                if (d.signum() < 0) {
                    n = n.negate()
                    d = d.negate()
                }
                val g = n.abs().gcd(d)
                return BigRational(n.divide(g), d.divide(g))
            }

            @JvmStatic
            fun of(value: BigDecimal): BigRational {
                if (value.signum() == 0) return ZERO
                val scale = value.scale()
                val unscaled = value.unscaledValue()
                return if (scale <= 0) {
                    of(unscaled.multiply(BigInteger.TEN.pow(-scale)))
                } else {
                    of(unscaled, BigInteger.TEN.pow(scale))
                }
            }
        }
    }

    /**
     * 单位前缀（Metric Prefix）配置类。
     *
     * @property baseLevel 无缩放（Base^0）时的基础单位前缀
     * @property larger 放大级别（Level > 0）的前缀列表，如 `["k", "M", "G"]`
     * @property smaller 缩小级别（Level < 0）的前缀列表，从近到远，如 `["m", "µ", "n"]`
     * @property largerUnlimited 超过 [larger] 时是否用 `+N`（如 `G+1`）无限扩展
     * @property smallerUnlimited 超过 [smaller] 时是否用 `-N`（如 `m-1`）无限扩展
     */
    data class MetricPrefix(
        val baseLevel: String,
        val larger: List<String>,
        val smaller: List<String>,
        val largerUnlimited: Boolean,
        val smallerUnlimited: Boolean,
    ) {
        constructor(
            baseLevel: String,
            larger: List<String?>,
            smaller: List<String?>,
        ) : this(
            baseLevel,
            larger.dropLastWhile { it == null }.map { requireNotNull(it) { "null in larger middle" } },
            smaller.dropLastWhile { it == null }.map { requireNotNull(it) { "null in smaller middle" } },
            larger.isNotEmpty() && larger.last() != null,
            smaller.isNotEmpty() && smaller.last() != null,
        )

        /**
         * @param n 0 基础；>0 放大；<0 缩小
         * @return 前缀；越界且未 unlimited 时为 null
         */
        fun level(n: Int): String? {
            if (n > 0) {
                larger.getOrNull(n - 1)?.let { return it }
                if (!largerUnlimited) return null
                return (larger.lastOrNull() ?: baseLevel) + "+" + (n - larger.size)
            }
            if (n < 0) {
                smaller.getOrNull(-n - 1)?.let { return it }
                if (!smallerUnlimited) return null
                return (smaller.lastOrNull() ?: baseLevel) + "-" + (-n - smaller.size)
            }
            return baseLevel
        }

        companion object {
            @JvmField
            val SI = of(
                null,
                "q", "r", "y", "z", "a", "f", "p", "n", "µ", "m",
                "",
                "k", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q",
                null,
            )!!

            @JvmField
            val IEC = of(null, "", "Ki", "Mi", "Gi", "Ti", "Pi", "Ei", "Zi", "Yi", "Ri", "Qi", null)!!

            @JvmField
            val EMPTY = of("")!!

            /**
             * 平铺词头序列构建实例。
             * 公共后缀为 base；首/尾非 null 分别开启 smaller/larger unlimited。
             */
            fun of(vararg levels: String?): MetricPrefix? {
                if (levels.isEmpty()) return null
                val largerUnlimited = levels.last() != null
                val smallerUnlimited = levels.first() != null
                val list = levels.dropWhile { it == null }.dropLastWhile { it == null }.let { mid ->
                    if (mid.any { it == null }) return null
                    mid.filterNotNull()
                }
                if (list.isEmpty()) return null
                val commonSuffix = list.reduce { acc, current ->
                    val commonLen = (0 until minOf(acc.length, current.length)).takeWhile { i ->
                        acc[acc.length - 1 - i] == current[current.length - 1 - i]
                    }.size
                    acc.takeLast(commonLen)
                }
                if (list.size != list.toSet().size) return null
                val baseLevelIndex = list.indexOf(commonSuffix)
                if (baseLevelIndex < 0) return null
                return MetricPrefix(
                    baseLevel = list[baseLevelIndex],
                    larger = list.subList(baseLevelIndex + 1, list.size),
                    smaller = list.subList(0, baseLevelIndex).reversed(),
                    largerUnlimited = largerUnlimited,
                    smallerUnlimited = smallerUnlimited,
                )
            }
        }
    }

    enum class Rounding {
        Up, Down, HalfUp, HalfDown, HalfEven;

        val java: JavaRounding
            get() = when (this) {
                Up -> JavaRounding.UP
                Down -> JavaRounding.DOWN
                HalfUp -> JavaRounding.HALF_UP
                HalfDown -> JavaRounding.HALF_DOWN
                HalfEven -> JavaRounding.HALF_EVEN
            }
    }

    private val biBase: BigInteger = BigInteger.valueOf(base.toLong())
    private val promoteThreshold: BigInteger =
        BigInteger.valueOf((if (threshold <= 0) base else threshold).toLong())
    private val maxDecimal: Int = if (maxDecimalPlaces >= 0) {
        maxDecimalPlaces
    } else {
        kotlin.math.log10(base.toDouble()).roundToInt().coerceAtLeast(0)
    }

    init {
        require(base >= 2) { "base must be >= 2" }
        require(base <= 16777216) { "base must be <= 16777216" }
        require(width >= 0) { "width must be >= 0 (0 is infinity)" }
        require(width <= 1048576) { "width must be <= 1048576" }
        require(threshold >= 0) { "threshold must be >= 0" }
        require(threshold <= 67108864) { "threshold must be <= 67108864" }
        if (min != null && max != null) {
            require(min <= max) { "min must be <= max" }
        }
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

    // ── public entry points ──────────────────────────────────────

    /** 完整字符串 = 数字部分 + 单位前缀 */
    fun format(value: Int): String = join(formatParts(value))

    fun format(value: Long): String = join(formatParts(value))

    fun format(value: BigInteger): String = join(formatParts(value))

    fun format(num: BigInteger, den: BigInteger): String = join(formatParts(num, den))

    fun format(value: BigRational): String = join(formatParts(value))

    fun format(value: BigDecimal): String = join(formatParts(value))

    fun format(value: Double): String = join(formatParts(value))

    /**
     * 拆成 (数字, 单位前缀)。
     * - 正常：`"1.5" to "k"`
     * - 无词头：`"999" to ""`
     * - min/max/错误：替代全文在 first，second 为空
     */
    fun formatParts(value: Int): Pair<String, String> =
        formatParts(BigInteger.valueOf(value.toLong()))

    fun formatParts(value: Long): Pair<String, String> =
        formatParts(BigInteger.valueOf(value))

    fun formatParts(value: BigInteger): Pair<String, String> {
        clampParts(BigRational.of(value))?.let { return it }
        return when (value.signum()) {
            -1 -> {
                if (!allowNegative) return errParts()
                formatSignedParts('-') { bw -> formatIntegerAbsolute(value.abs(), bw) }
            }
            1 -> {
                if (showPositiveSign) {
                    formatSignedParts('+') { bw -> formatIntegerAbsolute(value, bw) }
                } else {
                    formatIntegerAbsolute(value, width) ?: errParts()
                }
            }
            else -> formatIntegerAbsolute(BigInteger.ZERO, width) ?: errParts()
        }
    }

    fun formatParts(num: BigInteger, den: BigInteger): Pair<String, String> =
        formatParts(BigRational.of(num, den))

    fun formatParts(value: BigRational): Pair<String, String> {
        clampParts(value)?.let { return it }
        return when (value.signum) {
            -1 -> {
                if (!allowNegative) return errParts()
                val abs = value.abs()
                formatSignedParts('-') { bw -> formatRationalAbsolute(abs.num, abs.den, bw) }
            }
            1 -> {
                if (showPositiveSign) {
                    formatSignedParts('+') { bw -> formatRationalAbsolute(value.num, value.den, bw) }
                } else {
                    formatRationalAbsolute(value.num, value.den, width) ?: errParts()
                }
            }
            else -> formatIntegerAbsolute(BigInteger.ZERO, width) ?: errParts()
        }
    }

    fun formatParts(value: BigDecimal): Pair<String, String> = formatParts(BigRational.of(value))

    fun formatParts(value: Double): Pair<String, String> {
        if (value.isNaN() || value.isInfinite()) return errParts()
        if (value.toLong().toDouble() == value && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
            return formatParts(value.toLong())
        }
        return formatParts(BigRational.of(BigDecimal.valueOf(value)))
    }

    // ── clamp / sign ─────────────────────────────────────────────

    private fun join(parts: Pair<String, String>): String = parts.first + parts.second

    private fun errParts(): Pair<String, String> = errDisplay to ""

    private fun partsLen(parts: Pair<String, String>): Int = parts.first.length + parts.second.length

    private fun fits(parts: Pair<String, String>, bodyWidth: Int): Boolean =
        bodyWidth <= 0 || partsLen(parts) <= bodyWidth

    private fun clampParts(value: BigRational): Pair<String, String>? {
        if (min != null && value < BigRational.of(min)) return minDisplay to ""
        if (max != null && value > BigRational.of(max)) return maxDisplay to ""
        return null
    }

    private inline fun formatSignedParts(
        sign: Char,
        body: (bodyWidth: Int) -> Pair<String, String>?,
    ): Pair<String, String> {
        val bodyW = when {
            width <= 0 -> 0
            width == 1 -> return errParts()
            else -> width - 1
        }
        val (num, unit) = body(bodyW) ?: return errParts()
        val signed = sign + num
        if (width > 0 && signed.length + unit.length > width) return errParts()
        return signed to unit
    }

    // ── level helpers（唯一依据：mp.level）────────────────────────

    private fun canPromoteFrom(level: Int): Boolean = mp.level(level + 1) != null

    private fun canDemoteFrom(level: Int): Boolean = mp.level(level - 1) != null

    // ── shared rounding（单次舍入）────────────────────────────────

    /** round(numer/denom) → BigInteger，只应用一次 [rounding] */
    private fun roundRatio(numer: BigInteger, denom: BigInteger): BigInteger {
        if (numer.signum() == 0) return BigInteger.ZERO
        return BigDecimal(numer).divide(BigDecimal(denom), 0, rounding.java).toBigInteger()
    }

    private fun formatMantissa(intOut: BigInteger, fracStr: String): String {
        if (fracStr.isEmpty()) return intOut.toString()
        return if (intOut.signum() == 0 && allowOmitLeadingZero) {
            ".$fracStr"
        } else {
            "$intOut.$fracStr"
        }
    }

    private fun fracString(fracScaled: BigInteger, fracDigits: Int): String {
        if (fracDigits <= 0 || fracScaled.signum() == 0) return ""
        var s = fracScaled.toString().padStart(fracDigits, '0')
        if (allowOmitDecimal) s = s.trimEnd('0')
        return s
    }

    /**
     * @return (intPart, fracDigitsString)；frac 可能因进位清空
     */
    private fun computeParts(
        intPart: BigInteger,
        remainder: BigInteger,
        divisor: BigInteger,
        fracDigits: Int,
    ): Pair<BigInteger, String> {
        if (remainder.signum() == 0 || divisor == BigInteger.ONE) {
            return intPart to ""
        }
        val scale = if (fracDigits <= 0) BigInteger.ONE else BigInteger.TEN.pow(fracDigits)
        var fracScaled = roundRatio(remainder.multiply(scale), divisor)
        var intOut = intPart
        if (fracScaled >= scale) {
            intOut = intOut.add(BigInteger.ONE)
            fracScaled = BigInteger.ZERO
        }
        return intOut to fracString(fracScaled, fracDigits)
    }

    /** 进位升档后若有余数，按同一 rounding 生成小数 */
    private fun fracFromRemainder(rem: BigInteger): String {
        val remDigits = maxDecimal.coerceAtLeast(1)
        val scale = BigInteger.TEN.pow(remDigits)
        val fracScaled = roundRatio(rem.multiply(scale), biBase)
        return fracString(fracScaled, remDigits)
    }

    /**
     * 舍入进位可能导致达到 threshold，继续升档（999.5k → 1M）。
     * @return null 若最终 level 无词头
     */
    private fun applyCarryPromote(
        intIn: BigInteger,
        fracIn: String,
        levelIn: Int,
    ): Triple<BigInteger, String, Int>? {
        var outInt = intIn
        var outFrac = fracIn
        var outLevel = levelIn
        while (outFrac.isEmpty() && outInt >= promoteThreshold && canPromoteFrom(outLevel)) {
            val divRem = outInt.divideAndRemainder(biBase)
            outInt = divRem[0]
            outLevel++
            if (divRem[1].signum() != 0) {
                outFrac = fracFromRemainder(divRem[1])
                break
            }
        }
        mp.level(outLevel) ?: return null
        return Triple(outInt, outFrac, outLevel)
    }

    private fun assemble(intOut: BigInteger, fracStr: String, level: Int, bodyWidth: Int): Pair<String, String>? {
        val unit = mp.level(level) ?: return null
        val parts = formatMantissa(intOut, fracStr) to unit
        if (!fits(parts, bodyWidth)) return null
        return parts
    }

    // ── integer fast path ────────────────────────────────────────

    /**
     * 非负整数快路径：等价 den=1，且 level≥0（不降级）。
     * @return (数字, 单位) 或 null
     */
    private fun formatIntegerAbsolute(abs: BigInteger, bodyWidth: Int): Pair<String, String>? {
        var level = 0
        var scaled = abs
        while (scaled >= promoteThreshold && canPromoteFrom(level)) {
            scaled = scaled.divide(biBase)
            level++
        }
        return fitByShifting(level, bodyWidth, demote = false) { lv ->
            renderIntegerAtLevel(abs, lv, bodyWidth)
        }
    }

    private fun renderIntegerAtLevel(abs: BigInteger, level: Int, bodyWidth: Int): Pair<String, String>? {
        if (level < 0) return null
        val unit = mp.level(level) ?: return null
        val divisor = powBase(level)
        val intPart = abs.divide(divisor)
        val remainder = abs.remainder(divisor)
        val fracStart = if (maxDecimal <= 0 || remainder.signum() == 0) 0 else maxDecimal

        for (fracDigits in fracStart downTo 0) {
            val (rawInt, rawFrac) = computeParts(intPart, remainder, divisor, fracDigits)
            val carried = applyCarryPromote(rawInt, rawFrac, level) ?: continue
            assemble(carried.first, carried.second, carried.third, bodyWidth)?.let { return it }
        }

        val intOnly = intPart.toString() to unit
        if (fits(intOnly, bodyWidth)) return intOnly
        return null
    }

    // ── rational path ────────────────────────────────────────────

    /**
     * 非负有理数 num/den（已约分、均 >0 或 num=0）。
     * 支持降级到 smaller 词头。
     */
    private fun formatRationalAbsolute(num: BigInteger, den: BigInteger, bodyWidth: Int): Pair<String, String>? {
        if (num.signum() == 0) {
            return formatIntegerAbsolute(BigInteger.ZERO, bodyWidth)
        }
        // 整数且 den=1：快路径
        if (den == BigInteger.ONE) {
            return formatIntegerAbsolute(num, bodyWidth)
        }

        var level = 0
        while (canPromoteFrom(level) && shouldPromoteRational(num, den, level)) level++
        while (canDemoteFrom(level) && shouldDemoteRational(num, den, level)) level--
        // 降档后可能重新越过 threshold（如 0.6 → 600m → 0.6，threshold=500）
        while (canPromoteFrom(level) && shouldPromoteRational(num, den, level)) level++

        return fitByShifting(level, bodyWidth, demote = true) { lv ->
            renderRationalAtLevel(num, den, lv, bodyWidth)
        }
    }

    /** mantissa = (num/den) / base^level >= threshold */
    private fun shouldPromoteRational(num: BigInteger, den: BigInteger, level: Int): Boolean {
        return if (level >= 0) {
            // num >= threshold * den * base^level
            num >= promoteThreshold.multiply(den).multiply(powBase(level))
        } else {
            // num * base^{-level} >= threshold * den
            num.multiply(powBase(-level)) >= promoteThreshold.multiply(den)
        }
    }

    /** mantissa < 1（0 已排除） */
    private fun shouldDemoteRational(num: BigInteger, den: BigInteger, level: Int): Boolean {
        return if (level >= 0) {
            // num < den * base^level
            num < den.multiply(powBase(level))
        } else {
            // num * base^{-level} < den
            num.multiply(powBase(-level)) < den
        }
    }

    private fun powBase(exp: Int): BigInteger =
        if (exp == 0) BigInteger.ONE else biBase.pow(exp)

    private fun renderRationalAtLevel(
        num: BigInteger,
        den: BigInteger,
        level: Int,
        bodyWidth: Int,
    ): Pair<String, String>? {
        mp.level(level) ?: return null

        // mantissa = num * base^{-level} / den
        // L>=0: numer=num, divisor=den*base^L
        // L<0:  numer=num*base^{-L}, divisor=den
        val (numer, divisor) = if (level >= 0) {
            num to den.multiply(powBase(level))
        } else {
            num.multiply(powBase(-level)) to den
        }

        val intPart = numer.divide(divisor)
        val remainder = numer.remainder(divisor)
        val fracStart = if (maxDecimal <= 0 || remainder.signum() == 0) 0 else maxDecimal

        for (fracDigits in fracStart downTo 0) {
            val (rawInt, rawFrac) = computeParts(intPart, remainder, divisor, fracDigits)
            val carried = applyCarryPromote(rawInt, rawFrac, level) ?: continue
            assemble(carried.first, carried.second, carried.third, bodyWidth)?.let { return it }
        }

        val unit = mp.level(level) ?: return null
        val intOnly = intPart.toString() to unit
        if (fits(intOnly, bodyWidth)) return intOnly
        return null
    }

    // ── width fitting ────────────────────────────────────────────

    /**
     * 先试自然 level，过长则升档；有理数路径还可降档以缩短（如 0.000001 → 1µ）。
     */
    private inline fun fitByShifting(
        startLevel: Int,
        bodyWidth: Int,
        demote: Boolean,
        render: (level: Int) -> Pair<String, String>?,
    ): Pair<String, String>? {
        render(startLevel)?.let { if (fits(it, bodyWidth)) return it }

        if (bodyWidth <= 0) return render(startLevel)

        var level = startLevel
        var guard = 0
        while (canPromoteFrom(level) && guard++ < 128) {
            level++
            render(level)?.let { if (fits(it, bodyWidth)) return it }
        }

        if (demote) {
            level = startLevel
            guard = 0
            while (canDemoteFrom(level) && guard++ < 128) {
                level--
                render(level)?.let { if (fits(it, bodyWidth)) return it }
            }
        }
        return null
    }

    // ── presets ──────────────────────────────────────────────────

    companion object {
        @JvmField
        val SI = MetricFormat(base = 1000, mp = MetricPrefix.SI, allowOmitDecimal = true)

        @JvmField
        val IEC = MetricFormat(base = 1024, mp = MetricPrefix.IEC, allowOmitDecimal = true)

        @JvmStatic
        fun si(width: Int): MetricFormat = SI.copy(width = width, allowOmitLeadingZero = width in 1..4)

        @JvmStatic
        fun iec(width: Int): MetricFormat = IEC.copy(width = width, allowOmitLeadingZero = width in 1..4)

        @JvmStatic
        fun siFormat(value: Long): String = SI.format(value)

        @JvmStatic
        fun siFormat(value: BigInteger): String = SI.format(value)

        @JvmStatic
        fun siFormat(value: Long, width: Int): String = si(width).format(value)

        @JvmStatic
        fun siFormat(value: BigInteger, width: Int): String = si(width).format(value)

        @JvmStatic
        fun siFormat(value: Double, width: Int): String = si(width).format(value)

        @JvmStatic
        fun siFormat(value: BigDecimal, width: Int): String = si(width).format(value)

        @JvmStatic
        fun iecFormat(value: Long): String = IEC.format(value)

        @JvmStatic
        fun iecFormat(value: BigInteger): String = IEC.format(value)

        @JvmStatic
        fun iecFormat(value: Long, width: Int): String = iec(width).format(value)

        @JvmStatic
        fun iecFormat(value: BigInteger, width: Int): String = iec(width).format(value)
    }
}
