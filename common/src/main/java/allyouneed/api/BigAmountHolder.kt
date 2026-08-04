package allyouneed.api

import java.math.BigInteger

/** Object that can carry an optional BigInteger stored amount (no global map). */
interface BigAmountHolder {
    var bigAmount: BigInteger?
}
