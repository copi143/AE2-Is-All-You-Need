package allyouneed.resgen

import com.github.ajalt.colormath.model.JzCzHz
import com.github.ajalt.colormath.model.RGB
import kotlin.math.roundToInt

class Color(r: Int, g: Int, b: Int) {
    val colormath = RGB(r, g, b)
    val termFg = "\u001b[38;2;${r};${g};${b}m"
    val termBg = "\u001b[48;2;${r};${g};${b}m"
    val hex = String.format("#%02X%02X%02X", r, g, b)
}

val AE2_COLORS = run {
    val array = arrayListOf<Color>()
    for (i in 0..19) {
        val newColor = gamutMap(
            JzCzHz(
                j = if (i < 10) 0.01f else 0.006f,
                c = 0.01f,
                h = 240f - i * 36,
            )
        ).toSRGB()
        val r = (newColor.r * 255).roundToInt()
        val g = (newColor.g * 255).roundToInt()
        val b = (newColor.b * 255).roundToInt()
        array.add(Color(r, g, b))
    }
    array
}

val AE2_GRADIENT = run {
    val array = arrayListOf<Color>()
    for (i in 0..19) {
        val newColor = gamutMap(
            JzCzHz(j = 0.01f, c = 0.01f, h = 240f - i * 18)
        ).toSRGB()
        val r = (newColor.r * 255).roundToInt()
        val g = (newColor.g * 255).roundToInt()
        val b = (newColor.b * 255).roundToInt()
        array.add(Color(r, g, b))
    }
    array
}

fun main() {
    println("\nAE2 Colors:")
    for ((index, color) in AE2_COLORS.withIndex()) {
        println("Color $index: ${color.hex} ${color.termFg}Sample Text\u001b[m ${color.termBg}Sample Background\u001b[m")
    }
    println("\nAE2 Gradient:")
    for ((index, color) in AE2_GRADIENT.withIndex()) {
        println("Gradient $index: ${color.hex} ${color.termFg}Sample Text\u001b[m ${color.termBg}Sample Background\u001b[m")
    }
}
