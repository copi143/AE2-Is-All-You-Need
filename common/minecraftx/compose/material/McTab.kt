package minecraftx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun McTabRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, content = content)
}

@Composable
fun McTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    handleClicks: Boolean = true,
    colors: McColorScheme = McTheme.colors,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = if (selected) colors.tabBackgroundSelected else colors.tabBackground
    Box(
        modifier = modifier
            .height(McTheme.shapes.tabHeight)
            .background(fill)
            .drawBehind {
                drawRect(color = colors.tabBorder, style = Stroke(1f))
                if (selected) {
                    drawRect(
                        color = colors.tabIndicator,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2f),
                        size = androidx.compose.ui.geometry.Size(size.width, 2f),
                    )
                }
            }
            .then(if (handleClicks) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun McTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    handleClicks: Boolean = true,
    colors: McColorScheme = McTheme.colors,
) {
    McTab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        handleClicks = handleClicks,
        colors = colors,
    ) {
        val color = if (enabled) colors.textPrimary else colors.textDisabled
        McText(Component.literal(label), color = color.value.toInt())
    }
}
