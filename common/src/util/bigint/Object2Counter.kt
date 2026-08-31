package allyouneed.util.bigint

import java.math.BigInteger

/**
 * 用 [Long] / [java.math.BigInteger] 表示的键值对。
 * - 数量一定不为负。
 * - 当数量在 [Long] 可表示范围内时，保证一定用 [Long] 表示。
 * - 当数量超过 [Long] 范围时，保证 [Long] 值一定为 -1，[java.math.BigInteger] 存储实际大小。
 */
open class Object2Counter<Key> internal constructor(
    val key: Key,
    lo: ULong,
    hi: ULong,
    bi: BigInteger?,
) : Counter(lo, hi, bi) {
    companion object {
        operator fun <Key> invoke(key: Key, value: UInt) = Object2Counter(key, value.toULong(), 0UL, null)
        operator fun <Key> invoke(key: Key, value: ULong) = Object2Counter(key, value, 0UL, null)
    }

    constructor(key: Key, value: Counter) : this(key, value.lo, value.hi, value.bi)

    constructor(key: Key, value: Int) : this(key, value.toULong(), 0UL, null) {
        require(value >= 0) { "Try constructing `${Object2Counter::class.qualifiedName}` using negative numbers." }
    }

    constructor(key: Key, value: Long) : this(key, value.toULong(), 0UL, null) {
        require(value >= 0) { "Try constructing `${Object2Counter::class.qualifiedName}` using negative numbers." }
    }

    constructor(key: Key, value: BigInteger) : this(
        key,
        if (value.bitLength() <= 64) value.toLong().toULong() else ULong.MAX_VALUE,
        if (value.bitLength() <= 128) (value.shiftRight(64)).toLong().toULong() else ULong.MAX_VALUE,
        if (value.bitLength() <= 128) null else value,
    ) {
        require(value.signum() >= 0) { "Try constructing `${Object2Counter::class.qualifiedName}` using negative numbers." }
    }

    fun withSize(value: Counter): Object2Counter<Key> = Object2Counter(key, value)
    fun withSize(value: UInt): Object2Counter<Key> = Object2Counter(key, value)
    fun withSize(value: ULong): Object2Counter<Key> = Object2Counter(key, value)
    fun withSize(value: Int): Object2Counter<Key> = Object2Counter(key, value)
    fun withSize(value: Long): Object2Counter<Key> = Object2Counter(key, value)
    fun withSize(value: BigInteger): Object2Counter<Key> = Object2Counter(key, value)

    val valBig: BigInteger get() = toBigInteger()

    open operator fun plus(other: Object2Counter<Key>): Object2Counter<Key> {
        require(this.key == other.key) { "Cannot add stacks with different keys: ${this.key} vs ${other.key}" }
        return Object2Counter(key, (this as Counter) + (other as Counter))
    }

    override operator fun plus(other: Counter): Object2Counter<Key> {
        return Object2Counter(key, (this as Counter) + other)
    }

    override operator fun plus(amount: Long): Object2Counter<Key> {
        require(amount >= 0) { "Cannot add a negative amount; use minus" }
        return Object2Counter(key, (this as Counter) + amount)
    }

    override operator fun plus(amount: BigInteger): Object2Counter<Key> {
        require(amount.signum() >= 0) { "Cannot add a negative amount; use minus" }
        if (amount.signum() == 0) return this
        return Object2Counter(key, this.valBig + amount)
    }

    open operator fun minus(other: Object2Counter<Key>): Object2Counter<Key> {
        require(this.key == other.key) { "Cannot subtract stacks with different keys: ${this.key} vs ${other.key}" }
        return minus(other.valBig)
    }

    override operator fun minus(amount: Long): Object2Counter<Key> {
        require(amount >= 0) { "Cannot subtract a negative amount; use plus" }
        return Object2Counter(key, (this as Counter) - amount)
    }

    override operator fun minus(amount: BigInteger): Object2Counter<Key> {
        require(amount.signum() >= 0) { "Cannot subtract a negative amount; use plus" }
        return Object2Counter(key, (this as Counter) - amount)
    }

    override operator fun times(scale: Long): Object2Counter<Key> {
        require(scale >= 0) { "Cannot multiply by a negative number." }
        return Object2Counter(key, (this as Counter) * scale)
    }

    operator fun compareTo(other: Object2Counter<Key>): Int {
        require(this.key == other.key) { "Cannot compare stacks with different keys: ${this.key} vs ${other.key}" }
        return (this as Counter).compareTo(other as Counter)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Object2Counter<Key>) return false
        if (this.key != other.key) return false
        // (this as Counter) == other counter would virtual-dispatch back here and overflow the stack.
        return super.equals(other)
    }

    override fun hashCode(): Int {
        // (this as Counter).hashCode() would virtual-dispatch back here and overflow the stack.
        val valueHash = if (bi != null) bi.hashCode() else 31 * hi.hashCode() + lo.hashCode()
        return (key?.hashCode() ?: 0) xor valueHash
    }

    override fun toString(): String = "$key*$stringValue"
}
