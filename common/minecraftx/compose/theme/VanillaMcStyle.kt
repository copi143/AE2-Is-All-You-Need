package minecraftx.compose.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/** MC 原版界面风格的配色:浅灰面板(0xC6C6C6) + 深色文字,按钮按原版贴图实测像素程序化复刻。 */
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
    override val textCaret: Color get() = Color(0xFFD0D0D0)
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
 * MC 原版风格,全程序化渲染(不用贴图)。按钮按 widgets.png 实测像素复刻:1px 黑色外框
 * (悬停白色)+ 内圈上/左亮线、下/右暗线 + 平面灰底 —— 常态 底 0x6F6F6F / 亮 0xAAAAAA /
 * 暗 0x565656,悬停 底 0x757575 / 亮 0xAFAFAF / 暗 0x5E5E5E,禁用 底 0x2B2B2B;物品槽为
 * 经典凹陷斜面(上/左 0x373737,下/右 白,填 0x8B8B8B);tooltip 用原版渐变(底
 * 0xF0100010,边框上 0x505000FF → 下 0x5028007F);其余控件保持程序化斜面。
 */
object VanillaMcStyle : McStyle {

    override val id: String = "vanilla"
    override val defaultColors: McColorScheme get() = VanillaColors

    private val bevelLight = Color(0xFFFFFFFF)
    private val bevelDark = Color(0xFF555555)

    private val gradientPaint by lazy { androidx.compose.ui.graphics.Paint() }


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

    /**
     * 九宫格 blit:源区域 (u, v, regionW x regionH),边距 (borderX x borderY),目标为整个
     * 节点区域。Nearest 采样,保持像素风。
     */

    /** 垂直渐变矩形(逐顶点颜色,经 Canvas.drawVertices 走逐顶点着色管线)。 */
    private fun DrawScope.gradientQuad(l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        if (r <= l || b <= t) return
        drawIntoCanvas { canvas ->
            canvas.drawVertices(
                Vertices(
                    VertexMode.Triangles,
                    listOf(
                        Offset(l, t), Offset(r, t), Offset(l, b),
                        Offset(r, t), Offset(r, b), Offset(l, b),
                    ),
                    List(6) { Offset.Zero },
                    // 顶点序:LT RT LB / RT RB LB —— 顶行用 top,底行用 bottom
                    listOf(
                        Color(top), Color(top), Color(bottom),
                        Color(top), Color(bottom), Color(bottom),
                    ),
                    listOf(),
                ),
                BlendMode.SrcOver,
                gradientPaint,
            )
        }
    }

    override fun DrawScope.panelChrome(colors: McColorScheme) {
        bevel(colors.panelBackground, colors.panelBorder, raised = true)
    }

    override fun DrawScope.closeButtonChrome(colors: McColorScheme) {
        bevel(colors.closeButtonBackground, colors.closeButtonBorder, raised = true)
    }

    override fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean) {
        // widgets.png 实测配方:1px 外框 + 内圈上/左亮线、下/右暗线 + 平灰底(按下沿用悬停态)。
        val border: Int
        val face: Int
        val tl: Int
        val br: Int
        when {
            !enabled -> {
                border = 0xFF000000.toInt(); face = 0xFF2B2B2B.toInt(); tl = 0xFF2B2B2B.toInt(); br = 0xFF2B2B2B.toInt()
            }
            hovered || pressed -> {
                border = 0xFFFFFFFF.toInt(); face = 0xFF757575.toInt(); tl = 0xFFAFAFAF.toInt(); br = 0xFF5E5E5E.toInt()
            }
            else -> {
                border = 0xFF000000.toInt(); face = 0xFF6F6F6F.toInt(); tl = 0xFFAAAAAA.toInt(); br = 0xFF565656.toInt()
            }
        }
        val w = size.width
        val h = size.height
        drawRect(Color(border))
        drawRect(Color(face), topLeft = Offset(1f, 1f), size = Size(w - 2f, h - 2f))
        drawRect(Color(tl), topLeft = Offset(1f, 1f), size = Size(w - 2f, 1f))
        drawRect(Color(tl), topLeft = Offset(1f, 1f), size = Size(1f, h - 2f))
        drawRect(Color(br), topLeft = Offset(1f, h - 2f), size = Size(w - 2f, 1f))
        drawRect(Color(br), topLeft = Offset(w - 2f, 1f), size = Size(1f, h - 2f))
    }

    override fun buttonLabelColor(colors: McColorScheme, enabled: Boolean, hovered: Boolean) =
        if (enabled) Color(0xFFFFFFFF) else Color(0xFFA0A0A0)

    override val buttonLabelShadow: Boolean get() = true

    override fun DrawScope.slotChrome(colors: McColorScheme) {
        // 原版容器槽位:凹陷斜面(上/左深、下/右亮)。
        drawRect(Color(0xFF8B8B8B))
        drawLine(Color(0xFF373737), Offset(0f, 0f), Offset(size.width, 0f))
        drawLine(Color(0xFF373737), Offset(0f, 0f), Offset(0f, size.height))
        drawLine(Color(0xFFFFFFFF), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
        drawLine(Color(0xFFFFFFFF), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height))
    }

    override fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean) {
        bevel(
            if (selected) colors.tabBackgroundSelected else colors.tabBackground,
            colors.tabBorder,
            raised = true,
        )
    }

    override fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean) {
        // 原版 checkbox.png:1px 黑框 + 平深灰底(0x2B2B2B),无斜面;✓ 由组件叠加。
        drawRect(Color(0xFF2B2B2B))
        drawRect(Color(0xFF000000), style = Stroke(1f))
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

    override val scrollbarTrackWidth: androidx.compose.ui.unit.Dp get() = androidx.compose.ui.unit.Dp(8f)
    override val scrollbarBarWidth: androidx.compose.ui.unit.Dp get() = androidx.compose.ui.unit.Dp(8f)

    override fun DrawScope.scrollbarTrackChrome(colors: McColorScheme) {
        // 原版(AbstractScrollWidget)不画轨道底,只有滑块。
    }

    override fun DrawScope.scrollbarBarChrome(colors: McColorScheme) {
        // 原版滑块:0x808080 外圈 + 内缩 1px 的 0xC0C0C0。
        drawRect(Color(0xFF808080))
        drawRect(
            Color(0xFFC0C0C0),
            topLeft = Offset(0f, 0f),
            size = Size(size.width - 1f, size.height - 1f),
        )
    }

    override fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean) {
        drawRect(if (focused) colors.inputBorderFocused else colors.inputBorder, style = Stroke(1f))
        drawRect(colors.inputBackground, topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
    }

    override fun DrawScope.tooltipChrome(colors: McColorScheme) {
        // 原版 TooltipRenderUtil:底 0xF0100010,边框上 0x505000FF → 下 0x5028007F 垂直渐变。
        val borderTop = 0x505000FF
        val borderBottom = 0x5028007F
        drawRect(Color(0xF0100010))
        gradientQuad(1f, 0f, size.width - 1f, 1f, borderTop, borderTop)
        gradientQuad(1f, size.height - 1f, size.width - 1f, size.height, borderBottom, borderBottom)
        gradientQuad(0f, 1f, 1f, size.height - 1f, borderTop, borderBottom)
        gradientQuad(size.width - 1f, 1f, size.width, size.height - 1f, borderTop, borderBottom)
    }
}
