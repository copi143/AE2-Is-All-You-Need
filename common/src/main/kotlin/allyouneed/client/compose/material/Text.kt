package allyouneed.client.compose.material

import allyouneed.client.compose.ui.draw.McDrawScope
import allyouneed.client.compose.ui.layout.*
import allyouneed.client.compose.ui.modifier.DrawModifier
import allyouneed.client.compose.ui.modifier.Modifier
import androidx.compose.runtime.Composable
import net.minecraft.client.Minecraft

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Int = 0xFFFFFFFF.toInt(),
) {
    Layout(
        modifier = modifier then TextDrawModifier(text, color),
        measurePolicy = TextMeasurePolicy(text),
        content = {},
    )
}

private class TextDrawModifier(
    private val text: String,
    private val color: Int,
) : DrawModifier {
    override fun draw(scope: McDrawScope, drawContent: () -> Unit) {
        scope.drawText(text, 0, 0, color)
    }
}

private class TextMeasurePolicy(
    private val text: String,
) : MeasurePolicy {
    private val font = Minecraft.getInstance().font

    override fun measure(
        scope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val w = constraints.constrainWidth(font.width(text))
        val h = constraints.constrainHeight(font.lineHeight)
        return MeasureResult(w, h, emptyList()) {}
    }
}
