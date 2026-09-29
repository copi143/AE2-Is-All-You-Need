@file:Suppress("NOTHING_TO_INLINE", "unused")

package allyouneed.util

import java.math.BigInteger

/** 浮点数的指数部分 */
inline val Float.floatingExp get() = ((this.toBits() ushr 23) and 0xFF) - 127

/** 浮点数的指数部分 */
inline val Double.floatingExp get() = ((this.toBits() ushr 52).toInt() and 0x7FF) - 1023

inline fun BigInteger.saturateToLong(): Long {
    if (this.signum() < 0) return 0L
    if (this.bitLength() > 63) return Long.MAX_VALUE
    return this.toLong()
}

inline fun BigInteger.saturateToInt(): Int {
    if (this.signum() < 0) return 0
    if (this.bitLength() > 31) return Int.MAX_VALUE
    return this.toInt()
}

/** 饱和加法：a + b */
inline infix fun Int.satAdd(other: Int): Int {
    val res = this + other
    val overflow = ((this xor res) and (other xor res)) < 0
    return if (overflow) {
        if (this < 0) Int.MIN_VALUE else Int.MAX_VALUE
    } else res
}

/** 饱和加法：a + b */
inline infix fun Long.satAdd(other: Long): Long {
    val res = this + other
    val overflow = ((this xor res) and (other xor res)) < 0L
    return if (overflow) {
        if (this < 0L) Long.MIN_VALUE else Long.MAX_VALUE
    } else res
}

/** 饱和加法：a + b */
inline infix fun UByte.satAdd(other: UByte): UByte {
    val res = (this + other).toUByte()
    return if (res < this) UByte.MAX_VALUE else res
}

/** 饱和加法：a + b */
inline infix fun UShort.satAdd(other: UShort): UShort {
    val res = (this + other).toUShort()
    return if (res < this) UShort.MAX_VALUE else res
}

/** 饱和加法：a + b */
inline infix fun UInt.satAdd(other: UInt): UInt {
    val res = this + other
    return if (res < this) UInt.MAX_VALUE else res
}

/** 饱和加法：a + b */
inline infix fun ULong.satAdd(other: ULong): ULong {
    val res = this + other
    return if (res < this) ULong.MAX_VALUE else res
}

/** 饱和减法：a - b */
inline infix fun Int.satSub(other: Int): Int {
    val res = this - other
    val overflow = ((this xor other) and (this xor res)) < 0
    return if (overflow) {
        if (this < 0) Int.MIN_VALUE else Int.MAX_VALUE
    } else res
}

/** 饱和减法：a - b */
inline infix fun Long.satSub(other: Long): Long {
    val res = this - other
    val overflow = ((this xor other) and (this xor res)) < 0L
    return if (overflow) {
        if (this < 0L) Long.MIN_VALUE else Long.MAX_VALUE
    } else res
}

/** 饱和减法：a - b */
inline infix fun UByte.satSub(other: UByte): UByte {
    return if (this < other) UByte.MIN_VALUE else (this - other).toUByte()
}

/** 饱和减法：a - b */
inline infix fun UShort.satSub(other: UShort): UShort {
    return if (this < other) UShort.MIN_VALUE else (this - other).toUShort()
}

/** 饱和减法：a - b */
inline infix fun UInt.satSub(other: UInt): UInt {
    return if (this < other) UInt.MIN_VALUE else this - other
}

/** 饱和减法：a - b */
inline infix fun ULong.satSub(other: ULong): ULong {
    return if (this < other) ULong.MIN_VALUE else this - other
}

/** 饱和乘法：a * b */
inline infix fun Int.satMul(other: Int): Int {
    val res = this.toLong() * other.toLong()
    return when {
        res > Int.MAX_VALUE -> Int.MAX_VALUE
        res < Int.MIN_VALUE -> Int.MIN_VALUE
        else -> res.toInt()
    }
}

/** 饱和乘法：a * b */
inline infix fun Long.satMul(other: Long): Long {
    if (this == 0L || other == 0L) return 0L
    val res = this * other
    val overflow = (this == Long.MIN_VALUE && other == -1L) || (res / other != this)
    return if (overflow) {
        if ((this xor other) < 0L) Long.MIN_VALUE else Long.MAX_VALUE
    } else res
}

/** 饱和乘法：a * b */
inline infix fun UInt.satMul(other: UInt): UInt {
    val res = this.toULong() * other.toULong()
    return if (res > UInt.MAX_VALUE.toULong()) UInt.MAX_VALUE else res.toUInt()
}

/** 饱和乘法：a * b */
inline infix fun ULong.satMul(other: ULong): ULong {
    if (this == 0UL || other == 0UL) return 0UL
    val res = this * other
    return if (res / this != other) ULong.MAX_VALUE else res
}
