package allyouneed.client.compose.ui.layout

import allyouneed.client.compose.ui.modifier.Modifier
import allyouneed.client.compose.ui.node.LayoutNode
import allyouneed.client.compose.ui.node.UiApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode

@Composable
fun Layout(
    modifier: Modifier = Modifier,
    measurePolicy: MeasurePolicy,
    content: @Composable () -> Unit,
) {
    ComposeNode<LayoutNode, UiApplier>(
        factory = { LayoutNode() },
        update = {
            set(measurePolicy) { this.measurePolicy = it }
            set(modifier) { this.modifier = it }
        },
        content = content,
    )
}
