package ae2x.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import minecraftx.compose.material.McTab
import minecraftx.compose.material.McTabRow

@Composable
fun <T> AeEncodingTabs(
    values: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
) {
    McTabRow(modifier) {
        for (value in values) {
            McTab(
                label = label(value),
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}
