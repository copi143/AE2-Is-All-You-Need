package minecraftx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component

@Composable
fun McCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    colors: McColorScheme = McTheme.colors,
) {
    Row(
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(colors.checkboxBackground)
                .drawBehind { drawRect(color = colors.checkboxBorder, style = Stroke(1f)) },
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                McText(Component.literal("✓"), maxWidth = 8, color = colors.checkboxMark.value.toInt())
            }
        }
        if (label != null) {
            Spacer(Modifier.width(4.dp))
            val color = if (enabled) colors.textPrimary else colors.textDisabled
            McText(Component.literal(label), color = color.value.toInt())
        }
    }
}

@Composable
fun McToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: McColorScheme = McTheme.colors,
) {
    Box(
        modifier = modifier
            .size(20.dp, 10.dp)
            .background(if (checked) colors.toggleTrackOn else colors.toggleTrackOff)
            .drawBehind { drawRect(color = colors.buttonBorder, style = Stroke(1f)) }
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(8.dp)
                .background(colors.toggleThumb),
        )
    }
}
