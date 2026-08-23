@file:Suppress("unused")

package allyouneed.util

import net.minecraft.resources.ResourceLocation
import java.math.BigInteger

fun idify(value: String): String = value.lowercase().replace(" ", "_").replace("-", "_").replace(".", "_")

fun String.rl(ns: String) = ResourceLocation(ns, this)
val String.rl get() = this.rl(MODID)
val String.rlMC get() = this.rl("minecraft")
val String.rlAE get() = this.rl("ae2")
val String.rlGT get() = this.rl("gtceu")

fun ResourceLocation.joinParent(parent: String): ResourceLocation {
    return "$parent/$path".rl(namespace)
}

fun ResourceLocation.joinChild(child: String): ResourceLocation {
    return "$path/$child".rl(namespace)
}

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
