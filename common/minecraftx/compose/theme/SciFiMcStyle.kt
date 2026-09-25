package minecraftx.compose.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** 科幻风格配色:近黑蓝底 + 霓虹青主色 + 琥珀色辅助。 */
object SciFiColors : McColorScheme {
    override val textPrimary: Color get() = Color(0xFFC8F4F8)
    override val textSecondary: Color get() = Color(0xFF5E8E98)
    override val textDisabled: Color get() = Color(0xFF33555C)

    override val panelBackground: Color get() = Color(0xF20A1016)
    override val panelBorder: Color get() = Color(0xFF35E0E8)

    override val closeButtonBackground: Color get() = Color(0xFF0E1A22)
    override val closeButtonBorder: Color get() = Color(0xFF35E0E8)

    override val contentBackground: Color get() = Color(0xE6080C11)
    override val contentBorder: Color get() = Color(0xFF14404A)

    override val slotBackground: Color get() = Color(0xFF081018)
    override val slotBorder: Color get() = Color(0xFF14404A)
    override val slotHoverOverlay: Color get() = Color(0x2835E0E8)

    override val inputBackground: Color get() = Color(0xFF081018)
    override val inputBorder: Color get() = Color(0xFF14404A)
    override val inputBorderFocused: Color get() = Color(0xFF35E0E8)
    override val textCaret: Color get() = Color(0xFF35E0E8)
    override val textSelection: Color get() = Color(0x5535E0E8)

    override val scrollbarTrack: Color get() = Color(0x33101820)
    override val scrollbarBar: Color get() = Color(0xFF35E0E8)

    override val tooltipBackground: Color get() = Color(0xF20A1016)
    override val tooltipBorder: Color get() = Color(0xFF35E0E8)

    override val buttonBackground: Color get() = Color(0xFF0E1A22)
    override val buttonBackgroundHovered: Color get() = Color(0xFF122630)
    override val buttonBackgroundPressed: Color get() = Color(0xFF08131A)
    override val buttonBackgroundDisabled: Color get() = Color(0xFF0A1218)
    override val buttonBorder: Color get() = Color(0xFF1E5A64)
    override val buttonBorderFocused: Color get() = Color(0xFF35E0E8)

    override val tabBackground: Color get() = Color(0xFF0A1218)
    override val tabBackgroundSelected: Color get() = Color(0xFF122630)
    override val tabBorder: Color get() = Color(0xFF1E5A64)
    override val tabIndicator: Color get() = Color(0xFF35E0E8)

    override val progressTrack: Color get() = Color(0xFF081018)
    override val progressFill: Color get() = Color(0xFF35E0E8)

    override val checkboxBackground: Color get() = Color(0xFF081018)
    override val checkboxBorder: Color get() = Color(0xFF1E5A64)
    override val checkboxMark: Color get() = Color(0xFF35E0E8)

    override val toggleTrackOff: Color get() = Color(0xFF0E1A22)
    override val toggleTrackOn: Color get() = Color(0xFF144A54)
    override val toggleThumb: Color get() = Color(0xFF35E0E8)

    override val mdCodeBackground: Color get() = Color(0xFF08131A)
    override val mdCodeText: Color get() = Color(0xFFFFB84A)
    override val mdQuoteBar: Color get() = Color(0xFF35E0E8)
    override val mdRuleLine: Color get() = Color(0xFF14404A)
    override val mdHeadingAccent: Color get() = Color(0xFF35E0E8)
    override val mdLink: Color get() = Color(0xFF6BE8F0)
}

/**
 * 科幻风格:近黑蓝底 + 霓虹青辉光边框(内圈亮线 + 外圈半透明光晕),全部直角;
 * 面板四角带短促的"切角"高亮刻度线,强化 HUD 感。
 */
object SciFiMcStyle : McStyle {

    override val id: String = "scifi"
    override val defaultColors: McColorScheme get() = SciFiColors

    private fun DrawScope.glowBox(fill: Color, accent: Color) {
        drawRect(fill)
        drawRect(accent.copy(alpha = 0.25f), style = Stroke(3f))
        drawRect(accent, style = Stroke(1f))
    }

    /** 四角 4px 刻度线。 */
    private fun DrawScope.cornerTicks(color: Color) {
        val t = 4f
        drawLine(color, Offset(0.5f, 0.5f), Offset(0.5f + t, 0.5f))
        drawLine(color, Offset(0.5f, 0.5f), Offset(0.5f, 0.5f + t))
        drawLine(color, Offset(size.width - 0.5f, 0.5f), Offset(size.width - 0.5f - t, 0.5f))
        drawLine(color, Offset(size.width - 0.5f, 0.5f), Offset(size.width - 0.5f, 0.5f + t))
        drawLine(color, Offset(0.5f, size.height - 0.5f), Offset(0.5f + t, size.height - 0.5f))
        drawLine(color, Offset(0.5f, size.height - 0.5f), Offset(0.5f, size.height - 0.5f - t))
        drawLine(color, Offset(size.width - 0.5f, size.height - 0.5f), Offset(size.width - 0.5f - t, size.height - 0.5f))
        drawLine(color, Offset(size.width - 0.5f, size.height - 0.5f), Offset(size.width - 0.5f, size.height - 0.5f - t))
    }

    override fun DrawScope.panelChrome(colors: McColorScheme) {
        glowBox(colors.panelBackground, colors.panelBorder.copy(alpha = 0.6f))
        cornerTicks(colors.panelBorder)
    }

    override fun DrawScope.closeButtonChrome(colors: McColorScheme) {
        glowBox(colors.closeButtonBackground, colors.closeButtonBorder)
    }

    override fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean) {
        glowBox(
            when {
                !enabled -> colors.buttonBackgroundDisabled
                pressed -> colors.buttonBackgroundPressed
                hovered -> colors.buttonBackgroundHovered
                else -> colors.buttonBackground
            },
            if (hovered && enabled) colors.buttonBorderFocused else colors.buttonBorder,
        )
        if (hovered && enabled) cornerTicks(colors.buttonBorderFocused)
    }

    override fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean) {
        drawRect(if (selected) colors.tabBackgroundSelected else colors.tabBackground)
        drawRect(colors.tabBorder, style = Stroke(1f))
        if (selected) {
            drawRect(colors.tabIndicator, topLeft = Offset(0f, size.height - 2f), size = Size(size.width, 2f))
            cornerTicks(colors.tabIndicator)
        }
    }

    override fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(colors.checkboxBackground)
        drawRect(if (checked) colors.checkboxMark else colors.checkboxBorder, style = Stroke(1f))
        if (checked) drawRect(colors.checkboxMark.copy(alpha = 0.25f), style = Stroke(3f))
    }

    override fun DrawScope.toggleTrackChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(if (checked) colors.toggleTrackOn else colors.toggleTrackOff)
        drawRect(colors.buttonBorder, style = Stroke(1f))
    }

    override fun DrawScope.toggleThumbChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(colors.toggleThumb)
        if (checked) drawRect(colors.toggleThumb.copy(alpha = 0.3f), style = Stroke(2f))
    }

    override fun DrawScope.progressTrackChrome(colors: McColorScheme) {
        drawRect(colors.progressTrack)
        drawRect(colors.contentBorder, style = Stroke(1f))
    }

    override fun DrawScope.progressFillChrome(colors: McColorScheme) {
        drawRect(colors.progressFill)
        drawRect(Color(0x66FFFFFF), topLeft = Offset(0f, 0f), size = Size(size.width, 1f))
    }

    override fun DrawScope.scrollbarTrackChrome(colors: McColorScheme) {
        drawRect(colors.scrollbarTrack)
    }

    override fun DrawScope.scrollbarBarChrome(colors: McColorScheme) {
        drawRect(colors.scrollbarBar.copy(alpha = 0.8f))
        drawRect(colors.scrollbarBar, style = Stroke(1f))
    }

    override fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean) {
        val accent = if (focused) colors.inputBorderFocused else colors.inputBorder
        drawRect(colors.inputBackground)
        drawRect(accent, style = Stroke(1f))
        if (focused) cornerTicks(accent)
    }

    override fun DrawScope.tooltipChrome(colors: McColorScheme) {
        glowBox(colors.tooltipBackground, colors.tooltipBorder)
    }
}
