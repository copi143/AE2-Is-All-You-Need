package allyouneed.compose.material

import allyouneed.compose.ui.layout.Box
import allyouneed.compose.ui.modifier.Modifier
import allyouneed.compose.ui.modifier.background
import allyouneed.compose.ui.modifier.clickable
import allyouneed.compose.ui.modifier.padding
import androidx.compose.runtime.Composable

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(0xFF555555.toInt())
            .padding(horizontal = 12, vertical = 6)
            .clickable(onClick = onClick),
        content = content,
    )
}
