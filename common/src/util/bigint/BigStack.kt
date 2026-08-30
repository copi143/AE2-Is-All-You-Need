package allyouneed.util.bigint

import appeng.api.stacks.AEKey
import java.math.BigInteger

/**
 * 用 [Long] / [BigInteger] 表示的物品堆。
 * - 数量一定不为负。
 * - 当数量在 [Long] 可表示范围内时，保证一定用 [Long] 表示。
 * - 当数量超过 [Long] 范围时，保证 [Long] 值一定为 -1，[BigInteger] 存储实际大小。
 */
class BigStack internal constructor(key: AEKey, lo: ULong, hi: ULong, bi: BigInteger?) :
    Object2Counter<AEKey>(key, lo, hi, bi) {
    constructor(key: AEKey, value: Counter) : this(key, value.lo, value.hi, value.bi)
    constructor(key: AEKey, value: UInt) : this(key, value.toULong(), 0UL, null)
    constructor(key: AEKey, value: ULong) : this(key, value, 0UL, null)

    constructor(key: AEKey, value: Int) : this(key, value.toULong(), 0UL, null) {
        require(value >= 0) { "AE2 stack size is negative" }
    }

    constructor(key: AEKey, value: Long) : this(key, value.toULong(), 0UL, null) {
        require(value >= 0) { "AE2 stack size is negative" }
    }

    constructor(key: AEKey, value: BigInteger) : this(
        key,
        if (value.bitLength() <= 64) value.toLong().toULong() else ULong.MAX_VALUE,
        if (value.bitLength() <= 128) (value.shiftRight(64)).toLong().toULong() else ULong.MAX_VALUE,
        if (value.bitLength() <= 128) null else value,
    ) {
        require(value.signum() >= 0) { "AE2 stack size is negative" }
    }
}
