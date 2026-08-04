package allyouneed.client.compose.ui.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

interface PointerInputModifier : Modifier.Element

data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int, py: Int) = px in x until (x + width) && py in y until (y + height)
}

class ClickableModifier(
    val onClick: () -> Unit,
) : PointerInputModifier {
    var enabled: Boolean = true
}

fun Modifier.clickable(onClick: () -> Unit): Modifier = this then ClickableModifier(onClick)

class ScrollModifier(
    val scrollState: ScrollState,
) : PointerInputModifier

class ScrollState(initial: Int = 0) {
    var value: Int by mutableStateOf(initial)
    var maxValue: Int = 0
    fun scroll(amount: Int) { value = (value + amount).coerceIn(0, maxValue) }
}

fun Modifier.verticalScroll(state: ScrollState): Modifier = this then ScrollModifier(state)
