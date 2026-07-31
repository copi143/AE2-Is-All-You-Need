package allyouneed.util

import java.math.BigInteger

/**
 * IEC 80000-13 (binary) formatting façade over [IntegerFormat.IEC].
 */
object IecFormat {
    @JvmStatic
    @JvmOverloads
    fun format(number: Long, width: Int = 0): String {
        val fmt = if (width > 0) IntegerFormat.iec(width) else IntegerFormat.IEC
        return fmt.format(number)
    }

    @JvmStatic
    @JvmOverloads
    fun format(number: BigInteger, width: Int = 0): String {
        val fmt = if (width > 0) IntegerFormat.iec(width) else IntegerFormat.IEC
        return fmt.format(number)
    }

    /** Bytes label using IEC binary units (e.g. `4Ki`, `256Mi`). */
    @JvmStatic
    fun formatBytes(bytes: Long): String {
        if (bytes < 0 || bytes == Long.MAX_VALUE) return "∞"
        return IntegerFormat.IEC.format(bytes)
    }

    @JvmStatic
    fun formatBytes(bytes: BigInteger): String {
        if (bytes.signum() < 0) return "∞"
        return IntegerFormat.IEC.format(bytes)
    }
}
