package minecraftx.compose.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Material Design 3(深色基准)配色。 */
object MaterialColors : McColorScheme {
    override val textPrimary: Color get() = Color(0xFFE6E1E5)
    override val textSecondary: Color get() = Color(0xFFCAC4D0)
    override val textDisabled: Color get() = Color(0x66E6E1E5)

    override val panelBackground: Color get() = Color(0xFF211F26)
    override val panelBorder: Color get() = Color(0x00000000)

    override val closeButtonBackground: Color get() = Color(0xFF2B2930)
    override val closeButtonBorder: Color get() = Color(0x00000000)

    override val contentBackground: Color get() = Color(0xFF1C1B1F)
    override val contentBorder: Color get() = Color(0x00000000)

    override val slotBackground: Color get() = Color(0xFF2B2930)
    override val slotBorder: Color get() = Color(0x00000000)
    override val slotHoverOverlay: Color get() = Color(0x14E6E1E5)

    override val inputBackground: Color get() = Color(0xFF2B2930)
    override val inputBorder: Color get() = Color(0xFF938F99)
    override val inputBorderFocused: Color get() = Color(0xFFD0BCFF)
    override val textCaret: Color get() = Color(0xFFD0BCFF)
    override val textSelection: Color get() = Color(0x55D0BCFF)

    override val scrollbarTrack: Color get() = Color(0x00000000)
    override val scrollbarBar: Color get() = Color(0xFF938F99)

    override val tooltipBackground: Color get() = Color(0xFF313033)
    override val tooltipBorder: Color get() = Color(0x00000000)

    override val buttonBackground: Color get() = Color(0xFFD0BCFF)
    override val buttonBackgroundHovered: Color get() = Color(0xFFD0BCFF)
    override val buttonBackgroundPressed: Color get() = Color(0xFFD0BCFF)
    override val buttonBackgroundDisabled: Color get() = Color(0x1FE6E1E5)
    override val buttonBorder: Color get() = Color(0x00000000)
    override val buttonBorderFocused: Color get() = Color(0x00000000)

    override val tabBackground: Color get() = Color(0x00000000)
    override val tabBackgroundSelected: Color get() = Color(0x00000000)
    override val tabBorder: Color get() = Color(0x00000000)
    override val tabIndicator: Color get() = Color(0xFFD0BCFF)

    override val progressTrack: Color get() = Color(0xFF49454F)
    override val progressFill: Color get() = Color(0xFFD0BCFF)

    override val checkboxBackground: Color get() = Color(0x00000000)
    override val checkboxBorder: Color get() = Color(0xFF938F99)
    override val checkboxMark: Color get() = Color(0xFF381E72)

    override val toggleTrackOff: Color get() = Color(0xFF49454F)
    override val toggleTrackOn: Color get() = Color(0xFFD0BCFF)
    override val toggleThumb: Color get() = Color(0xFF381E72)

    override val mdQuoteBar: Color get() = Color(0xFFD0BCFF)
    override val mdHeadingAccent: Color get() = Color(0xFFD0BCFF)
    override val mdLink: Color get() = Color(0xFFD0BCFF)
}

/**
 * Material Design 风格:圆角卡片式面板、全圆角实心按钮(primary 填充 + onPrimary 文字),
 * 悬停/按下用白色状态层叠加(M3 state layer),输入框聚焦时底部出现 2px primary 指示条。
 */
object MaterialMcStyle : McStyle {

    override val id: String = "material"
    override val defaultColors: McColorScheme get() = MaterialColors

    private val hoverOverlay = Color(0x14FFFFFF)
    private val pressOverlay = Color(0x1FFFFFFF)

    private fun DrawScope.round(fill: Color, radius: Float) {
        drawRoundRect(fill, cornerRadius = CornerRadius(radius))
    }

    override fun DrawScope.panelChrome(colors: McColorScheme) {
        round(colors.panelBackground, 12f)
    }

    override fun DrawScope.closeButtonChrome(colors: McColorScheme) {
        round(colors.closeButtonBackground, size.minDimension / 2f)
    }

    override fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean) {
        val radius = size.height / 2f
        round(
            if (enabled) colors.buttonBackground else colors.buttonBackgroundDisabled,
            radius,
        )
        if (enabled && pressed) drawRoundRect(pressOverlay, cornerRadius = CornerRadius(radius))
        else if (enabled && hovered) drawRoundRect(hoverOverlay, cornerRadius = CornerRadius(radius))
    }

    override fun buttonLabelColor(colors: McColorScheme, enabled: Boolean, hovered: Boolean) =
        if (enabled) Color(0xFF381E72) else colors.textDisabled

    override fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean) {
        if (selected) {
            drawRect(colors.tabIndicator, topLeft = Offset(0f, size.height - 3f), size = Size(size.width, 3f))
        }
    }

    override fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean) {
        if (checked) {
            round(colors.buttonBackground, 2f)
        } else {
            drawRoundRect(colors.checkboxBorder, cornerRadius = CornerRadius(2f), style = Stroke(2f))
        }
    }

    override fun DrawScope.toggleTrackChrome(colors: McColorScheme, checked: Boolean) {
        round(if (checked) colors.toggleTrackOn else colors.toggleTrackOff, size.height / 2f)
    }

    override fun DrawScope.toggleThumbChrome(colors: McColorScheme, checked: Boolean) {
        round(colors.toggleThumb, size.minDimension / 2f)
    }

    override fun DrawScope.progressTrackChrome(colors: McColorScheme) {
        round(colors.progressTrack, size.height / 2f)
    }

    override fun DrawScope.progressFillChrome(colors: McColorScheme) {
        round(colors.progressFill, size.height / 2f)
    }

    override fun DrawScope.scrollbarTrackChrome(colors: McColorScheme) {
    }

    override fun DrawScope.scrollbarBarChrome(colors: McColorScheme) {
        round(colors.scrollbarBar, size.width / 2f)
    }

    override fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean) {
        round(colors.inputBackground, 4f)
        val indicator = if (focused) colors.inputBorderFocused else colors.inputBorder
        drawRect(
            indicator,
            topLeft = Offset(4f, size.height - if (focused) 2f else 1f),
            size = Size(size.width - 8f, if (focused) 2f else 1f),
        )
    }

    override fun DrawScope.tooltipChrome(colors: McColorScheme) {
        round(colors.tooltipBackground, 4f)
    }
}
