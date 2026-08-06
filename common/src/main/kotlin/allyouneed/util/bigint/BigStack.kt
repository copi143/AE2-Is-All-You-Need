package allyouneed.util.bigint

import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import java.math.BigInteger

class BigStack private constructor(val key: AEKey, private val valLong: Long, private val valBig: BigInteger?) {
    constructor(key: AEKey, value: Long) : this(key, value, null)
    constructor(key: AEKey, value: BigInteger) : this(key, -1, value)

    init {
        assert(valLong >= 0 || valBig != null) { "AE2 stack size is negative" }
    }

    val valueLong: Long get() = valLong
    val valueBig: BigInteger get() = valBig ?: BigInteger.valueOf(valLong)

    operator fun plus(other: BigStack): BigStack {
        assert(this.key == other.key)
        return if (this.valBig != null || other.valBig != null) {
            val v1 = (this.valBig ?: BigInteger.valueOf(this.valLong))
            val v2 = (other.valBig ?: BigInteger.valueOf(other.valLong))
            BigStack(this.key, v1 + v2)
        } else {
            val v = this.valLong + other.valLong
            if (v < 0) {
                BigStack(this.key, BigInteger.valueOf(this.valLong) + BigInteger.valueOf(other.valLong))
            } else {
                BigStack(this.key, v)
            }
        }
    }

    companion object {
        @JvmStatic
        fun from(stack: GenericStack) = BigStack(stack.what, stack.amount)
    }
}
