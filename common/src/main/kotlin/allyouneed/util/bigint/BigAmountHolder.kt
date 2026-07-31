package allyouneed.util.bigint

import java.math.BigInteger

/** Object that can carry an optional BigInteger stored amount (no global map). */
interface BigAmountHolder {
    fun getBigAmount(): BigInteger?

    fun setBigAmount(amount: BigInteger?)
}
