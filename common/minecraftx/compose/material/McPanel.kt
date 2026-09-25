package minecraftx.compose.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component

/**
 * A fixed-size framed panel; its chrome (background + border) is drawn by the active theme style
 * ([McTheme.style]). [content] is laid out on top of the chrome inside a [BoxScope], so children
 * can use `Modifier.matchParentSize()` or position themselves with `Modifier.offset` relative to
 * the panel origin. Centering on screen is left to the caller (e.g.
 * `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`).
 */
@Composable
fun McPanel(
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = McTheme.style
    Box(modifier) {
        Box(Modifier.matchParentSize().drawBehind { with(style) { panelChrome(colors) } })
        content()
    }
}

@Composable
fun McPanel(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
    content: @Composable BoxScope.() -> Unit,
) {
    McPanel(modifier.size(width, height), colors, content)
}

/** The standard ✕ close button; chrome drawn by the active theme style. */
@Composable
fun McCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    colors: McColorScheme = McTheme.colors,
) {
    val style = McTheme.style
    Box(
        modifier = modifier
            .size(size)
            .drawBehind { with(style) { closeButtonChrome(colors) } }
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        McText(Component.literal("✕"), maxWidth = 16, color = colors.textPrimary.toArgb())
    }
}
