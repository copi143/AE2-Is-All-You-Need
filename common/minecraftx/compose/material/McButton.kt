package minecraftx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component

@Composable
fun McButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: McColorScheme = McTheme.colors,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        !enabled -> colors.buttonBackgroundDisabled
        pressed -> colors.buttonBackgroundPressed
        hovered -> colors.buttonBackgroundHovered
        else -> colors.buttonBackground
    }
    val border = if (hovered && enabled) colors.buttonBorderFocused else colors.buttonBorder
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = McTheme.shapes.buttonHeight)
            .background(fill)
            .drawBehind { drawRect(color = border, style = Stroke(1f)) }
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun McButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: McColorScheme = McTheme.colors,
) {
    McButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors) {
        val color = if (enabled) colors.textPrimary else colors.textDisabled
        McText(Component.literal(label), color = color.toArgb())
    }
}

@Composable
fun McIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = McTheme.shapes.iconButtonSize,
    colors: McColorScheme = McTheme.colors,
    content: @Composable BoxScope.() -> Unit,
) {
    McButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        colors = colors,
        content = content,
    )
}
