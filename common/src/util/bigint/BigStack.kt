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
typealias BigStack = Object2BigInt<AEKey>

/**
 * 用 [Long] / [BigInteger] 表示的键值对。
 * - 数量一定不为负。
 * - 当数量在 [Long] 可表示范围内时，保证一定用 [Long] 表示。
 * - 当数量超过 [Long] 范围时，保证 [Long] 值一定为 -1，[BigInteger] 存储实际大小。
 */
open class Object2BigInt<Key> internal constructor(val key: Key, internal val valLong: Long, internal val bigInt: BigInteger?) {
    constructor(key: Key, value: Long) : this(key, value, null)
    constructor(key: Key, value: BigInteger) : this(
        key,
        if (value.bitLength() < 64) value.toLong() else -1,
        if (value.bitLength() < 64) null else value,
    )

    init {
        require(valLong >= 0 || (bigInt != null && bigInt.signum() > 0)) {
            "AE2 stack size is negative"
        }
    }

    val valIntSaturate: Int get() = if (valLong < 0 || valLong > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else valLong.toInt()
    val valLongSaturate: Long get() = if (valLong < 0) Long.MAX_VALUE else valLong
    val valBig: BigInteger get() = bigInt ?: BigInteger.valueOf(valLong)
    val valString: String get() = bigInt?.toString() ?: valLong.toString()
    val isZero: Boolean get() = valLong == 0L

    fun withSize(value: Long): Object2BigInt<Key> = Object2BigInt(key, value, null)
    fun withSize(value: BigInteger): Object2BigInt<Key> = Object2BigInt(key, value)

    open operator fun plus(other: Object2BigInt<Key>): Object2BigInt<Key> {
        require(this.key == other.key) { "Cannot add stacks with different keys: ${this.key} vs ${other.key}" }
        return if (this.bigInt != null || other.bigInt != null) {
            Object2BigInt(this.key, -1, this.valBig + other.valBig)
        } else {
            val v = this.valLong + other.valLong
            if (v < 0) {
                Object2BigInt(this.key, -1, BigInteger.valueOf(this.valLong) + BigInteger.valueOf(other.valLong))
            } else {
                Object2BigInt(this.key, v, null)
            }
        }
    }

    open operator fun plus(amount: Long): Object2BigInt<Key> {
        require(amount >= 0) { "Cannot add a negative amount; use minus" }
        if (amount == 0L) return this
        if (bigInt != null) {
            return Object2BigInt(key, valBig + BigInteger.valueOf(amount))
        }
        val v = this.valLong + amount
        return if (v < 0) {
            Object2BigInt(key, -1, valBig + BigInteger.valueOf(amount))
        } else {
            Object2BigInt(key, v, null)
        }
    }

    open operator fun plus(amount: BigInteger): Object2BigInt<Key> {
        require(amount.signum() >= 0) { "Cannot add a negative amount; use minus" }
        if (amount.signum() == 0) return this
        return Object2BigInt(key, this.valBig + amount)
    }

    open operator fun minus(other: Object2BigInt<Key>): Object2BigInt<Key> {
        require(this.key == other.key) { "Cannot subtract stacks with different keys: ${this.key} vs ${other.key}" }
        return minus(other.valBig)
    }

    open operator fun minus(amount: Long): Object2BigInt<Key> {
        require(amount >= 0) { "Cannot subtract a negative amount; use plus" }
        if (amount == 0L) return this
        if (bigInt == null) {
            require(valLong >= amount) { "Resulting stack size would be negative" }
            return Object2BigInt(key, valLong - amount, null)
        }
        val result = valBig - BigInteger.valueOf(amount)
        require(result.signum() >= 0) { "Resulting stack size would be negative" }
        return Object2BigInt(key, result)
    }

    open operator fun minus(amount: BigInteger): Object2BigInt<Key> {
        require(amount.signum() >= 0) { "Cannot subtract a negative amount; use plus" }
        if (amount.signum() == 0) return this
        val result = valBig - amount
        require(result.signum() >= 0) { "Resulting stack size would be negative" }
        return Object2BigInt(key, result)
    }

    open operator fun times(scale: Long): Object2BigInt<Key> {
        require(scale >= 0) { "Cannot multiply by a negative number." }
        return if (scale == 0L) {
            Object2BigInt(this.key, 0, null)
        } else if (this.bigInt != null) {
            Object2BigInt(this.key, this.valBig * BigInteger.valueOf(scale))
        } else {
            val v = runCatching { Math.multiplyExact(this.valLong, scale) }.getOrNull()
            if (v != null) {
                Object2BigInt(this.key, v, null)
            } else {
                Object2BigInt(this.key, BigInteger.valueOf(this.valLong) * BigInteger.valueOf(scale))
            }
        }
    }

    operator fun compareTo(other: Object2BigInt<Key>): Int {
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
        if (other !is Object2BigInt<Key>) return false
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
        fun from(stack: GenericStack) = Object2BigInt(stack.what, stack.amount)

        @JvmStatic
        fun from(key: AEKey, value: Long) = Object2BigInt(key, value)

        @JvmStatic
        fun from(key: AEKey, value: BigInteger) = Object2BigInt(key, value)
    }
}
