package allyouneed.util.bigint

import org.jetbrains.annotations.Contract
import java.math.BigInteger
import kotlin.math.pow

class U128 private constructor(private val hi: ULong, private val lo: ULong) : Number(), Comparable<U128> {
    override fun toDouble(): Double {
        return hi.toDouble() * 2.0.pow(64) + lo.toDouble()
    }

    override fun toFloat(): Float {
        return hi.toFloat() * 2.0f.pow(64) + lo.toFloat()
    }

    override fun toLong(): Long = lo.toLong()
    override fun toInt(): Int = lo.toInt()
    override fun toShort(): Short = lo.toShort()
    override fun toByte(): Byte = lo.toByte()

    @Deprecated("See [Number#toChar]", ReplaceWith("this.toInt().toChar()"))
    override fun toChar(): Char = lo.toInt().toChar()

    @Contract(pure = true)
    operator fun plus(other: U128): U128 {
        val newLo = this.lo + other.lo
        val carry = if (newLo < this.lo) 1uL else 0uL
        val newHi = this.hi + other.hi + carry
        return U128(newHi, newLo)
    }

    @Contract(pure = true)
    operator fun minus(other: U128): U128 {
        val newLo = this.lo - other.lo
        val borrow = if (this.lo < other.lo) 1uL else 0uL
        val newHi = this.hi - other.hi - borrow
        return U128(newHi, newLo)
    }

    /** 逻辑左移 (shl) */
    @Contract(pure = true)
    infix fun shl(bits: Int): U128 {
        return when (val shift = bits and 127) {
            0 -> this
            in 1..63 -> {
                val newHigh = (this.hi shl shift) or (this.lo shr (64 - shift))
                val newLow = this.lo shl shift
                U128(newHigh, newLow)
            }

            else -> {
                val newHigh = this.lo shl (shift - 64)
                U128(newHigh, 0uL)
            }
        }
    }

    /** 逻辑右移 (shr) */
    infix fun shr(bits: Int): U128 {
        return when (val shift = bits and 127) {
            0 -> this
            in 1..63 -> {
                val newLow = (this.lo shr shift) or (this.hi shl (64 - shift))
                val newHigh = this.hi shr shift
                U128(newHigh, newLow)
            }

            else -> {
                val newLow = this.hi shr (shift - 64)
                U128(0uL, newLow)
            }
        }
    }

    /** 乘法 (*) */
    @Contract(pure = true)
    operator fun times(other: U128): U128 {
        // 将低 64 位再拆分成两个 32 位以防止 64 位乘法中间结果溢出
        val a0 = this.lo and 0xFFFFFFFFuL
        val a1 = this.lo shr 32
        val b0 = other.lo and 0xFFFFFFFFuL
        val b1 = other.lo shr 32

        val p00 = a0 * b0
        val p01 = a0 * b1
        val p10 = a1 * b0
        val p11 = a1 * b1

        val middle = p01 + (p00 shr 32)
        val middleLow = (middle and 0xFFFFFFFFuL) + p10
        val carry = middleLow shr 32

        val lowRes = (middleLow shl 32) or (p00 and 0xFFFFFFFFuL)
        val highCarry = p11 + (middle shr 32) + carry

        // 高位交叉相乘，超过 128 位的高位截断忽略
        val highRes = this.hi * other.lo + this.lo * other.hi + highCarry

        return U128(highRes, lowRes)
    }

    // ==================== 2. 除法与取模 (BigInteger 快捷方式) ====================

    /** 除法 (/) */
    operator fun div(other: U128): U128 {
        require(other != MIN_VALUE) { "Division by zero" }
        val resultBI = this.toBigInteger() / other.toBigInteger()
        return from(resultBI)
    }

    /** 取模 (%) */
    operator fun rem(other: U128): U128 {
        require(other != MIN_VALUE) { "Division by zero" }
        val resultBI = this.toBigInteger() % other.toBigInteger()
        return from(resultBI)
    }

    // ==================== 3. 辅助函数 ====================

    fun toBigInteger(): BigInteger {
        val highBI = BigInteger(hi.toString())
        val lowBI = BigInteger(lo.toString())
        return (highBI shl 64) + lowBI
    }

    override fun compareTo(other: U128): Int {
        return if (this.hi != other.hi) this.hi.compareTo(other.hi) else this.lo.compareTo(other.lo)
    }

    override fun toString(): String = toBigInteger().toString()

    companion object {
        val MIN_VALUE = U128(0uL, 0uL)
        val MAX_VALUE = U128(ULong.MAX_VALUE, ULong.MAX_VALUE)

        private val MASK64 = BigInteger.ONE.shl(64).minus(BigInteger.ONE)

        /** 从 BigInteger 转换回 U128 (保留低 128 位) */
        fun from(bi: BigInteger): U128 {
            val lo = bi and MASK64
            val hi = (bi shr 64) and MASK64
            return U128(hi.toLong().toULong(), lo.toLong().toULong())
        }
    }
}
