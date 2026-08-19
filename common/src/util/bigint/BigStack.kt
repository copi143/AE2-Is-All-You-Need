package allyouneed.util.bigint

import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import java.math.BigInteger

/**
 * 用 [Long] / [BigInteger] 表示的物品堆。
 * - 数量一定不为负。
 * - 当数量在 [Long] 可表示范围内时，保证一定用 [Long] 表示。
 * - 当数量超过 [Long] 范围时，保证 [Long] 值一定为 -1，[BigInteger] 存储实际大小。
 */
class BigStack private constructor(val key: AEKey, val valLong: Long, private val bigInt: BigInteger?) {
    constructor(key: AEKey, value: Long) : this(key, value, null)
    constructor(key: AEKey, value: BigInteger) : this(
        key,
        if (value.bitLength() < 64) value.toLong() else -1,
        if (value.bitLength() < 64) null else value,
    )

    init {
        assert(valLong >= 0 || (bigInt != null && bigInt > BigInteger.ZERO)) {
            "AE2 stack size is negative"
        }
    }

    val valIntSaturate: Int get() = if (valLong < 0 || valLong > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else valLong.toInt()
    val valLongSaturate: Long get() = if (valLong < 0) Long.MAX_VALUE else valLong
    val valBig: BigInteger get() = bigInt ?: BigInteger.valueOf(valLong)
    val valString: String get() = bigInt?.toString() ?: valLong.toString()
    val isZero: Boolean get() = valLong == 0L

    fun withSIze(value: Long): BigStack = BigStack(key, value, null)
    fun withSIze(value: BigInteger): BigStack = BigStack(key, value)

    operator fun plus(other: BigStack): BigStack {
        require(this.key == other.key) { "Cannot add stacks with different keys: ${this.key} vs ${other.key}" }
        return if (this.bigInt != null || other.bigInt != null) {
            BigStack(this.key, -1, this.valBig + other.valBig)
        } else {
            val v = this.valLong + other.valLong
            if (v < 0) {
                BigStack(this.key, -1, BigInteger.valueOf(this.valLong) + BigInteger.valueOf(other.valLong))
            } else {
                BigStack(this.key, v, null)
            }
        }
    }

    operator fun plus(amount: Long): BigStack {
        require(amount >= 0) { "Cannot add a negative amount; use minus" }
        if (amount == 0L) return this
        if (bigInt != null) {
            return BigStack(key, valBig + BigInteger.valueOf(amount))
        }
        val v = this.valLong + amount
        return if (v < 0) {
            BigStack(key, -1, valBig + BigInteger.valueOf(amount))
        } else {
            BigStack(key, v, null)
        }
    }

    operator fun plus(amount: BigInteger): BigStack {
        require(amount.signum() >= 0) { "Cannot add a negative amount; use minus" }
        if (amount.signum() == 0) return this
        return BigStack(key, this.valBig + amount)
    }

    operator fun minus(other: BigStack): BigStack {
        require(this.key == other.key) { "Cannot subtract stacks with different keys: ${this.key} vs ${other.key}" }
        return minus(other.valBig)
    }

    operator fun minus(amount: Long): BigStack {
        require(amount >= 0) { "Cannot subtract a negative amount; use plus" }
        if (amount == 0L) return this
        if (bigInt == null) {
            require(valLong >= amount) { "Resulting stack size would be negative" }
            return BigStack(key, valLong - amount, null)
        }
        val result = valBig - BigInteger.valueOf(amount)
        require(result.signum() >= 0) { "Resulting stack size would be negative" }
        return BigStack(key, result)
    }

    operator fun minus(amount: BigInteger): BigStack {
        require(amount.signum() >= 0) { "Cannot subtract a negative amount; use plus" }
        if (amount.signum() == 0) return this
        val result = valBig - amount
        require(result.signum() >= 0) { "Resulting stack size would be negative" }
        return BigStack(key, result)
    }

    operator fun times(scale: Long): BigStack {
        require(scale >= 0) { "Cannot multiply by a negative number." }
        return if (scale == 0L) {
            BigStack(this.key, 0, null)
        } else if (this.bigInt != null) {
            BigStack(this.key, this.valBig * BigInteger.valueOf(scale))
        } else {
            val v = runCatching { Math.multiplyExact(this.valLong, scale) }.getOrNull()
            if (v != null) {
                BigStack(this.key, v, null)
            } else {
                BigStack(this.key, BigInteger.valueOf(this.valLong) * BigInteger.valueOf(scale))
            }
        }
    }

    operator fun compareTo(other: BigStack): Int {
        require(this.key == other.key) { "Cannot compare stacks with different keys: ${this.key} vs ${other.key}" }
        return when {
            this.bigInt != null && other.bigInt != null -> this.bigInt.compareTo(other.bigInt)
            this.bigInt == null && other.bigInt != null -> -1
            this.bigInt != null && other.bigInt == null -> 1
            else -> this.valLong.compareTo(other.valLong)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigStack) return false
        if (this.key != other.key) return false
        if (this.valLong != other.valLong) return false
        if (this.bigInt != other.bigInt) return false
        return true
    }

    override fun hashCode(): Int = key.hashCode() xor if (valLong < 0) {
        bigInt.hashCode()
    } else {
        valLong.hashCode()
    }

    override fun toString(): String = "$key*$valString"

    companion object {
        @JvmStatic
        fun from(stack: GenericStack) = BigStack(stack.what, stack.amount)

        @JvmStatic
        fun from(key: AEKey, value: Long) = BigStack(key, value)

        @JvmStatic
        fun from(key: AEKey, value: BigInteger) = BigStack(key, value)
    }
}
