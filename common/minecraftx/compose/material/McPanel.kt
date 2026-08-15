package minecraftx.compose.material

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
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component

/**
 * A fixed-size framed panel: background plus a 1px border, both taken from the active theme
 * ([McTheme.colors]). [content] is laid out on top of the chrome inside a [BoxScope], so children
 * can use `Modifier.matchParentSize()` or position themselves with `Modifier.offset` relative to
 * the panel origin. Centering on screen is left to the caller (e.g.
 * `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`).
 */
@Composable
fun McPanel(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.size(width, height)) {
        Box(Modifier.matchParentSize().background(colors.panelBackground))
        Box(Modifier.matchParentSize().drawBehind { drawRect(color = colors.panelBorder, style = Stroke(1f)) })
        content()
    }
}

/** The standard ✕ close button (box with border and a centered cross glyph). */
@Composable
fun McCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    colors: McColorScheme = McTheme.colors,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(colors.closeButtonBackground)
            .drawBehind { drawRect(color = colors.closeButtonBorder, style = Stroke(1f)) }
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        McText(Component.literal("✕"), maxWidth = 12, color = colors.textPrimary.value.toInt())
    }
}
