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
    val baseColor = JzCzHz(0.01f, 0.01f, 240f)
    for (i in 0..9) {
        val newColor = gamutMap(baseColor.copy(h = baseColor.h - i * 36)).toSRGB()
        val r = (newColor.r * 255).roundToInt()
        val g = (newColor.g * 255).roundToInt()
        val b = (newColor.b * 255).roundToInt()
        array.add(Color(r, g, b))
    }
    array
}
