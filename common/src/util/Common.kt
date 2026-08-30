@file:Suppress("unused", "SpellCheckingInspection")

package allyouneed.util

import net.minecraft.resources.ResourceLocation
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
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

/**
 * 将 2^N 格式化为带数量级词头的形式
 */
fun formatScaledUnit(exp: Int, name: String? = null) = run {
    val prefix = when {
        exp >= 120 -> "max+"
        exp >= 110 -> "max"
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

/** 浮点数的指数部分 */
val Float.floatingExp get() = ((this.toBits() ushr 23) and 0xFF) - 127

/** 浮点数的指数部分 */
val Double.floatingExp get() = ((this.toBits() ushr 52).toInt() and 0x7FF) - 1023

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

/** Java 18 才加入导致的 */
private val UNSIGNED_MULTIPLY_HIGH_HANDLE = runCatching {
    MethodHandles.lookup().findStatic(
        Math::class.java, "unsignedMultiplyHigh", MethodType.methodType(
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
        )
    )
}.getOrNull()

fun unsignedMultiplyHigh(x: ULong, y: ULong): ULong = if (UNSIGNED_MULTIPLY_HIGH_HANDLE == null) {
    val z = Math.multiplyHigh(x.toLong(), y.toLong()).toULong()
    val fix = (if (x.toLong() < 0L) y else 0UL) + (if (y.toLong() < 0L) x else 0UL)
    z + fix
} else {
    (UNSIGNED_MULTIPLY_HIGH_HANDLE.invokeExact(x.toLong(), y.toLong()) as Long).toULong()
}
