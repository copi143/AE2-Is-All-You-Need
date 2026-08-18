package minecraftx.compose.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McTheme
import java.awt.Cursor

@Composable
fun McSplitter(
    axis: DockAxis,
    onDrag: (delta: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontal = axis == DockAxis.Horizontal
    val icon = PointerIcon(
        Cursor(if (horizontal) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR),
    )
    Box(
        modifier
            .then(if (horizontal) Modifier.width(4.dp).fillMaxHeight() else Modifier.height(4.dp).fillMaxWidth())
            .background(McTheme.colors.panelBorder)
            .pointerHoverIcon(icon)
            .draggable(
                state = rememberDraggableState(onDrag),
                orientation = if (horizontal) Orientation.Horizontal else Orientation.Vertical,
            ),
    )
}
