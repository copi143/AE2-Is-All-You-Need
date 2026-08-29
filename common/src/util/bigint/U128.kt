package allyouneed.util.bigint

import allyouneed.util.unsignedMultiplyHigh
import org.jetbrains.annotations.Contract
import java.math.BigInteger
import kotlin.math.pow

/**
 * 无符号 128 位整数，内部以两个 [ULong] 表示：`value == (hi shl 64) or lo`。
 *
 * 所有算术均为 mod 2^128 环绕语义（无溢出抛出），位移采用 `bits and 127` 掩码环绕，
 * 与原生 `Long`/`ULong` 的位移行为一致。
 */
class U128(internal val lo: ULong, internal val hi: ULong) : Number(), Comparable<U128> {
    override fun toDouble(): Double = hi.toDouble() * POW2_64D + lo.toDouble()
    override fun toFloat(): Float = hi.toFloat() * POW2_64F + lo.toFloat()
    override fun toLong(): Long = lo.toLong()
    override fun toInt(): Int = lo.toInt()
    override fun toShort(): Short = lo.toShort()
    override fun toByte(): Byte = lo.toByte()

    @Deprecated("See [Number#toChar]", ReplaceWith("this.toInt().toChar()"))
    override fun toChar(): Char = lo.toInt().toChar()

    val isZero: Boolean get() = lo == 0UL && hi == 0UL
    val isOne: Boolean get() = lo == 1UL && hi == 0UL
    val signum: Int get() = if (isZero) 0 else 1

    @Contract(pure = true)
    operator fun plus(other: U128): U128 {
        val newLo = this.lo + other.lo
        val carry = if (newLo < this.lo) 1uL else 0uL
        return U128(newLo, this.hi + other.hi + carry)
    }

    @Contract(pure = true)
    operator fun minus(other: U128): U128 {
        val newLo = this.lo - other.lo
        val borrow = if (this.lo < other.lo) 1uL else 0uL
        return U128(newLo, this.hi - other.hi - borrow)
    }

    @Contract(pure = true)
    infix fun shl(bits: Int): U128 {
        return when (val shift = bits and 127) {
            0 -> this
            in 1..63 -> {
                val newLow = this.lo shl shift
                val newHigh = (this.hi shl shift) or (this.lo shr (64 - shift))
                U128(newLow, newHigh)
            }

            else -> U128(0uL, this.lo shl (shift - 64)) // 64..127，低 64 位全部移出
        }
    }

    @Contract(pure = true)
    infix fun shr(bits: Int): U128 {
        return when (val shift = bits and 127) {
            0 -> this
            in 1..63 -> {
                val newLow = (this.lo shr shift) or (this.hi shl (64 - shift))
                U128(newLow, this.hi shr shift)
            }

            else -> U128(this.hi shr (shift - 64), 0uL) // 64..127，高 64 位全部移出
        }
    }

    @Contract(pure = true)
    operator fun times(other: U128): U128 {
        val newLo = this.lo * other.lo
        val newHi = unsignedMultiplyHigh(this.lo, other.lo)
        val cross = this.hi * other.lo + this.lo * other.hi
        return U128(newLo, newHi + cross)
    }

    @Contract(pure = true)
    infix fun and(other: U128): U128 = U128(lo and other.lo, hi and other.hi)

    @Contract(pure = true)
    infix fun or(other: U128): U128 = U128(lo or other.lo, hi or other.hi)

    @Contract(pure = true)
    infix fun xor(other: U128): U128 = U128(lo xor other.lo, hi xor other.hi)

    @Contract(pure = true)
    fun inv(): U128 = U128(lo.inv(), hi.inv())

    @Contract(pure = true)
    fun numberOfLeadingZeros(): Int = if (hi == 0UL) 64 + lo.countLeadingZeroBits() else hi.countLeadingZeroBits()

    @Contract(pure = true)
    fun bitLength(): Int = if (isZero) 0 else 128 - numberOfLeadingZeros()

    /** 原生实现：恢复式长除法，同时返回商和余数。 */
    @Contract(pure = true)
    fun divmod(other: U128): Pair<U128, U128> {
        if (other.isZero) throw ArithmeticException("Division by zero")
        if (this < other) return MIN_VALUE to this
        if (other.isOne) return this to MIN_VALUE
        if (hi == 0UL && other.hi == 0UL) {
            return U128(lo / other.lo, 0UL) to U128(lo % other.lo, 0UL)
        }
        val shift = bitLength() - other.bitLength()
        var remainder = this
        var quotient = MIN_VALUE
        var divisor = other shl shift
        var bit = ONE shl shift
        while (!bit.isZero) {
            if (remainder >= divisor) {
                remainder -= divisor
                quotient = quotient or bit
            }
            divisor = divisor shr 1
            bit = bit shr 1 // 逐位下移到下一个商位
        }
        return quotient to remainder
    }

    @Contract(pure = true)
    operator fun div(other: U128): U128 = divmod(other).first

    @Contract(pure = true)
    operator fun rem(other: U128): U128 = divmod(other).second

    @Contract(pure = true)
    fun toBigInteger(): BigInteger = hi.toUnsignedBigInteger().shiftLeft(64).or(lo.toUnsignedBigInteger())

    @Contract(pure = true)
    override fun compareTo(other: U128): Int {
        return if (this.hi != other.hi) this.hi.compareTo(other.hi) else this.lo.compareTo(other.lo)
    }

    @Contract(pure = true)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is U128) return false
        return lo == other.lo && hi == other.hi
    }

    @Contract(pure = true)
    override fun hashCode(): Int = 31 * hi.hashCode() + lo.hashCode()

    @Contract(pure = true)
    override fun toString(): String = toBigInteger().toString()

    companion object {
        val MIN_VALUE: U128 = U128(ULong.MIN_VALUE, ULong.MIN_VALUE)
        val MAX_VALUE: U128 = U128(ULong.MAX_VALUE, ULong.MAX_VALUE)
        const val SIZE_BYTES: Int = 16
        const val SIZE_BITS: Int = 128

        val ZERO: U128 = U128(0UL, 0UL)
        val ONE: U128 = U128(1UL, 0UL)

        private val MASK64 = BigInteger.ONE.shl(64).minus(BigInteger.ONE)
        private val POW2_64 = BigInteger.ONE.shiftLeft(64)
        private val POW2_64F = 2.0f.pow(64)
        private val POW2_64D = 2.0.pow(64)

        /** 无符号 64 位整数的正 [BigInteger]，避免字符串解析。 */
        private fun ULong.toUnsignedBigInteger(): BigInteger {
            val signed = BigInteger.valueOf(toLong())
            return if (signed.signum() >= 0) signed else signed.add(POW2_64)
        }

        /** 从 [BigInteger] 转换，保留低 128 位。 */
        fun from(bi: BigInteger): U128 {
            val lo = bi and MASK64
            val hi = (bi shr 64) and MASK64
            return U128(lo.toLong().toULong(), hi.toLong().toULong())
        }
    }
}
