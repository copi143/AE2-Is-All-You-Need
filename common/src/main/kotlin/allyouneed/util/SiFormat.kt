package allyouneed.util

import java.math.BigInteger
import kotlin.math.round

/**
 * SI (decimal) formatting façade over [IntegerFormat.SI].
 */
object SiFormat {
    @JvmStatic
    fun format(number: Long, width: Int): String = IntegerFormat.si(width).format(number)

    @JvmStatic
    fun format(number: BigInteger, width: Int): String = IntegerFormat.si(width).format(number)

    @JvmStatic
    fun format(number: Double, width: Int): String {
        require(number >= 0) { "amount must be non-negative" }
        if (number.isNaN() || number.isInfinite()) return "???"
        if (number <= Long.MAX_VALUE.toDouble() && number == round(number)) {
            return format(number.toLong(), width)
        }
        if (number > Long.MAX_VALUE.toDouble()) {
            return format(java.math.BigDecimal.valueOf(number).toBigInteger(), width)
        }
        // Non-integer doubles: fall back to long truncation path for UI slots
        return format(number.toLong(), width)
    }
}
