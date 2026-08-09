package allyouneed.client.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.minecraft.network.chat.Component

/**
 * A fixed-size framed panel: dark background plus a 1px border. [content] is laid out on top of the
 * chrome inside a [BoxScope], so children can use `Modifier.matchParentSize()` or position
 * themselves with `Modifier.offset` relative to the panel origin. Centering on screen is left to the
 * caller (e.g. `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`).
 */
@Composable
fun McPanel(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    background: Color = Color(0xC0101010),
    border: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.size(width, height)) {
        Box(Modifier.matchParentSize().background(background))
        Box(Modifier.matchParentSize().drawBehind { drawRect(color = border, style = Stroke(1f)) })
        content()
    }
}

/** The standard ✕ close button (14x14 box with border and a centered cross glyph). */
@Composable
fun McCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color(0xAA404040))
            .drawBehind { drawRect(color = Color.White, style = Stroke(1f)) }
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        McText(Component.literal("✕"), maxWidth = 12)
    }
}
