package minecraftx.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic color contract for the Minecraft-flavored Compose component set. Every framework
 * component (`McPanel`, `McText`, `ItemSlot`, scrollbars, tooltips...) reads its paint colors from
 * the active scheme instead of hard-coding them, so a whole UI can be re-skinned by swapping the
 * scheme (via [McTheme]) or a single node by passing an explicit [minecraftx.compose.material.McPanel.colors].
 *
 * The interface carries the **dark theme** values as defaults, so a custom theme object only needs
 * to override the slots it differs in. Built-in schemes: [DarkColorScheme], [LightColorScheme].
 */
interface McColorScheme {
    /** Primary text color (lines, titles, values). */
    val textPrimary: Color get() = Color(0xFFFFFFFF)

    /** Secondary text color (hints, placeholders, muted labels). */
    val textSecondary: Color get() = Color(0xFF808080)

    /** Panel chrome: background fill and 1px border. */
    val panelBackground: Color get() = Color(0xC0101010)
    val panelBorder: Color get() = Color.White

    /** ✕ close button chrome. */
    val closeButtonBackground: Color get() = Color(0xAA404040)
    val closeButtonBorder: Color get() = Color.White

    /** Scrollable content area fill / frame (virtual column viewport). */
    val contentBackground: Color get() = Color(0x66000000)
    val contentBorder: Color get() = Color(0xFF333333)

    /** Item slot: base fill, 1px border and hover highlight overlay. */
    val slotBackground: Color get() = Color(0x66808080)
    val slotBorder: Color get() = Color(0xFF8B8B8B)
    val slotHoverOverlay: Color get() = Color(0x80FFFFFF)

    /** Text field chrome: fill, idle and focused borders, caret and selection highlight. */
    val inputBackground: Color get() = Color(0xAA181818)
    val inputBorder: Color get() = Color(0xFF6E6E6E)
    val inputBorderFocused: Color get() = Color(0xFFFFFFFF)
    val textCaret: Color get() = Color(0xFFFFFFFF)
    val textSelection: Color get() = Color(0x804040FF)

    /** Vertical scrollbar track and bar. */
    val scrollbarTrack: Color get() = Color(0xAA444444)
    val scrollbarBar: Color get() = Color(0xFFAAAAAA)

    /** Floating tooltip chrome. */
    val tooltipBackground: Color get() = Color(0xC0100010)
    val tooltipBorder: Color get() = Color(0xFF555555)
}
