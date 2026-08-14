package allyouneed.util.bigint

//import appeng.api.stacks.AEKey
//import appeng.api.stacks.GenericStack
//import java.math.BigInteger
//
//open class UBig private constructor(
//    private val l0: ULong = 0UL,
//    private val l1: ULong = 0UL,
//    private val bi: BigInteger?  = null,
//    ) {
//
//    init {
//        assert(l0 >= 0 || (bi != null && bi > BigInteger.ZERO))
//    }
//
//    val signum: Int get() = bi?.signum() ?: if(l0 ==0UL && l1 ==0UL) 0 else 1
//    val valIntSaturate: Int get() = if (l1 == 0UL && l0 < Int.MAX_VALUE.toULong()) l0.toInt() else Int.MAX_VALUE
//    val valLongSaturate: Long get() = if (l1 == 0UL && l0 < Long.MAX_VALUE.toULong()) l0.toLong() else Long.MAX_VALUE
//    val valBig: BigInteger get() = bi ?: BigInteger.valueOf(l0)
//    val valString: String get() = bi?.toString() ?: l0.toString()
//    val isZero: Boolean get() = l1 == 0UL && l0 == 0UL
//
//    fun setSize(value: Long): UBig = UBig(value, null)
//    fun setSize(value: BigInteger): UBig = UBig(value)
//
//    operator fun plus(other: UBig): UBig {
//        return if (this.bi != null || other.bi != null) {
//            UBig( -1, this.valBig + other.valBig)
//        } else {
//            val v = this.l0 + other.l0
//            if (v < 0) {
//                UBig(-1, BigInteger.valueOf(this.l0) + BigInteger.valueOf(other.l0))
//            } else {
//                UBig(v, null)
//            }
//        }
//    }
//
//    operator fun plus(amount: Long): UBig {
//        require(amount >= 0) { "Cannot add a negative amount; use minus" }
//        if (amount == 0L) return this
//        if (bi != null) {
//            return UBig(valBig + BigInteger.valueOf(amount))
//        }
//        val v = this.l0 + amount
//        return if (v < 0) {
//            UBig(-1, valBig + BigInteger.valueOf(amount))
//        } else {
//            UBig(v, null)
//        }
//    }
//
//    operator fun plus(amount: BigInteger): UBig {
//        require(amount.signum() >= 0) { "Cannot add a negative amount; use minus" }
//        if (amount.signum() == 0) return this
//        return UBig(this.valBig + amount)
//    }
//
//    operator fun minus(other: UBig): UBig {
//        return minus(other.valBig)
//    }
//
//    operator fun minus(amount: Long): UBig {
//        require(amount >= 0) { "Cannot subtract a negative amount; use plus" }
//        if (amount == 0L) return this
//        if (bi == null) {
//            require(l0 >= amount) { "Resulting stack size would be negative" }
//            return UBig(l0 - amount, null)
//        }
//        val result = valBig - BigInteger.valueOf(amount)
//        require(result.signum() >= 0) { "Resulting stack size would be negative" }
//        return UBig(result)
//    }
//
//    operator fun minus(amount: BigInteger): UBig {
//        require(amount.signum() >= 0) { "Cannot subtract a negative amount; use plus" }
//        if (amount.signum() == 0) return this
//        val result = valBig - amount
//        require(result.signum() >= 0) { "Resulting stack size would be negative" }
//        return UBig(result)
//    }
//
//    operator fun times(scale: Long): UBig {
//        require(scale >= 0) { "Cannot multiply by a negative number." }
//        return if (scale == 0L) {
//            UBig(this.0, null)
//        } else if (this.bi != null) {
//            UBig(this.valBig * BigInteger.valueOf(scale))
//        } else {
//            val v = runCatching { Math.multiplyExact(this.l0, scale) }.getOrNull()
//            if (v != null) {
//                UBig(v, null)
//            } else {
//                UBig(BigInteger.valueOf(this.l0) * BigInteger.valueOf(scale))
//            }
//        }
//    }
//
//    operator fun compareTo(other: UBig): Int {
//        return when {
//            this.bi != null && other.bi != null -> this.bi.compareTo(other.bi)
//            this.bi == null && other.bi != null -> -1
//            this.bi != null && other.bi == null -> 1
//            else -> this.l0.compareTo(other.l0)
//        }
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (other !is UBig) return false
//        if (this.key != other.key) return false
//        if (this.l0 != other.l0) return false
//        if (this.bi != other.bi) return false
//        return true
//    }
//
//    override fun hashCode(): Int = if (l1 == ULong.MAX_VALUE && l0 == ULong.MAX_VALUE)  {
//        bi.hashCode()
//    } else {
//        l1.hashCode() * 32 + l0.hashCode()
//    }
//
//    override fun toString(): String = valBig.toString()
//
//    companion object {
//        @JvmStatic
//        fun from(stack: GenericStack) = UBig(stack.what, stack.amount)
//
//        @JvmStatic
//        fun from(key: AEvalue: Long) = UBig(value)
//
//        @JvmStatic
//        fun from(key: AEvalue: BigInteger) = UBig(value)
//    }
//}
