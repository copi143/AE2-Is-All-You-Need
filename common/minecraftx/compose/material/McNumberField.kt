package minecraftx.compose.material

import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme

@Composable
fun McNumberField(
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    min: Long = 0,
    max: Long = Long.MAX_VALUE,
    step: Long = 1,
    width: Int = 60,
    colors: McColorScheme = McTheme.colors,
) {
    var text by remember(value) { mutableStateOf(TextFieldValue(value.toString(), TextRange(value.toString().length))) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        McIconButton(onClick = { onValueChange((value - step).coerceIn(min, max)) }, colors = colors) {
            McText("-", maxWidth = 8, color = colors.textPrimary.toArgb())
        }
        Spacer(Modifier.width(2.dp))
        McTextField(
            value = text,
            onValueChange = { next ->
                text = next
                parseLong(next.text)?.let { onValueChange(it.coerceIn(min, max)) }
            },
            modifier = Modifier.pointerInput(value, min, max, step) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.isConsumed) continue
                            val delta = change.scrollDelta.y
                            if (delta == 0f) continue
                            val direction = if (delta > 0f) -step else step
                            onValueChange((value + direction).coerceIn(min, max))
                            change.consume()
                        }
                    }
                }
            },
            imeEnabled = false,
            singleLine = true,
            width = width,
            height = 16,
            colors = colors,
        )
        Spacer(Modifier.width(2.dp))
        McIconButton(onClick = { onValueChange((value + step).coerceIn(min, max)) }, colors = colors) {
            McText("+", maxWidth = 8, color = colors.textPrimary.toArgb())
        }
    }
}

private fun parseLong(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "-" || trimmed == "+") return null
    return trimmed.toLongOrNull()
}
