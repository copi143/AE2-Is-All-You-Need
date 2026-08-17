package ae2x.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import minecraftx.compose.material.McSearchField
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme

@Composable
fun AeSearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    width: Int = 80,
    colors: McColorScheme = McTheme.colors,
) {
    McSearchField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        width = width,
        height = 16,
        colors = colors,
    )
}
