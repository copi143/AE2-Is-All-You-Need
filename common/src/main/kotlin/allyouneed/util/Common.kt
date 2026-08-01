package allyouneed.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger
import java.math.BigInteger

@Suppress("SpellCheckingInspection")
const val MODID = "ae2isallyouneed"

@Suppress("SpellCheckingInspection")
const val MODNAME = "AE2 Is All You Need"

@Suppress("SpellCheckingInspection")
val LOGNAME = MODNAME.replace(" ", "")

@JvmField
val logger: Logger = LoggerFactory.getLogger(LOGNAME)

@JvmField
val debugLogger: Logger = if (Services.platform.isDev()) {
    LoggerFactory.getLogger("$LOGNAME/Debug")
} else {
    NOPLogger.NOP_LOGGER
}

val Double.Ki get() = this * (1024.0)
val Double.Mi get() = this * (1024.0 * 1024.0)
val Double.Gi get() = this * (1024.0 * 1024.0 * 1024.0)
val Double.Ti get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Pi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Ei get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Zi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Yi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Ri get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Qi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)

val Int.Ki get() = this * (1024)
val Int.Mi get() = this * (1024 * 1024)
val Int.Gi get() = this * (1024 * 1024 * 1024)

val Long.Ki get() = this * (1024L)
val Long.Mi get() = this * (1024L * 1024L)
val Long.Gi get() = this * (1024L * 1024L * 1024L)
val Long.Ti get() = this * (1024L * 1024L * 1024L * 1024L)
val Long.Pi get() = this * (1024L * 1024L * 1024L * 1024L * 1024L)
val Long.Ei get() = this * (1024L * 1024L * 1024L * 1024L * 1024L * 1024L)

fun formatScaledUnit(exp: Int, name: String? = null) = run {
    val prefix = when {
        exp >= 100 -> "${1 shl (exp - 100)}q"
        exp >= 90 -> "${1 shl (exp - 90)}r"
        exp >= 80 -> "${1 shl (exp - 80)}y"
        exp >= 70 -> "${1 shl (exp - 70)}z"
        exp >= 60 -> "${1 shl (exp - 60)}e"
        exp >= 50 -> "${1 shl (exp - 50)}p"
        exp >= 40 -> "${1 shl (exp - 40)}t"
        exp >= 30 -> "${1 shl (exp - 30)}g"
        exp >= 20 -> "${1 shl (exp - 20)}m"
        exp >= 10 -> "${1 shl (exp - 10)}k"
        else -> "${1 shl exp}b"
    }
    if (name == null) prefix else "${prefix}_${name}"
}

val Float.floatingExp get() = (this.toBits() shr 23) - 127

val Double.floatingExp get() = (this.toBits() shr 52).toInt() - 1023

fun BigInteger.saturateToLong(): Long {
    if (this.signum() < 0) return 0L
    if (this.bitLength() > 63) return Long.MAX_VALUE
    return this.toLong()
}

fun BigInteger.saturateToInt(): Int {
    if (this.signum() < 0) return 0
    if (this.bitLength() > 31) return Int.MAX_VALUE
    return this.toInt()
}
