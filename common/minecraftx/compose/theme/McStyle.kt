package minecraftx.compose.theme

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 风格策略:完整主题 = 风格策略(McStyle,决定组件的渲染方式和行为属性) + 配色策略
 * ([McColorScheme],决定语义颜色)。组件只负责布局与交互,所有"皮肤像素"(背景、边框、
 * 装饰线)都委托给当前风格的 chrome 绘制函数,因此不同风格可以让同一组件呈现完全不同的
 * 外观(直角/圆角、平面/斜面/发光、有/无边框)。
 *
 * 所有 chrome 函数以 [DrawScope] 为接收者,在组件的 drawBehind 中调用;`size` 即节点尺寸。
 * 全局风格由 [McThemeSettings.style] 提供,[McTheme] 可局部覆盖。
 */
interface McStyle {

    /** 稳定 id,用于配置持久化("minimal"、"vanilla"、"ae2"、"material"、"scifi")。 */
    val id: String

    /** 该风格的默认配色;未显式指定配色时 [McTheme] 使用它。 */
    val defaultColors: McColorScheme

    /** 面板(卡片/窗口)外框。 */
    fun DrawScope.panelChrome(colors: McColorScheme)

    /** 面板右上角的 ✕ 关闭按钮。 */
    fun DrawScope.closeButtonChrome(colors: McColorScheme)

    /** 按钮外框;文本颜色经 [buttonLabelColor] 由风格决定。 */
    fun DrawScope.buttonChrome(colors: McColorScheme, enabled: Boolean, hovered: Boolean, pressed: Boolean)

    /** 按钮文本颜色(原版风格悬停时变黄,Material 用 onPrimary 等)。 */
    fun buttonLabelColor(colors: McColorScheme, enabled: Boolean, hovered: Boolean) =
        if (enabled) colors.textPrimary else colors.textDisabled

    /** 标签页外框;[selected] 为当前选中页。 */
    fun DrawScope.tabChrome(colors: McColorScheme, selected: Boolean)

    /** 复选框 10x10 外框(不含 ✓ 字形)。 */
    fun DrawScope.checkboxChrome(colors: McColorScheme, checked: Boolean)

    /** 开关滑轨(20x10)。 */
    fun DrawScope.toggleTrackChrome(colors: McColorScheme, checked: Boolean)

    /** 开关滑块(8x8)。 */
    fun DrawScope.toggleThumbChrome(colors: McColorScheme, checked: Boolean)

    /** 进度条轨道。 */
    fun DrawScope.progressTrackChrome(colors: McColorScheme)

    /** 进度条填充。 */
    fun DrawScope.progressFillChrome(colors: McColorScheme)

    /** 滚动条轨道。 */
    fun DrawScope.scrollbarTrackChrome(colors: McColorScheme)

    /** 滚动条滑块。 */
    fun DrawScope.scrollbarBarChrome(colors: McColorScheme)

    /** 文本输入框外框(多行框同用)。 */
    fun DrawScope.inputChrome(colors: McColorScheme, focused: Boolean)

    /** 提示框外框。 */
    fun DrawScope.tooltipChrome(colors: McColorScheme)
}

/** 内置风格注册表;[byId] 未命中时回落到 [MinimalMcStyle]。 */
object McStyles {
    val minimal = MinimalMcStyle
    val vanilla = VanillaMcStyle
    val ae2 = Ae2McStyle
    val material = MaterialMcStyle
    val scifi = SciFiMcStyle

    val all: List<McStyle> = listOf(minimal, vanilla, ae2, material, scifi)

    fun byId(id: String?): McStyle = all.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: minimal
}
