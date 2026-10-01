package minecraftx.compose.material

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.GuiGraphics
import allyouneed.client.compose.platform.renderMcTooltip

/**
 * A tooltip rendered entirely with the Compose framework: a [Column] of [McText] lines (each drawn
 * with the Minecraft font) wrapped in a padded background + border, measured to the content.
 *
 * It is the compose-rendered counterpart of the vanilla
 * `GuiGraphics.renderMcTooltip`; build the same tooltip once as
 * components and either draw it with vanilla ([renderMcTooltip] on a raw [GuiGraphics]) or lay it
 * out and render it here. This one participates in the Compose layout (so it can be placed in a
 * fixed spot, anchored to another node, etc.) instead of painting over everything after the tree.
 *
 * Position the modifier where the tooltip should sit, e.g.
 * `Modifier.offset((mouse.x + 12).dp, (mouse.y + 8).dp).zIndex(1f)` for a floating tooltip, or
 * offset it relative to its anchor node for an in-place variant.
 */
@Composable
fun McTooltip(
    lines: List<Component>,
    modifier: Modifier = Modifier,
    textColor: Int = McTheme.colors.textPrimary.toArgb(),
    maxWidth: Int = 220,
    paddingX: Int = 4,
    paddingY: Int = 3,
) {
    val colors = McTheme.colors
    val style = McTheme.style
    Column(
        modifier = modifier
            .drawBehind { with(style) { tooltipChrome(colors) } }
            .padding(horizontal = paddingX.dp, vertical = paddingY.dp),
    ) {
        for (line in lines) {
            McText(text = line, color = textColor, maxWidth = maxWidth)
        }
    }
}
