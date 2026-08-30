package allyouneed.util.bigint

import allyouneed.util.saturateToInt
import allyouneed.util.saturateToLong
import org.jetbrains.annotations.Contract
import java.math.BigInteger

/**
 * 为非负整数优化的三档计数器。
 *
 * - 大多数数据 < 2^64 (fits in unsigned [ULong]) → 仅用 [lo]，[hi]==0u，[bi]==null，最快路径
 * - 部分数据 < 2^128 (fits in unsigned 128) → 用 [lo]+[hi] (各存 64 位无符号)，[bi]==null
 * - 极少数 ≥ 2^128 → 退化到 [BigInteger]，[lo]/[hi] 置 [ULong.MAX_VALUE] 哨兵
 *
 * 不变量：
 * - [bi]==null 时，数值 == unsigned128(hi, lo)
 * - [bi]!=null 时，数值 == bi (≥ 2^128 且 ≥0)
 * - 构造始终归一化：能用 128 位表示的值绝不保留 [bi]
 */
open class Counter internal constructor(
    internal val lo: ULong,
    internal val hi: ULong,
    internal val bi: BigInteger?,
) : Number(), Comparable<Counter> {

    constructor(value: Int) : this(value.toULong(), 0UL, null) {
        require(value >= 0) { "Try constructing `${Counter::class.qualifiedName}` using negative numbers." }
    }

    constructor(value: Long) : this(value.toULong(), 0UL, null) {
        require(value >= 0) { "Try constructing `${Counter::class.qualifiedName}` using negative numbers." }
    }

    constructor(value: BigInteger) : this(
        if (value.bitLength() <= 64) value.toLong().toULong() else ULong.MAX_VALUE,
        if (value.bitLength() <= 128) (value.shiftRight(64)).toLong().toULong() else ULong.MAX_VALUE,
        if (value.bitLength() <= 128) null else value,
    ) {
        require(value.signum() >= 0) { "Try constructing `${Counter::class.qualifiedName}` using negative numbers." }
    }

    override fun toDouble(): Double = toBigInteger().toDouble()
    override fun toFloat(): Float = toBigInteger().toFloat()
    override fun toLong(): Long = bi?.toLong() ?: lo.toLong()
    override fun toInt(): Int = bi?.toInt() ?: lo.toInt()
    override fun toShort(): Short = lo.toShort()
    override fun toByte(): Byte = lo.toByte()

    @Deprecated("See [Number#toChar]", ReplaceWith("this.toInt().toChar()"))
    override fun toChar(): Char = lo.toInt().toChar()

    val isBig: Boolean get() = bi != null
    val isU128: Boolean get() = bi == null && hi != 0uL
    val isU64: Boolean get() = bi == null && hi == 0uL
    val isLong: Boolean get() = bi == null && hi == 0uL && lo <= Long.MAX_VALUE.toULong()

    val isZero: Boolean get() = (bi == null && hi == 0uL && lo == 0uL) || (bi != null && bi.signum() == 0)
    val isOne: Boolean get() = bi == null && hi == 0uL && lo == 1uL
    val signum: Int get() = if (isZero) 0 else 1

    /** 0..∞，与 [BigInteger.bitLength] 一致 */
    val bitLength: Int
        get() {
            bi?.let { return it.bitLength() }
            if (hi != 0uL) {
                return 64 + unsignedBitLength(hi)
            }
            return unsignedBitLength(lo)
        }

    // ── 转换 ───────────────────────────────────────────────────
    fun toBigInteger(): BigInteger {
        bi?.let { return it }
        if (hi == 0uL && lo == 0uL) return BigInteger.ZERO
        if (hi == 0uL) return lo.toBigInteger()
        return (hi.toBigInteger().shiftLeft(64).or(lo.toBigInteger()))
    }

    fun toU128(): U128 = U128.from(toBigInteger().and(MAX_U128_BI))

    fun toULong(): ULong = lo
    fun toUnsignedLong(): ULong = lo

    val longSaturated: Long
        get() = when {
            bi != null -> bi.saturateToLong()
            hi != 0uL -> Long.MAX_VALUE
            lo > Long.MAX_VALUE.toULong() -> Long.MAX_VALUE
            else -> lo.toLong()
        }

    val intSaturated: Int
        get() = when {
            bi != null -> bi.saturateToInt()
            hi != 0uL -> Int.MAX_VALUE
            lo > Int.MAX_VALUE.toULong() -> Int.MAX_VALUE
            else -> lo.toInt()
        }

    val stringValue: String get() = bi?.toString() ?: toBigInteger().toString()

    // ── 比较 ───────────────────────────────────────────────────
    override fun compareTo(other: Counter): Int {
        if (this === other) return 0
        val aBig = this.bi
        val bBig = other.bi
        if (aBig != null || bBig != null) {
            if (aBig != null && bBig != null) return aBig.compareTo(bBig)
            if (aBig != null) return 1 // big ≥2^128 > any U128
            return -1
        }
        // both U128
        val hiCmp = hi.compareTo(other.hi)
        if (hiCmp != 0) return hiCmp
        return lo.compareTo(other.lo)
    }

    fun compareTo(other: Long): Int {
        require(other >= 0) { "Counter is non-negative" }
        return compareTo(of(other))
    }

    fun compareTo(other: BigInteger): Int {
        require(other.signum() >= 0)
        return toBigInteger().compareTo(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Counter) return false
        if (this.bi != null || other.bi != null) {
            return this.toBigInteger() == other.toBigInteger()
        }
        return this.lo == other.lo && this.hi == other.hi
    }

    override fun hashCode(): Int {
        bi?.let { return it.hashCode() }
        var h = hi.hashCode()
        h = 31 * h + lo.hashCode()
        return h
    }

    override fun toString(): String = stringValue

    // ── 加法 ───────────────────────────────────────────────────
    @Contract(pure = true)
    open operator fun plus(other: Counter): Counter {
        if (this.isZero) return other
        if (other.isZero) return this
        if (this.bi != null || other.bi != null) {
            return of(toBigInteger().add(other.toBigInteger()))
        }
        // fast U64+U64
        if (this.hi == 0uL && other.hi == 0uL) {
            val a = this.lo
            val b = other.lo
            val sum = a + b
            val carry = if (sum < a) 1uL else 0uL
            if (carry == 0uL) {
                return Counter(sum, 0uL, null)
            }
            return Counter(sum, 1uL, null)
        }
        // general U128 add with overflow detection
        val newLo = lo + other.lo
        val carry = if (newLo < lo) 1uL else 0uL
        val newHi = hi + other.hi + carry
        val overflow = newHi < hi || newHi < other.hi
        if (overflow) {
            return of(toBigInteger().add(other.toBigInteger()))
        }
        return Counter(newLo, newHi, null)
    }

    open operator fun plus(amount: Long): Counter {
        require(amount >= 0) { "Cannot add negative amount" }
        if (amount == 0L) return this
        return plus(of(amount))
    }

    open operator fun plus(amount: ULong): Counter {
        if (amount == 0uL) return this
        return plus(of(amount))
    }

    open operator fun plus(amount: BigInteger): Counter {
        require(amount.signum() >= 0)
        if (amount.signum() == 0) return this
        return of(toBigInteger().add(amount))
    }

    // ── 减法 (结果必须 ≥0) ───────────────────────────────────
    @Contract(pure = true)
    open operator fun minus(other: Counter): Counter {
        if (other.isZero) return this
        require(compareTo(other) >= 0) { "Result would be negative: $this - $other" }
        if (this.bi != null || other.bi != null) {
            return of(toBigInteger().subtract(other.toBigInteger()))
        }
        val newLo = lo - other.lo
        val borrow = if (lo < other.lo) 1uL else 0uL
        val newHi = hi - other.hi - borrow
        return Counter(newLo, newHi, null).normalized()
    }

    open operator fun minus(amount: Long): Counter {
        require(amount >= 0)
        if (amount == 0L) return this
        return minus(of(amount))
    }

    open operator fun minus(amount: ULong): Counter {
        if (amount == 0uL) return this
        return minus(of(amount))
    }

    open operator fun minus(amount: BigInteger): Counter {
        require(amount.signum() >= 0)
        if (amount.signum() == 0) return this
        val res = toBigInteger().subtract(amount)
        require(res.signum() >= 0) { "Result would be negative" }
        return of(res)
    }

    // ── 乘法 ───────────────────────────────────────────────────
    @Contract(pure = true)
    open operator fun times(other: Counter): Counter {
        if (this.isZero || other.isZero) return ZERO
        if (this.isOne) return other
        if (other.isOne) return this
        if (this.bi != null || other.bi != null) {
            return of(toBigInteger().multiply(other.toBigInteger()))
        }
        return of(toBigInteger().multiply(other.toBigInteger()))
    }

    open operator fun times(scale: Long): Counter {
        require(scale >= 0)
        if (scale == 0L) return ZERO
        if (scale == 1L) return this
        if (this.bi != null) return of(toBigInteger().multiply(BigInteger.valueOf(scale)))
        if (this.hi == 0uL && this.lo <= Long.MAX_VALUE.toULong()) {
            val v = runCatching { Math.multiplyExact(this.lo.toLong(), scale) }.getOrNull()
            if (v != null) return Counter(v.toULong(), 0uL, null)
        }
        return of(toBigInteger().multiply(BigInteger.valueOf(scale)))
    }

    open operator fun times(scale: ULong): Counter {
        if (scale == 0uL) return ZERO
        if (scale == 1uL) return this
        return of(toBigInteger().multiply(scale.toBigInteger()))
    }

    open operator fun times(scale: BigInteger): Counter {
        require(scale.signum() >= 0)
        if (scale.signum() == 0) return ZERO
        if (scale == BigInteger.ONE) return this
        return of(toBigInteger().multiply(scale))
    }

    // ── 除法 / 取模 ───────────────────────────────────────────
    @Contract(pure = true)
    open operator fun div(other: Counter): Counter {
        require(!other.isZero) { "Division by zero" }
        if (this.compareTo(other) < 0) return ZERO
        if (other.isOne) return this
        return of(toBigInteger().divide(other.toBigInteger()))
    }

    open operator fun div(other: Long): Counter {
        require(other > 0) { "Division by zero or negative" }
        if (isZero) return ZERO
        if (other == 1L) return this
        return of(toBigInteger().divide(BigInteger.valueOf(other)))
    }

    open operator fun rem(other: Counter): Counter {
        require(!other.isZero) { "Division by zero" }
        if (this.compareTo(other) < 0) return this
        return of(toBigInteger().remainder(other.toBigInteger()))
    }

    open operator fun rem(other: Long): Counter {
        require(other > 0)
        if (isZero) return ZERO
        return of(toBigInteger().remainder(BigInteger.valueOf(other)))
    }

    open infix fun divide(other: Counter): Counter = div(other)
    open infix fun mod(other: Counter): Counter = rem(other)

    // ── 位移 (逻辑) ──────────────────────────────────────────
    infix fun shl(bits: Int): Counter {
        if (bits == 0 || isZero) return this
        require(bits >= 0) { "Shift amount must be non-negative" }
        if (bits >= 1024 * 16) {
            return of(toBigInteger().shiftLeft(bits))
        }
        if (bi == null) {
            if (bits < 128) {
                val shift = bits
                val result: Pair<ULong, ULong> = when {
                    shift >= 128 -> 0uL to 0uL
                    shift >= 64 -> (lo shl (shift - 64)) to 0uL
                    shift == 0 -> hi to lo
                    else -> {
                        val newHi = (hi shl shift) or (lo shr (64 - shift))
                        val newLo = lo shl shift
                        newHi to newLo
                    }
                }
                val overflow = when {
                    shift >= 128 -> hi != 0uL || lo != 0uL
                    shift >= 64 -> hi != 0uL || (shift > 64 && lo shr (128 - shift) != 0uL)
                    else -> hi shr (64 - shift) != 0uL
                }
                if (!overflow) {
                    return Counter(result.second, result.first, null).normalized()
                }
            }
        }
        return of(toBigInteger().shiftLeft(bits))
    }

    infix fun shr(bits: Int): Counter {
        if (bits == 0 || isZero) return this
        require(bits >= 0)
        if (bi != null) return of(toBigInteger().shiftRight(bits))
        if (bits >= 128) return ZERO
        return when {
            bits >= 64 -> {
                val newLo = hi shr (bits - 64)
                Counter(newLo, 0uL, null)
            }

            else -> {
                val newLo = (lo shr bits) or (hi shl (64 - bits))
                val newHi = hi shr bits
                Counter(newLo, newHi, null).normalized()
            }
        }
    }

    // ── 其他工具 ───────────────────────────────────────────────
    fun pow(exp: Int): Counter {
        require(exp >= 0) { "Negative exponent" }
        if (exp == 0) return ONE
        if (exp == 1) return this
        if (isZero) return ZERO
        return of(toBigInteger().pow(exp))
    }

    fun and(other: Counter): Counter = of(toBigInteger().and(other.toBigInteger()))
    fun or(other: Counter): Counter = of(toBigInteger().or(other.toBigInteger()))
    fun xor(other: Counter): Counter = of(toBigInteger().xor(other.toBigInteger()))
    fun notWithinWidth(width: Int): Counter {
        val biVal = toBigInteger()
        val mask = if (width >= 1024 * 16) biVal.not()
        else biVal.xor(BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE))
        return of(mask)
    }

    fun coerceAtLeast(min: Counter): Counter = if (compareTo(min) < 0) min else this
    fun coerceAtMost(max: Counter): Counter = if (compareTo(max) > 0) max else this
    fun coerceIn(min: Counter, max: Counter): Counter = coerceAtLeast(min).coerceAtMost(max)
    fun max(other: Counter): Counter = if (compareTo(other) >= 0) this else other
    fun min(other: Counter): Counter = if (compareTo(other) <= 0) this else other

    fun isPowerOfTwo(): Boolean {
        val biVal = toBigInteger()
        return biVal.signum() > 0 && biVal.and(biVal.subtract(BigInteger.ONE)) == BigInteger.ZERO
    }

    private fun normalized(): Counter {
        if (bi != null) return this
        return this
    }

    companion object {
        operator fun invoke(value: UInt) = Counter(value.toULong(), 0UL, null)
        operator fun invoke(value: ULong) = Counter(value, 0UL, null)

        @JvmField
        val ZERO: Counter = Counter(0uL, 0uL, null)

        @JvmField
        val ONE: Counter = Counter(1uL, 0uL, null)

        @JvmField
        val TWO: Counter = Counter(2uL, 0uL, null)

        @JvmField
        val MAX_U64: Counter = Counter(ULong.MAX_VALUE, 0uL, null)

        @JvmField
        val MAX_U128: Counter = Counter(ULong.MAX_VALUE, ULong.MAX_VALUE, null)

        private val MASK64: BigInteger = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
        private val MAX_U128_BI: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

        private fun unsignedBitLength(v: ULong): Int =
            if (v == 0uL) 0 else 64 - java.lang.Long.numberOfLeadingZeros(v.toLong())

        private fun ULong.toBigInteger(): BigInteger =
            if (this <= Long.MAX_VALUE.toULong()) BigInteger.valueOf(this.toLong())
            else BigInteger(this.toString())

        // ── 工厂 ───────────────────────────────────────────────
        @JvmStatic
        fun of(value: Long): Counter {
            require(value >= 0) { "Counter requires non-negative Long, got $value" }
            return Counter(value.toULong(), 0uL, null)
        }

        @JvmStatic
        fun of(value: ULong): Counter = Counter(value, 0uL, null)

        @JvmStatic
        fun of(value: UInt): Counter = Counter(value.toULong(), 0uL, null)

        @JvmStatic
        fun of(value: Int): Counter {
            require(value >= 0) { "Counter requires non-negative Int, got $value" }
            return Counter(value.toULong(), 0uL, null)
        }

        @JvmStatic
        fun of(value: BigInteger): Counter {
            require(value.signum() >= 0) { "Counter requires non-negative BigInteger, got $value" }
            if (value.signum() == 0) return ZERO
            val bl = value.bitLength()
            if (bl <= 64) {
                // 保留低 64 位无符号
                return Counter(value.and(MASK64).toLong().toULong(), 0uL, null)
            }
            if (bl <= 128) {
                val lo = value.and(MASK64).toLong().toULong()
                val hi = value.shiftRight(64).and(MASK64).toLong().toULong()
                return Counter(lo, hi, null)
            }
            return Counter(ULong.MAX_VALUE, ULong.MAX_VALUE, value)
        }

        @JvmStatic
        fun of(value: U128): Counter = of(value.toBigInteger())

        @JvmStatic
        fun of(value: String): Counter = of(BigInteger(value))

        @JvmStatic
        fun of(value: String, radix: Int): Counter = of(BigInteger(value, radix))

        @JvmStatic
        fun valueOf(value: Long): Counter = of(value)

        @JvmStatic
        fun valueOf(value: BigInteger): Counter = of(value)

        @JvmStatic
        fun fromRaw(lo: ULong, hi: ULong): Counter {
            if (hi == 0uL && lo == 0uL) return ZERO
            return Counter(lo, hi, null)
        }

        @JvmStatic
        fun fromU64(lo: ULong, hi: ULong = 0uL): Counter {
            if (hi == 0uL && lo == 0uL) return ZERO
            return Counter(lo, hi, null)
        }

        @JvmStatic
        fun fromLongs(loLong: Long, hiLong: Long, bi: BigInteger?): Counter {
            if (bi != null) return Counter(loLong.toULong(), hiLong.toULong(), bi)
            val lo = loLong.toULong()
            val hi = hiLong.toULong()
            if (hi == 0UL && lo == 0UL) return ZERO
            return Counter(lo, hi, null)
        }

        @JvmStatic
        fun parse(value: String): Counter = of(value)
    }
}
