package ae2x.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import appeng.client.gui.MathExpressionParser
import minecraftx.compose.material.McNumberField
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import java.text.DecimalFormat

@Composable
fun AeNumberEntry(
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    min: Long = 0,
    max: Long = Long.MAX_VALUE,
    step: Long = 1,
    width: Int = 60,
    colors: McColorScheme = McTheme.colors,
) {
    McNumberField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        min = min,
        max = max,
        step = step,
        width = width,
        colors = colors,
    )
}

fun parseAeExpression(text: String, fallback: Long): Long {
    val format = DecimalFormat().apply { isParseBigDecimal = true }
    return MathExpressionParser.parse(text, format)
        .map { it.toLong() }
        .orElse(fallback)
}
