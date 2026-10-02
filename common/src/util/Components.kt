package allyouneed.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

/** 当前状态的级别；描述设备或物品现在的情况，风险与操作后果使用 [Components.warning]。 */
enum class ComponentStatusLevel {
    /** 常规状态，例如当前模式、已连接的网络。 */
    NORMAL,

    /** 需要注意但尚可使用，例如存储接近上限。 */
    ATTENTION,

    /** 当前故障或无法工作，例如缺少频道、网络断开。 */
    ERROR,
}

/**
 * Tooltip 的语义样式；使用 [copy] 定制部分样式，其余沿用原主题。
 *
 * 不同语义可以使用相同颜色，各字段仍可独立配置。
 * 样式中未指定的属性继承外层样式；显式设置 false 可以关闭继承的装饰。
 * [statusAttention]、[statusError] 在 [status] 上叠加，其他语义直接在当前作用域上叠加。
 */
data class ComponentTheme(
    /** 所有追加内容的基础样式。 */
    val base: Style = Style.EMPTY,
    /** 标题或分节标题，提供视觉层级。 */
    val title: Style = Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true),
    /** 中性的背景简介：这是什么。 */
    val description: Style = Style.EMPTY.withColor(ChatFormatting.GRAY),
    /** 完成操作所需的方法与步骤。 */
    val usage: Style = Style.EMPTY.withColor(ChatFormatting.YELLOW),
    /** 物品或方块的核心功能：能做什么。 */
    val feature: Style = Style.EMPTY.withColor(ChatFormatting.AQUA),
    /** 相比普通版的增强；默认与功能同色，但可独立定制。 */
    val enhancement: Style = Style.EMPTY.withColor(ChatFormatting.AQUA),
    /** 使用前提、兼容条件与能力边界，例如需要频道或仅支持处理样板。 */
    val requirement: Style = Style.EMPTY.withColor(ChatFormatting.YELLOW),
    /** 影响计算和选择的机制，例如配方用量、能耗、匹配与舍入规则。 */
    val rule: Style = Style.EMPTY.withColor(ChatFormatting.GRAY),
    /** 当前模式、绑定对象等动态信息，也是所有状态级别的基础样式。 */
    val status: Style = Style.EMPTY.withColor(ChatFormatting.WHITE),
    /** 需要注意的状态，叠加在 [status] 上。 */
    val statusAttention: Style = Style.EMPTY.withColor(ChatFormatting.YELLOW),
    /** 当前故障，叠加在 [status] 上。 */
    val statusError: Style = Style.EMPTY.withColor(ChatFormatting.RED),
    /** 辅助操作或发现提示，例如按住 Shift 展开说明。 */
    val hint: Style = Style.EMPTY.withColor(ChatFormatting.YELLOW),
    /** 用于辨认、排查的次要详情，例如 MAC 地址、网络 ID、所属维度。 */
    val detail: Style = Style.EMPTY.withColor(ChatFormatting.DARK_GRAY),
    /** 损失、破坏或不可逆操作等风险与后果。 */
    val warning: Style = Style.EMPTY.withColor(ChatFormatting.RED).withBold(true),
)

@DslMarker
private annotation class ComponentDsl

/**
 * 按语义构建文本。语义块仅设置样式，文字、换行、编号和缩进由调用处提供。
 *
 * 基础主题 → 外层块 → 内层块 → 组件自身显式样式 → 链式 editor，后者覆盖前者的同名属性。
 * 块退出（包括异常退出）后恢复外层样式；同一行内可以混合多个语义。
 */
@ComponentDsl
@Suppress("NOTHING_TO_INLINE")
class Components @PublishedApi internal constructor(val theme: ComponentTheme) {
    private val lines: ArrayList<MutableComponent> = ArrayList()
    private var current: MutableComponent = Component.empty()
    private var dirty: Boolean = false

    @PublishedApi
    internal var currentStyle: Style = theme.base

    @PublishedApi
    internal var currentNamespace: String? = null

    val newLine
        get() = run {
            lines.add(current)
            current = Component.empty()
            dirty = false
        }

    inline fun str(str: String): MutableComponent = Component.literal(str)
    /** 按当前 [namespace] 展开翻译键，参数原样传递；没有命名空间或没有 `/` 时使用原键。 */
    inline fun l10n(l10n: String, vararg args: Any?): MutableComponent = Component.translatable(resolveTranslationKey(l10n), *args)

    inline fun line(line: Component) = appendLine(line.copy())
    inline fun line(line: String) = appendLine(str(line))
    inline fun l10nLine(l10nLine: String, vararg args: Any?) = appendLine(l10n(l10nLine, *args))

    inline fun text(text: Component) = appendText(text.copy())
    inline fun text(text: String) = appendText(str(text))
    inline fun l10nText(l10nText: String, vararg args: Any?) = appendText(l10n(l10nText, *args))

    inline fun lines(components: Iterable<Component>) {
        components.forEach { line(it) }
    }

    /**
     * 在作用域内用 `/` 标记翻译命名空间的插入位置，适用于 [l10n]、[l10nLine]、[l10nText]。
     *
     * 例如命名空间为 `xxx.yyy`：`aaa/bbb.ccc` → `aaa.xxx.yyy.bbb.ccc`，
     * `/aaa` → `xxx.yyy.aaa`，`aaa/` → `aaa.xxx.yyy`，`/` → `xxx.yyy`。
     * 每个 `/` 都插入一次命名空间，并用点连接非空部分；不含 `/` 的键保持原样。
     * 嵌套块替换外层命名空间，退出（包括异常退出）后恢复；空字符串表示插入空命名空间。
     * 仅影响本构建器创建的翻译组件，不改写传入的 Component 或翻译参数。
     */
    inline fun namespace(namespace: String, builder: Components.() -> Unit) {
        val previous = currentNamespace
        currentNamespace = namespace
        try {
            builder()
        } finally {
            currentNamespace = previous
        }
    }

    @PublishedApi
    internal fun resolveTranslationKey(key: String): String {
        val namespace = currentNamespace ?: return key
        if ('/' !in key) return key
        return key.split('/').flatMapIndexed { index, part ->
            if (index == 0) listOf(part) else listOf(namespace, part)
        }.filter { it.isNotEmpty() }.joinToString(".")
    }

    /** 标题或分节标题。 */
    inline fun title(builder: Components.() -> Unit) = style(theme.title, builder)
    /** 背景简介：这是什么；短 tooltip 可直接使用 [feature]，避免重复说明。 */
    inline fun description(builder: Components.() -> Unit) = style(theme.description, builder)
    /** 使用方法及操作步骤；步骤的编号、缩进由调用处提供。 */
    inline fun usage(builder: Components.() -> Unit) = style(theme.usage, builder)
    /** 核心功能：能做什么。 */
    inline fun feature(builder: Components.() -> Unit) = style(theme.feature, builder)
    /** 相比普通版的增强。 */
    inline fun enhancement(builder: Components.() -> Unit) = style(theme.enhancement, builder)
    /** 使用前提与限制；“需要供电”属于条件，“断电会丢失进度”属于 [warning]。 */
    inline fun requirement(builder: Components.() -> Unit) = style(theme.requirement, builder)
    /** 配方、能耗、吞吐、优先级等机制与计算规则。 */
    inline fun rule(builder: Components.() -> Unit) = style(theme.rule, builder)

    /**
     * 当前状态；[level] 由调用处根据实际状态选择，默认显示常规信息。
     * 注意和故障级别在主题的 status 样式上叠加，保留其未被覆盖的属性。
     */
    inline fun status(level: ComponentStatusLevel = ComponentStatusLevel.NORMAL, builder: Components.() -> Unit) {
        val statusStyle = when (level) {
            ComponentStatusLevel.NORMAL -> theme.status
            ComponentStatusLevel.ATTENTION -> theme.statusAttention.applyTo(theme.status)
            ComponentStatusLevel.ERROR -> theme.statusError.applyTo(theme.status)
        }
        style(statusStyle, builder)
    }

    /** 快捷操作、展开说明等辅助提示；完整操作方法使用 [usage]。 */
    inline fun hint(builder: Components.() -> Unit) = style(theme.hint, builder)
    /** 标识、来源等用于辨认或排查的次要信息。 */
    inline fun detail(builder: Components.() -> Unit) = style(theme.detail, builder)
    /** 风险与操作后果；当前故障使用 [status] 的 ERROR 级别。 */
    inline fun warning(builder: Components.() -> Unit) = style(theme.warning, builder)

    inline fun style(style: Style, builder: Components.() -> Unit) {
        val previous = currentStyle
        currentStyle = style.applyTo(previous)
        try {
            builder()
        } finally {
            currentStyle = previous
        }
    }

    inline fun color(rgb: Int, builder: Components.() -> Unit) = style(Style.EMPTY.withColor(rgb), builder)
    inline fun bold(enabled: Boolean = true, builder: Components.() -> Unit) = style(Style.EMPTY.withBold(enabled), builder)
    inline fun italic(enabled: Boolean = true, builder: Components.() -> Unit) = style(Style.EMPTY.withItalic(enabled), builder)
    inline fun underline(enabled: Boolean = true, builder: Components.() -> Unit) = style(Style.EMPTY.withUnderlined(enabled), builder)
    inline fun strikethrough(enabled: Boolean = true, builder: Components.() -> Unit) = style(Style.EMPTY.withStrikethrough(enabled), builder)
    inline fun obfuscated(enabled: Boolean = true, builder: Components.() -> Unit) = style(Style.EMPTY.withObfuscated(enabled), builder)

    @PublishedApi
    internal fun format(formatting: ChatFormatting): Style = Style.EMPTY.applyFormat(formatting)

    inline fun black(builder: Components.() -> Unit) = style(format(ChatFormatting.BLACK), builder)
    inline fun darkBlue(builder: Components.() -> Unit) = style(format(ChatFormatting.DARK_BLUE), builder)
    inline fun darkGreen(builder: Components.() -> Unit) = style(format(ChatFormatting.DARK_GREEN), builder)
    inline fun darkAqua(builder: Components.() -> Unit) = style(format(ChatFormatting.DARK_AQUA), builder)
    inline fun darkRed(builder: Components.() -> Unit) = style(format(ChatFormatting.DARK_RED), builder)
    inline fun darkPurple(builder: Components.() -> Unit) = style(format(ChatFormatting.DARK_PURPLE), builder)
    inline fun gold(builder: Components.() -> Unit) = style(format(ChatFormatting.GOLD), builder)
    inline fun gray(builder: Components.() -> Unit) = style(format(ChatFormatting.GRAY), builder)
    inline fun darkGray(builder: Components.() -> Unit) = style(format(ChatFormatting.DARK_GRAY), builder)
    inline fun blue(builder: Components.() -> Unit) = style(format(ChatFormatting.BLUE), builder)
    inline fun green(builder: Components.() -> Unit) = style(format(ChatFormatting.GREEN), builder)
    inline fun aqua(builder: Components.() -> Unit) = style(format(ChatFormatting.AQUA), builder)
    inline fun red(builder: Components.() -> Unit) = style(format(ChatFormatting.RED), builder)
    inline fun lightPurple(builder: Components.() -> Unit) = style(format(ChatFormatting.LIGHT_PURPLE), builder)
    inline fun yellow(builder: Components.() -> Unit) = style(format(ChatFormatting.YELLOW), builder)
    inline fun white(builder: Components.() -> Unit) = style(format(ChatFormatting.WHITE), builder)


    @PublishedApi
    internal fun appendLine(appendLine: MutableComponent): MutableComponentEditor {
        if (dirty) newLine
        appendLine.style = appendLine.style.applyTo(currentStyle)
        lines.add(appendLine)
        return MutableComponentEditor(appendLine)
    }

    @PublishedApi
    internal fun appendText(appendText: MutableComponent): MutableComponentEditor {
        appendText.style = appendText.style.applyTo(currentStyle)
        current.append(appendText)
        dirty = true
        return MutableComponentEditor(appendText)
    }

    @PublishedApi
    internal fun finish(): List<MutableComponent> {
        if (dirty) newLine
        return lines
    }

    companion object {
        @Volatile
        var defaultTheme: ComponentTheme = ComponentTheme()

        inline fun build(builder: Components.() -> Unit) = build(defaultTheme, builder)

        inline fun build(theme: ComponentTheme, builder: Components.() -> Unit) = Components(theme).apply(builder).finish()

        inline fun build(namespace: String, theme: ComponentTheme = defaultTheme, builder: Components.() -> Unit) =
            build(theme) { namespace(namespace, builder) }
    }
}

@DslMarker
private annotation class MutableComponentDsl

@MutableComponentDsl
@Suppress("NOTHING_TO_INLINE")
class MutableComponentEditor internal constructor(@PublishedApi internal val component: MutableComponent) {
    inline fun style(style: Style) = apply { component.style = style.applyTo(component.style) }
    inline fun color(rgb: Int) = apply { component.withStyle { it.withColor(rgb) } }
    inline fun bold(enabled: Boolean) = apply { component.withStyle { it.withBold(enabled) } }
    inline fun italic(enabled: Boolean) = apply { component.withStyle { it.withItalic(enabled) } }
    inline fun underline(enabled: Boolean) = apply { component.withStyle { it.withUnderlined(enabled) } }
    inline fun strikethrough(enabled: Boolean) = apply { component.withStyle { it.withStrikethrough(enabled) } }
    inline fun obfuscated(enabled: Boolean) = apply { component.withStyle { it.withObfuscated(enabled) } }

    inline val black get() = apply { component.withStyle(ChatFormatting.BLACK) }
    inline val darkBlue get() = apply { component.withStyle(ChatFormatting.DARK_BLUE) }
    inline val darkGreen get() = apply { component.withStyle(ChatFormatting.DARK_GREEN) }
    inline val darkAqua get() = apply { component.withStyle(ChatFormatting.DARK_AQUA) }
    inline val darkRed get() = apply { component.withStyle(ChatFormatting.DARK_RED) }
    inline val darkPurple get() = apply { component.withStyle(ChatFormatting.DARK_PURPLE) }
    inline val gold get() = apply { component.withStyle(ChatFormatting.GOLD) }
    inline val gray get() = apply { component.withStyle(ChatFormatting.GRAY) }
    inline val darkGray get() = apply { component.withStyle(ChatFormatting.DARK_GRAY) }
    inline val blue get() = apply { component.withStyle(ChatFormatting.BLUE) }
    inline val green get() = apply { component.withStyle(ChatFormatting.GREEN) }
    inline val aqua get() = apply { component.withStyle(ChatFormatting.AQUA) }
    inline val red get() = apply { component.withStyle(ChatFormatting.RED) }
    inline val lightPurple get() = apply { component.withStyle(ChatFormatting.LIGHT_PURPLE) }
    inline val yellow get() = apply { component.withStyle(ChatFormatting.YELLOW) }
    inline val white get() = apply { component.withStyle(ChatFormatting.WHITE) }
    inline val obfuscated get() = apply { component.withStyle(ChatFormatting.OBFUSCATED) }
    inline val bold get() = apply { component.withStyle(ChatFormatting.BOLD) }
    inline val strikethrough get() = apply { component.withStyle(ChatFormatting.STRIKETHROUGH) }
    inline val underline get() = apply { component.withStyle(ChatFormatting.UNDERLINE) }
    inline val italic get() = apply { component.withStyle(ChatFormatting.ITALIC) }
}
