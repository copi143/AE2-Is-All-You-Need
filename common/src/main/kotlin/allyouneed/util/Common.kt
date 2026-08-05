@file:Suppress("SpellCheckingInspection", "unused")

package allyouneed.util

import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger
import java.math.BigInteger

const val MODID = "ae2isallyouneed"
const val MODNAME = "AE2 Is All You Need"
val LOGNAME = MODNAME.replace(" ", "")

@JvmField
val logger: Logger = LoggerFactory.getLogger(LOGNAME)

@JvmField
val debugLogger: Logger = if (Services.platform.isDev()) {
    LoggerFactory.getLogger("$LOGNAME/Debug")
} else {
    NOPLogger.NOP_LOGGER
}

fun String.rl(ns: String) = ResourceLocation(ns, this)
val String.rl get() = this.rl(MODID)
val String.rlMC get() = this.rl("minecraft")
val String.rlAE get() = this.rl("ae2")
val String.rlGT get() = this.rl("gtceu")

data class Number(val bigInt: BigInteger) {
    val intOverflow = bigInt.bitLength() > 31
    val longOverflow = bigInt.bitLength() > 63
    val int = bigInt.toInt()
    val long = bigInt.toLong()
    val float = bigInt.toFloat()
    val double = bigInt.toDouble()

    companion object {
        @JvmStatic
        fun powOfTwo(e: Int): Number {
            return Number(BigInteger.TWO.pow(e))
        }
    }
}

val kibi = Number.powOfTwo(10)
val mebi = Number.powOfTwo(20)
val gibi = Number.powOfTwo(30)
val tebi = Number.powOfTwo(40)
val pebi = Number.powOfTwo(50)
val exbi = Number.powOfTwo(60)
val zebi = Number.powOfTwo(70)
val yobi = Number.powOfTwo(80)
val robi = Number.powOfTwo(90)
val quebi = Number.powOfTwo(100)

val BigInteger.Ki get() = this * kibi.bigInt
val BigInteger.Mi get() = this * mebi.bigInt
val BigInteger.Gi get() = this * gibi.bigInt
val BigInteger.Ti get() = this * tebi.bigInt
val BigInteger.Pi get() = this * pebi.bigInt
val BigInteger.Ei get() = this * exbi.bigInt
val BigInteger.Zi get() = this * zebi.bigInt
val BigInteger.Yi get() = this * yobi.bigInt
val BigInteger.Ri get() = this * robi.bigInt
val BigInteger.Qi get() = this * quebi.bigInt

val Int.Ki get() = this * kibi.int
val Int.Mi get() = this * mebi.int
val Int.Gi get() = this * gibi.int

val Long.Ki get() = this * kibi.long
val Long.Mi get() = this * mebi.long
val Long.Gi get() = this * gibi.long
val Long.Ti get() = this * tebi.long
val Long.Pi get() = this * pebi.long
val Long.Ei get() = this * exbi.long

val Float.Ki get() = this * kibi.float
val Float.Mi get() = this * mebi.float
val Float.Gi get() = this * gibi.float
val Float.Ti get() = this * tebi.float
val Float.Pi get() = this * pebi.float
val Float.Ei get() = this * exbi.float
val Float.Zi get() = this * zebi.float
val Float.Yi get() = this * yobi.float
val Float.Ri get() = this * robi.float
val Float.Qi get() = this * quebi.float

val Double.Ki get() = this * kibi.double
val Double.Mi get() = this * mebi.double
val Double.Gi get() = this * gibi.double
val Double.Ti get() = this * tebi.double
val Double.Pi get() = this * pebi.double
val Double.Ei get() = this * exbi.double
val Double.Zi get() = this * zebi.double
val Double.Yi get() = this * yobi.double
val Double.Ri get() = this * robi.double
val Double.Qi get() = this * quebi.double

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
