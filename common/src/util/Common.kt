@file:Suppress("unused", "SpellCheckingInspection")

package allyouneed.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

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

val String.mcText: MutableComponent get() = Component.literal(this)
val String.mcTranslate: MutableComponent get() = Component.translatable(this)
fun String.mcTranslate(vararg args: Any?): MutableComponent = Component.translatable(this, *args)

fun MutableComponent.setBlack(): MutableComponent = this.withStyle(ChatFormatting.BLACK)
fun MutableComponent.setDarkBlue(): MutableComponent = this.withStyle(ChatFormatting.DARK_BLUE)
fun MutableComponent.setDarkGreen(): MutableComponent = this.withStyle(ChatFormatting.DARK_GREEN)
fun MutableComponent.setDarkAqua(): MutableComponent = this.withStyle(ChatFormatting.DARK_AQUA)
fun MutableComponent.setDarkRed(): MutableComponent = this.withStyle(ChatFormatting.DARK_RED)
fun MutableComponent.setDarkPurple(): MutableComponent = this.withStyle(ChatFormatting.DARK_PURPLE)
fun MutableComponent.setGold(): MutableComponent = this.withStyle(ChatFormatting.GOLD)
fun MutableComponent.setGray(): MutableComponent = this.withStyle(ChatFormatting.GRAY)
fun MutableComponent.setDarkGray(): MutableComponent = this.withStyle(ChatFormatting.DARK_GRAY)
fun MutableComponent.setBlue(): MutableComponent = this.withStyle(ChatFormatting.BLUE)
fun MutableComponent.setGreen(): MutableComponent = this.withStyle(ChatFormatting.GREEN)
fun MutableComponent.setAqua(): MutableComponent = this.withStyle(ChatFormatting.AQUA)
fun MutableComponent.setRed(): MutableComponent = this.withStyle(ChatFormatting.RED)
fun MutableComponent.setLightPurple(): MutableComponent = this.withStyle(ChatFormatting.LIGHT_PURPLE)
fun MutableComponent.setYellow(): MutableComponent = this.withStyle(ChatFormatting.YELLOW)
fun MutableComponent.setWhite(): MutableComponent = this.withStyle(ChatFormatting.WHITE)
fun MutableComponent.setObfuscated(): MutableComponent = this.withStyle(ChatFormatting.OBFUSCATED)
fun MutableComponent.setBold(): MutableComponent = this.withStyle(ChatFormatting.BOLD)
fun MutableComponent.setStrikethrough(): MutableComponent = this.withStyle(ChatFormatting.STRIKETHROUGH)
fun MutableComponent.setUnderline(): MutableComponent = this.withStyle(ChatFormatting.UNDERLINE)
fun MutableComponent.setItalic(): MutableComponent = this.withStyle(ChatFormatting.ITALIC)

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
