package minecraftx.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun McSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    imeEnabled: Boolean = true,
    width: Int = 200,
    height: Int = 16,
    colors: McColorScheme = McTheme.colors,
    onSubmit: () -> Unit = {},
) {
    val latestChange = rememberUpdatedState(onValueChange)
    McTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Release && event.button == PointerButton.Secondary) {
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        latestChange.value(TextFieldValue("", TextRange.Zero))
                        change.consume()
                    }
                }
            }
        },
        imeEnabled = imeEnabled,
        singleLine = true,
        width = width,
        height = height,
        placeholder = placeholder,
        colors = colors,
        onImeActionPerformed = { if (it == ImeAction.Done) onSubmit() },
    )
}
