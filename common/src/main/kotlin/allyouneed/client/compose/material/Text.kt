package allyouneed.client.compose.material

import allyouneed.client.compose.platform.McGraphics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import net.minecraft.client.Minecraft

/**
 * Renders [text] with the Minecraft font. Layout uses official Compose constraints measured against
 * the MC font metrics; painting bypasses the official text pipeline (which would pull in the skiko
 * font stack) and draws straight onto [McGraphics]'s GuiGraphics inside the node's translated frame.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Int = 0xFFFFFFFF.toInt(),
) {
    val font = Minecraft.getInstance().font
    Layout(
        content = {},
        modifier = modifier.drawBehind {
            McGraphics.current?.drawString(font, text, 0, 0, color)
        },
    ) { _, constraints: androidx.compose.ui.unit.Constraints ->
        val w = constraints.constrainWidth(font.width(text))
        val h = constraints.constrainHeight(font.lineHeight)
        layout(w, h) {}
    }
}
