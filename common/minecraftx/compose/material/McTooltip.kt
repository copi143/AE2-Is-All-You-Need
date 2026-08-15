package minecraftx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component

/**
 * A tooltip rendered entirely with the Compose framework: a [Column] of [McText] lines (each drawn
 * with the Minecraft font) wrapped in a padded background + border, measured to the content.
 *
 * It is the compose-rendered counterpart of the vanilla
 * [allyouneed.client.compose.platform.GuiGraphics.renderMcTooltip]: build the same tooltip once as
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
    textColor: Int = McTheme.colors.textPrimary.value.toInt(),
    background: Color = McTheme.colors.tooltipBackground,
    border: Color = McTheme.colors.tooltipBorder,
    maxWidth: Int = 220,
    paddingX: Int = 4,
    paddingY: Int = 3,
) {
    Column(
        modifier = modifier
            .background(background)
            .drawBehind { drawRect(color = border, style = Stroke(1f)) }
            .padding(horizontal = paddingX.dp, vertical = paddingY.dp),
    ) {
        for (line in lines) {
            McText(text = line, color = textColor, maxWidth = maxWidth)
        }
    }
}
