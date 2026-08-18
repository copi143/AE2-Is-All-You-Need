package allyouneed.resgen

import com.github.ajalt.colormath.model.JzCzHz
import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.colormath.model.SRGB

interface IColor {
    val colormath: RGB
    val termFg: String
    val termBg: String
    val hex: String
}

class Color(r: Int, g: Int, b: Int): IColor {
    private constructor(srgb: RGB, b: Boolean) : this(srgb.redInt, srgb.greenInt, srgb.blueInt)
    constructor(rgb: RGB) : this(rgb.toSRGB(), true)
    constructor(jch: JzCzHz) : this(gamutMap(jch).toSRGB(), true)
    override val colormath = SRGB(r, g, b)
    override val termFg = "\u001b[38;2;${r};${g};${b}m"
    override val termBg = "\u001b[48;2;${r};${g};${b}m"
    override val hex = String.format("#%02X%02X%02X", r, g, b)
}

enum class LogColor(private val inner: Color): IColor by inner {
    Fatal(Color(JzCzHz(0.008f, 0.015f, 30f))),
    Error(Color(JzCzHz(0.008f, 0.015f, 60f))),
    Warn(Color(JzCzHz(0.008f, 0.015f, 90f))),
    Info(Color(JzCzHz(0.008f, 0.015f, 150f))),
    Debug(Color(JzCzHz(0.008f, 0.015f, 240f))),
    Trace(Color(JzCzHz(0.008f, 0.015f, 0f))),
    Spam(Color(JzCzHz(0.005f, 0.005f, 300f))),
}

val AE2_COLOR_CREATIVE = Color(200, 28, 228)

val AE2_COLORS = run {
    val array = arrayListOf<Color>()
    for (i in 0..19) {
        array.add(Color(
            JzCzHz(
                j = if (i < 10) 0.01f else 0.006f,
                c = 0.01f,
                h = 240f - i * 36,
            )
        ))
    }
    array
}

val AE2_GRADIENT = run {
    val array = arrayListOf<Color>()
    for (i in 0..19) {
        array.add(Color(JzCzHz(j = 0.01f, c = 0.01f, h = 240f - i * 18)))
    }
    array
}

fun main() {
    println("\nLog Colors:")
    for (color in LogColor.entries) {
        println("Level ${color.name}: ${color.hex} ${color.termFg}Sample Text\u001b[m ${color.termBg}Sample Background\u001b[m")
    }
    println("\nAE2 Color Creative:")
    println("    ${AE2_COLOR_CREATIVE.hex} ${AE2_COLOR_CREATIVE.termFg}Sample Text\u001b[m ${AE2_COLOR_CREATIVE.termBg}Sample Background\u001b[m")
    println("\nAE2 Colors:")
    for ((index, color) in AE2_COLORS.withIndex()) {
        println("Color $index: ${color.hex} ${color.termFg}Sample Text\u001b[m ${color.termBg}Sample Background\u001b[m")
    }
    println("\nAE2 Gradient:")
    for ((index, color) in AE2_GRADIENT.withIndex()) {
        println("Gradient $index: ${color.hex} ${color.termFg}Sample Text\u001b[m ${color.termBg}Sample Background\u001b[m")
    }
}
