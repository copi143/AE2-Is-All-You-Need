package minecraftx.compose.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 简约风格(也是注册表的回落风格):扁平外观 —— 纯色底 + 1px 直角细边框,横平竖直,
 * 无圆角/斜面/发光/阴影等任何层叠效果。配色走 dark/light 全局配置
 * ([McThemeSettings.colorScheme])。
 */
object MinimalMcStyle : McStyle {

    override val id: String = "minimal"
    override val defaultColors: McColorScheme get() = McThemeSettings.colorScheme

    override fun DrawScope.panelChrome(colors: McColorScheme) {
        drawRect(colors.panelBackground)
        drawRect(colors.panelBorder, style = Stroke(1f))
    }

    override fun DrawScope.closeButtonChrome(colors: McColorScheme) {
        drawRect(colors.closeButtonBackground)
        drawRect(colors.closeButtonBorder, style = Stroke(1f))
    }

    override fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean) {
        drawRect(
            when {
                !enabled -> colors.buttonBackgroundDisabled
                pressed -> colors.buttonBackgroundPressed
                hovered -> colors.buttonBackgroundHovered
                else -> colors.buttonBackground
            },
        )
        drawRect(
            if (hovered && enabled) colors.buttonBorderFocused else colors.buttonBorder,
            style = Stroke(1f),
        )
    }

    override fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean) {
        drawRect(if (selected) colors.tabBackgroundSelected else colors.tabBackground)
        drawRect(colors.tabBorder, style = Stroke(1f))
        if (selected) {
            drawRect(colors.tabIndicator, topLeft = Offset(0f, size.height - 2f), size = Size(size.width, 2f))
        }
    }

    override fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(colors.checkboxBackground)
        drawRect(colors.checkboxBorder, style = Stroke(1f))
    }

    override fun DrawScope.toggleTrackChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(if (checked) colors.toggleTrackOn else colors.toggleTrackOff)
        drawRect(colors.buttonBorder, style = Stroke(1f))
    }

    override fun DrawScope.toggleThumbChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(colors.toggleThumb)
    }

    override fun DrawScope.progressTrackChrome(colors: McColorScheme) {
        drawRect(colors.progressTrack)
        drawRect(colors.buttonBorder, style = Stroke(1f))
    }

    override fun DrawScope.progressFillChrome(colors: McColorScheme) {
        drawRect(colors.progressFill)
    }

    override fun DrawScope.scrollbarTrackChrome(colors: McColorScheme) {
        drawRect(colors.scrollbarTrack)
    }

    override fun DrawScope.scrollbarBarChrome(colors: McColorScheme) {
        drawRect(colors.scrollbarBar)
    }

    override fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean) {
        drawRect(if (focused) colors.inputBorderFocused else colors.inputBorder)
        drawRect(colors.inputBackground, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
    }

    override fun DrawScope.tooltipChrome(colors: McColorScheme) {
        drawRect(colors.tooltipBackground)
        drawRect(colors.tooltipBorder, style = Stroke(1f))
    }
}
