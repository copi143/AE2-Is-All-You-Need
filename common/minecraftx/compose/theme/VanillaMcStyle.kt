package minecraftx.compose.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** MC 原版界面风格的配色:浅灰面板(0xC6C6C6) + 深色文字,按钮文字白色带悬停变黄。 */
object VanillaColors : McColorScheme {
    override val textPrimary: Color get() = Color(0xFF3F3F3F)
    override val textSecondary: Color get() = Color(0xFF6E6E6E)
    override val textDisabled: Color get() = Color(0xFFA0A0A0)

    override val panelBackground: Color get() = Color(0xFFC6C6C6)
    override val panelBorder: Color get() = Color(0xFF000000)

    override val closeButtonBackground: Color get() = Color(0xFFC6C6C6)
    override val closeButtonBorder: Color get() = Color(0xFF000000)

    override val contentBackground: Color get() = Color(0xFFC6C6C6)
    override val contentBorder: Color get() = Color(0xFF555555)

    override val slotBackground: Color get() = Color(0xFF8B8B8B)
    override val slotBorder: Color get() = Color(0xFF373737)
    override val slotHoverOverlay: Color get() = Color(0x80FFFFFF)

    override val inputBackground: Color get() = Color(0xFF000000)
    override val inputBorder: Color get() = Color(0xFFA0A0A0)
    override val inputBorderFocused: Color get() = Color(0xFFFFFFFF)
    override val textCaret: Color get() = Color(0xFFFFFFFF)
    override val textSelection: Color get() = Color(0xFF3333AA)

    override val scrollbarTrack: Color get() = Color(0xFF9A9A9A)
    override val scrollbarBar: Color get() = Color(0xFF6E6E6E)

    override val tooltipBackground: Color get() = Color(0xF0100010)
    override val tooltipBorder: Color get() = Color(0xFF7B2FBE)

    override val buttonBackground: Color get() = Color(0xFFC6C6C6)
    override val buttonBackgroundHovered: Color get() = Color(0xFFC6C6C6)
    override val buttonBackgroundPressed: Color get() = Color(0xFFADADAD)
    override val buttonBackgroundDisabled: Color get() = Color(0xFFBABABA)
    override val buttonBorder: Color get() = Color(0xFF000000)
    override val buttonBorderFocused: Color get() = Color(0xFFFFFFFF)

    override val tabBackground: Color get() = Color(0xFFA8A8A8)
    override val tabBackgroundSelected: Color get() = Color(0xFFC6C6C6)
    override val tabBorder: Color get() = Color(0xFF000000)
    override val tabIndicator: Color get() = Color(0xFFFFFFFF)

    override val progressTrack: Color get() = Color(0xFF8B8B8B)
    override val progressFill: Color get() = Color(0xFF7EBB42)

    override val checkboxBackground: Color get() = Color(0xFF8B8B8B)
    override val checkboxBorder: Color get() = Color(0xFF000000)
    override val checkboxMark: Color get() = Color(0xFFFFFFFF)

    override val toggleTrackOff: Color get() = Color(0xFF8B8B8B)
    override val toggleTrackOn: Color get() = Color(0xFF7EBB42)
    override val toggleThumb: Color get() = Color(0xFFC6C6C6)
}

/**
 * MC 原版风格:经典斜面控件 —— 1px 黑色外框 + 上/左亮线、下/右暗线构成的"凸起"观感,
 * 按下时斜面反转(凹陷);按钮悬停文字变黄(原版行为)。
 */
object VanillaMcStyle : McStyle {

    override val id: String = "vanilla"
    override val defaultColors: McColorScheme get() = VanillaColors

    private val bevelLight = Color(0xFFFFFFFF)
    private val bevelDark = Color(0xFF555555)

    private fun DrawScope.bevel(fill: Color, border: Color, raised: Boolean) {
        drawRect(fill)
        drawRect(border, style = Stroke(1f))
        val tl = if (raised) bevelLight else bevelDark
        val br = if (raised) bevelDark else bevelLight
        drawLine(tl, Offset(1f, 1f), Offset(size.width - 1f, 1f))
        drawLine(tl, Offset(1f, 1f), Offset(1f, size.height - 1f))
        drawLine(br, Offset(1f, size.height - 1f), Offset(size.width - 1f, size.height - 1f))
        drawLine(br, Offset(size.width - 1f, 1f), Offset(size.width - 1f, size.height - 1f))
    }

    override fun DrawScope.panelChrome(colors: McColorScheme) {
        bevel(colors.panelBackground, colors.panelBorder, raised = true)
    }

    override fun DrawScope.closeButtonChrome(colors: McColorScheme) {
        bevel(colors.closeButtonBackground, colors.closeButtonBorder, raised = true)
    }

    override fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean) {
        bevel(
            when {
                !enabled -> colors.buttonBackgroundDisabled
                pressed -> colors.buttonBackgroundPressed
                else -> colors.buttonBackground
            },
            if (hovered && enabled) colors.buttonBorderFocused else colors.buttonBorder,
            raised = !pressed,
        )
    }

    override fun buttonLabelColor(colors: McColorScheme, enabled: Boolean, hovered: Boolean) = when {
        !enabled -> colors.textDisabled
        hovered -> Color(0xFFFFFFA0)
        else -> Color(0xFFE0E0E0)
    }

    override fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean) {
        bevel(
            if (selected) colors.tabBackgroundSelected else colors.tabBackground,
            colors.tabBorder,
            raised = true,
        )
    }

    override fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean) {
        bevel(colors.checkboxBackground, colors.checkboxBorder, raised = false)
    }

    override fun DrawScope.toggleTrackChrome(colors: McColorScheme, checked: Boolean) {
        bevel(if (checked) colors.toggleTrackOn else colors.toggleTrackOff, colors.buttonBorder, raised = false)
    }

    override fun DrawScope.toggleThumbChrome(colors: McColorScheme, checked: Boolean) {
        bevel(colors.toggleThumb, colors.buttonBorder, raised = true)
    }

    override fun DrawScope.progressTrackChrome(colors: McColorScheme) {
        bevel(colors.progressTrack, colors.buttonBorder, raised = false)
    }

    override fun DrawScope.progressFillChrome(colors: McColorScheme) {
        drawRect(colors.progressFill)
    }

    override fun DrawScope.scrollbarTrackChrome(colors: McColorScheme) {
        drawRect(colors.scrollbarTrack)
    }

    override fun DrawScope.scrollbarBarChrome(colors: McColorScheme) {
        bevel(colors.scrollbarBar, colors.buttonBorder, raised = true)
    }

    override fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean) {
        drawRect(if (focused) colors.inputBorderFocused else colors.inputBorder, style = Stroke(1f))
        drawRect(colors.inputBackground, topLeft = Offset(1f, 1f), size = androidx.compose.ui.geometry.Size(size.width - 2f, size.height - 2f))
    }

    override fun DrawScope.tooltipChrome(colors: McColorScheme) {
        drawRect(colors.tooltipBackground)
        drawRect(colors.tooltipBorder, style = Stroke(1f))
    }
}
