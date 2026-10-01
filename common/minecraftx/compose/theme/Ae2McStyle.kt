package minecraftx.compose.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** AE2 1.21 新版界面风格的配色:深藏青面板 + 青色高亮。 */
object Ae2Colors : McColorScheme {
    override val textPrimary: Color get() = Color(0xFFD8DCE8)
    override val textSecondary: Color get() = Color(0xFF7E86A0)
    override val textDisabled: Color get() = Color(0xFF4A5064)

    override val panelBackground: Color get() = Color(0xFF1B1B29)
    override val panelBorder: Color get() = Color(0xFF2E2E48)

    override val closeButtonBackground: Color get() = Color(0xFF23233A)
    override val closeButtonBorder: Color get() = Color(0xFF3C3C5E)

    override val contentBackground: Color get() = Color(0xFF14141F)
    override val contentBorder: Color get() = Color(0xFF23233A)

    override val slotBackground: Color get() = Color(0xFF12121C)
    override val slotBorder: Color get() = Color(0xFF262640)
    override val slotHoverOverlay: Color get() = Color(0x2864C8FF)

    override val inputBackground: Color get() = Color(0xFF12121C)
    override val inputBorder: Color get() = Color(0xFF2E2E48)
    override val inputBorderFocused: Color get() = Color(0xFF64C8FF)
    override val textCaret: Color get() = Color(0xFFD8DCE8)
    override val textSelection: Color get() = Color(0x5564C8FF)

    override val scrollbarTrack: Color get() = Color(0x3312121C)
    override val scrollbarBar: Color get() = Color(0xFF3C3C5E)

    override val tooltipBackground: Color get() = Color(0xF2161624)
    override val tooltipBorder: Color get() = Color(0xFF3C3C5E)

    override val buttonBackground: Color get() = Color(0xFF23233A)
    override val buttonBackgroundHovered: Color get() = Color(0xFF2C2C48)
    override val buttonBackgroundPressed: Color get() = Color(0xFF1A1A2C)
    override val buttonBackgroundDisabled: Color get() = Color(0xFF1C1C2E)
    override val buttonBorder: Color get() = Color(0xFF3C3C5E)
    override val buttonBorderFocused: Color get() = Color(0xFF64C8FF)

    override val tabBackground: Color get() = Color(0xFF1C1C2E)
    override val tabBackgroundSelected: Color get() = Color(0xFF23233A)
    override val tabBorder: Color get() = Color(0xFF2E2E48)
    override val tabIndicator: Color get() = Color(0xFF64C8FF)

    override val progressTrack: Color get() = Color(0xFF12121C)
    override val progressFill: Color get() = Color(0xFF3FA88F)

    override val checkboxBackground: Color get() = Color(0xFF12121C)
    override val checkboxBorder: Color get() = Color(0xFF2E2E48)
    override val checkboxMark: Color get() = Color(0xFF64C8FF)

    override val toggleTrackOff: Color get() = Color(0xFF23233A)
    override val toggleTrackOn: Color get() = Color(0xFF2E6E8E)
    override val toggleThumb: Color get() = Color(0xFFD8DCE8)

    override val mdQuoteBar: Color get() = Color(0xFF64C8FF)
    override val mdHeadingAccent: Color get() = Color(0xFF64C8FF)
    override val mdLink: Color get() = Color(0xFF7FB3E0)
}

/**
 * AE2 1.21 新版风格:深藏青底色 + 细边框,控件顶部带一条半透高光(新版 AE2 面板的标志性
 * 层次),聚焦/悬停统一用青色高亮。
 */
object Ae2McStyle : McStyle {

    override val id: String = "ae2"
    override val defaultColors: McColorScheme get() = Ae2Colors

    private val topHighlight = Color(0x14FFFFFF)

    /** 底板 + 1px 边框 + 顶部 1px 高光。 */
    private fun DrawScope.chip(fill: Color, border: Color) {
        drawRect(fill)
        drawRect(border, style = Stroke(1f))
        drawRect(topHighlight, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, 1f))
    }

    override fun DrawScope.panelChrome(colors: McColorScheme) {
        chip(colors.panelBackground, colors.panelBorder)
    }

    override fun DrawScope.closeButtonChrome(colors: McColorScheme) {
        chip(colors.closeButtonBackground, colors.closeButtonBorder)
    }

    override fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean) {
        chip(
            when {
                !enabled -> colors.buttonBackgroundDisabled
                pressed -> colors.buttonBackgroundPressed
                hovered -> colors.buttonBackgroundHovered
                else -> colors.buttonBackground
            },
            if (hovered && enabled) colors.buttonBorderFocused else colors.buttonBorder,
        )
    }

    override fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean) {
        chip(if (selected) colors.tabBackgroundSelected else colors.tabBackground, colors.tabBorder)
        if (selected) {
            drawRect(colors.tabIndicator, topLeft = Offset(0f, size.height - 2f), size = Size(size.width, 2f))
        }
    }

    override fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean) {
        chip(colors.checkboxBackground, colors.checkboxBorder)
    }

    override fun DrawScope.toggleTrackChrome(colors: McColorScheme, checked: Boolean) {
        chip(if (checked) colors.toggleTrackOn else colors.toggleTrackOff, colors.buttonBorder)
    }

    override fun DrawScope.toggleThumbChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(colors.toggleThumb)
    }

    override fun DrawScope.progressTrackChrome(colors: McColorScheme) {
        chip(colors.progressTrack, colors.buttonBorder)
    }

    override fun DrawScope.progressFillChrome(colors: McColorScheme) {
        drawRect(colors.progressFill)
    }

    override fun DrawScope.scrollbarTrackChrome(colors: McColorScheme) {
        drawRect(colors.scrollbarTrack)
    }

    override fun DrawScope.scrollbarBarChrome(colors: McColorScheme) {
        drawRect(colors.scrollbarBar)
        drawRect(topHighlight, topLeft = Offset(0f, 0f), size = Size(size.width, 1f))
    }

    override fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean) {
        drawRect(if (focused) colors.inputBorderFocused else colors.inputBorder, style = Stroke(1f))
        drawRect(colors.inputBackground, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
    }

    override fun DrawScope.tooltipChrome(colors: McColorScheme) {
        chip(colors.tooltipBackground, colors.tooltipBorder)
    }
}
