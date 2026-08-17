package ae2x.compose.format

import allyouneed.util.MetricFormat
import java.math.BigInteger

object AeAmountFormat {
    fun slot(amount: Long): String = MetricFormat.siFormat(amount, 4)

    fun slot(amount: BigInteger): String = MetricFormat.siFormat(amount, 4)

    fun tooltip(amount: Long): String = amount.toString()

    fun bytes(amount: Long): String = MetricFormat.iecFormat(amount)
}
