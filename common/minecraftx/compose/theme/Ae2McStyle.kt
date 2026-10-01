package minecraftx.compose.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AE2 1.21.1 新版界面风格配色:浅长春花蓝灰主题。全部取自 1.21.1 官方贴图实测:
 * 面板底 CBCCD4、描边藏青 413F54、内圈高光 F2F2F2;控件面 9A9FB4、亮环 ADB0C4、
 * 底部色带 696D88;悬停面 9CD3FF / 环 DAFFFF;强调色 DAFFFF。
 */
object Ae2Colors : McColorScheme {
    override val textPrimary: Color get() = Color(0xFF413F54)
    override val textSecondary: Color get() = Color(0xFF696D88)
    override val textDisabled: Color get() = Color(0xFF878FA5)

    override val panelBackground: Color get() = Color(0xFFCBCCD4)
    override val panelBorder: Color get() = Color(0xFF413F54)

    override val closeButtonBackground: Color get() = Color(0xFF9A9FB4)
    override val closeButtonBorder: Color get() = Color(0xFF413F54)

    override val contentBackground: Color get() = Color(0xFF9A9FB4)
    override val contentBorder: Color get() = Color(0xFF696D88)

    override val slotBackground: Color get() = Color(0xFF9A9FB4)
    override val slotBorder: Color get() = Color(0xFF696D88)
    override val slotHoverOverlay: Color get() = Color(0x66DAFFFF)

    override val inputBackground: Color get() = Color(0xFF9A9FB4)
    override val inputBorder: Color get() = Color(0xFF696D88)
    override val inputBorderFocused: Color get() = Color(0xFFDAFFFF)
    override val textCaret: Color get() = Color(0xFF413F54)
    override val textSelection: Color get() = Color(0xAA9CD3FF)

    override val scrollbarTrack: Color get() = Color(0x00000000)
    override val scrollbarBar: Color get() = Color(0xFF9A9FB4)

    override val tooltipBackground: Color get() = Color(0xF2CBCCD4)
    override val tooltipBorder: Color get() = Color(0xFF413F54)

    override val buttonBackground: Color get() = Color(0xFF9A9FB4)
    override val buttonBackgroundHovered: Color get() = Color(0xFF9CD3FF)
    override val buttonBackgroundPressed: Color get() = Color(0xFF9CD3FF)
    override val buttonBackgroundDisabled: Color get() = Color(0xFF696D88)
    override val buttonBorder: Color get() = Color(0xFF413F54)
    override val buttonBorderFocused: Color get() = Color(0xFFDAFFFF)

    override val tabBackground: Color get() = Color(0xFF9A9FB4)
    override val tabBackgroundSelected: Color get() = Color(0xFFCBCCD4)
    override val tabBorder: Color get() = Color(0xFF413F54)
    override val tabIndicator: Color get() = Color(0xFFDAFFFF)

    override val progressTrack: Color get() = Color(0xFF9A9FB4)
    override val progressFill: Color get() = Color(0xFF9CD3FF)

    override val checkboxBackground: Color get() = Color(0xFF9A9FB4)
    override val checkboxBorder: Color get() = Color(0xFF413F54)
    override val checkboxMark: Color get() = Color(0xFFDAFFFF)

    override val toggleTrackOff: Color get() = Color(0xFF9A9FB4)
    override val toggleTrackOn: Color get() = Color(0xFF9CD3FF)
    override val toggleThumb: Color get() = Color(0xFFF2F2F2)

    override val mdQuoteBar: Color get() = Color(0xFF696D88)
    override val mdHeadingAccent: Color get() = Color(0xFF413F54)
    override val mdLink: Color get() = Color(0xFF2A5F9E)
}

/**
 * AE2 1.21.1 新版风格,全程序化复刻官方贴图结构:
 *  - 面板(background.png):1px 413F54 外框 + 1px F2F2F2 内高光 + CBCCD4 面;
 *  - 按钮/滑块(button.png / small_scroller.png):413F54 外框 + ADB0C4 上/左/右内环 +
 *    9A9FB4 面 + 底部 696D88 色带;悬停 环 DAFFFF、面 9CD3FF、带 708CBA;禁用 面 696D88;
 *  - 槽位/输入框(text_field.png):1px F2F2F2 + 696D88 + 9A9FB4 凹陷;
 *  - 复选框:413F54 细框 + DAFFFF 对勾(强调色,取自官方 checkbox.png)。
 */
object Ae2McStyle : McStyle {

    override val id: String = "ae2"
    override val defaultColors: McColorScheme get() = Ae2Colors

    private val border = Color(0xFF413F54)
    private val ring = Color(0xFFADB0C4)
    private val face = Color(0xFF9A9FB4)
    private val band = Color(0xFF696D88)
    private val ringHover = Color(0xFFDAFFFF)
    private val faceHover = Color(0xFF9CD3FF)
    private val bandHover = Color(0xFF708CBA)
    private val faceDisabled = Color(0xFF696D88)
    private val ringDisabled = Color(0xFF878FA5)
    private val panelFace = Color(0xFFCBCCD4)
    private val panelInner = Color(0xFFF2F2F2)

    /** 按钮/滑块结构:外框 + 上左右内环 + 面 + 底部色带(20px 高时 3px,小尺寸等比收缩)。 */
    private fun DrawScope.ae2Box(faceC: Color, ringC: Color, bandC: Color) {
        val w = size.width
        val h = size.height
        val bandH = if (h >= 12f) 3f else 1f
        drawRect(border)
        drawRect(faceC, topLeft = Offset(1f, 1f), size = Size(w - 2f, h - 2f))
        drawRect(ringC, topLeft = Offset(1f, 1f), size = Size(w - 2f, 1f))
        drawRect(ringC, topLeft = Offset(1f, 1f), size = Size(1f, h - 1f - bandH))
        drawRect(ringC, topLeft = Offset(w - 2f, 1f), size = Size(1f, h - 1f - bandH))
        drawRect(bandC, topLeft = Offset(1f, h - 1f - bandH), size = Size(w - 2f, bandH))
    }

    /** 槽位/输入框的凹陷结构:F2F2F2 亮外圈 + 696D88 内圈 + 面。 */
    private fun DrawScope.ae2Inset(faceC: Color) {
        val w = size.width
        val h = size.height
        drawRect(panelInner)
        drawRect(band, topLeft = Offset(1f, 1f), size = Size(w - 2f, h - 2f))
        drawRect(faceC, topLeft = Offset(2f, 2f), size = Size(w - 4f, h - 4f))
    }

    override fun DrawScope.panelChrome(colors: McColorScheme) {
        drawRect(border)
        drawRect(panelInner, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
        drawRect(colors.panelBackground, topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 4f))
    }

    override fun DrawScope.closeButtonChrome(colors: McColorScheme) {
        ae2Box(face, ring, band)
    }

    override fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean) {
        when {
            !enabled -> ae2Box(faceDisabled, ringDisabled, border)
            hovered || pressed -> ae2Box(faceHover, ringHover, bandHover)
            else -> ae2Box(face, ring, band)
        }
    }

    override fun buttonLabelColor(colors: McColorScheme, enabled: Boolean, hovered: Boolean) =
        if (enabled) border else colors.textDisabled

    override fun DrawScope.slotChrome(colors: McColorScheme) {
        ae2Inset(colors.slotBackground)
    }

    override fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean) {
        if (selected) {
            drawRect(border)
            drawRect(panelInner, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 1f))
            drawRect(colors.tabBackgroundSelected, topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 2f))
        } else {
            ae2Box(face, ring, band)
        }
    }

    override fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(face)
        drawRect(border, style = Stroke(1f))
    }

    override fun DrawScope.toggleTrackChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(border)
        drawRect(
            if (checked) colors.toggleTrackOn else colors.toggleTrackOff,
            topLeft = Offset(1f, 1f),
            size = Size(size.width - 2f, size.height - 2f),
        )
    }

    override fun DrawScope.toggleThumbChrome(colors: McColorScheme, checked: Boolean) {
        drawRect(border)
        drawRect(colors.toggleThumb, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
    }

    override fun DrawScope.progressTrackChrome(colors: McColorScheme) {
        ae2Inset(colors.progressTrack)
    }

    override fun DrawScope.progressFillChrome(colors: McColorScheme) {
        drawRect(colors.progressFill)
    }

    override val scrollbarTrackWidth: Dp get() = 7.dp
    override val scrollbarBarWidth: Dp get() = 7.dp

    override fun DrawScope.scrollbarTrackChrome(colors: McColorScheme) {
        // 新版 AE2 不画轨道底,只有滑块。
    }

    override fun DrawScope.scrollbarBarChrome(colors: McColorScheme) {
        ae2Box(face, ring, band)
    }

    override fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean) {
        ae2Inset(colors.inputBackground)
        if (focused) {
            drawRect(colors.inputBorderFocused, style = Stroke(1f))
        }
    }

    override fun DrawScope.tooltipChrome(colors: McColorScheme) {
        drawRect(colors.tooltipBackground)
        drawRect(panelInner, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
        drawRect(colors.tooltipBackground, topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 4f))
        drawRect(colors.tooltipBorder, style = Stroke(1f))
    }
}
