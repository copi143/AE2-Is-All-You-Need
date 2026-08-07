package allyouneed.client.itemdetail.styling

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * Shared colors and small component builders used by the item-details UI across
 * the vanilla fallback, the EMI and the JEI renderers.
 */
object DetailsStyling {
    const val COLOR_TITLE = 0xFFFFFFFF.toInt()
    const val COLOR_SECTION = 0xFFFFAA00.toInt()
    const val COLOR_LABEL = 0xFFAAAAAA.toInt()
    const val COLOR_VALUE = 0xFFFFFFFF.toInt()
    const val COLOR_KEY = 0xFF55FF55.toInt()
    const val COLOR_DIM = 0xFF777777.toInt()

    private val SECTION_STYLE: Style = Style.EMPTY.withColor(COLOR_SECTION).withBold(true)
    private val LABEL_STYLE: Style = Style.EMPTY.withColor(COLOR_LABEL)
    private val VALUE_STYLE: Style = Style.EMPTY.withColor(COLOR_VALUE)
    private val KEY_STYLE: Style = Style.EMPTY.withColor(COLOR_KEY)

    /** Section heading, e.g. "▸ 基本信息". */
    fun section(text: String): Component = Component.literal("▸ $text").withStyle(SECTION_STYLE)

    /** "label: value" line, label grey and value white. */
    fun kv(label: String, value: Any): Component = Component.literal(label).withStyle(LABEL_STYLE)
        .append(Component.literal(value.toString()).withStyle(VALUE_STYLE))

    /** Plain coloured line. */
    fun line(text: String, color: Int = COLOR_VALUE): Component =
        Component.literal(text).withStyle(Style.EMPTY.withColor(color))

    /** A tag entry like `#minecraft:logs`, shown green. */
    fun tag(text: String): Component = Component.literal("#$text").withStyle(KEY_STYLE)

    /** Formats a float without trailing zeros. */
    fun formatFloat(value: Float): String {
        if (value == value.toInt().toFloat()) return value.toInt().toString()
        val text = "%.4f".format(value).trimEnd('0').trimEnd('.')
        return text
    }
}
