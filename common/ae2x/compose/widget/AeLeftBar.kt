package ae2x.compose.widget

import ae2x.compose.LocalAeHost
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun AeLeftBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalAeHost.current
    Column(
        modifier = modifier
            .padding(end = 2.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val scale = host.uiScale
                host.addExclusion(
                    (pos.x * scale).roundToInt(),
                    (pos.y * scale).roundToInt(),
                    (coords.size.width * scale).roundToInt(),
                    (coords.size.height * scale).roundToInt(),
                )
            },
        content = content,
    )
}
