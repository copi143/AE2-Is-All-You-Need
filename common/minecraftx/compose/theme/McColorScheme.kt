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
    val textPrimary: Color get() = Color(0xFFE8E8ED)
    val textSecondary: Color get() = Color(0xFF8E8E98)
    val textDisabled: Color get() = Color(0xFF5A5A64)

    val panelBackground: Color get() = Color(0xF21A1B22)
    val panelBorder: Color get() = Color(0xFF3C3C48)

    val closeButtonBackground: Color get() = Color(0xFF2A2A34)
    val closeButtonBorder: Color get() = Color(0xFF5A5A66)

    val contentBackground: Color get() = Color(0xE6121218)
    val contentBorder: Color get() = Color(0xFF2A2A34)

    val slotBackground: Color get() = Color(0xFF101014)
    val slotBorder: Color get() = Color(0xFF2E2E38)
    val slotHoverOverlay: Color get() = Color(0x28FFFFFF)
    val slotDisabledOverlay: Color get() = Color(0x99000000)
    val slotMissingOverlay: Color get() = Color(0x55C04040)

    val inputBackground: Color get() = Color(0xFF121218)
    val inputBorder: Color get() = Color(0xFF3A3A46)
    val inputBorderFocused: Color get() = Color(0xFF6BA3D4)
    val textCaret: Color get() = Color(0xFFE8E8ED)
    val textSelection: Color get() = Color(0x556BA3D4)

    val scrollbarTrack: Color get() = Color(0x33121218)
    val scrollbarBar: Color get() = Color(0xFF5A5A68)

    val tooltipBackground: Color get() = Color(0xF2161620)
    val tooltipBorder: Color get() = Color(0xFF4A4A58)

    val buttonBackground: Color get() = Color(0xFF2A2A34)
    val buttonBackgroundHovered: Color get() = Color(0xFF363644)
    val buttonBackgroundPressed: Color get() = Color(0xFF22222A)
    val buttonBackgroundDisabled: Color get() = Color(0xFF1E1E26)
    val buttonBorder: Color get() = Color(0xFF454552)
    val buttonBorderFocused: Color get() = Color(0xFF6BA3D4)

    val tabBackground: Color get() = Color(0xFF1E1E26)
    val tabBackgroundSelected: Color get() = Color(0xFF2A2A34)
    val tabBorder: Color get() = Color(0xFF3A3A46)
    val tabIndicator: Color get() = Color(0xFF6BA3D4)

    val progressTrack: Color get() = Color(0xFF121218)
    val progressFill: Color get() = Color(0xFF4A9B6A)

    val checkboxBackground: Color get() = Color(0xFF121218)
    val checkboxBorder: Color get() = Color(0xFF3A3A46)
    val checkboxMark: Color get() = Color(0xFFE8E8ED)

    val toggleTrackOff: Color get() = Color(0xFF2A2A34)
    val toggleTrackOn: Color get() = Color(0xFF3D7A9E)
    val toggleThumb: Color get() = Color(0xFFE0E0E6)

    val error: Color get() = Color(0xFFE05555)

    /** Markdown rendering tokens ([McMarkdown] code blocks, quotes, rules, headings, links). */
    val mdCodeBackground: Color get() = Color(0xFF1E1E28)
    val mdCodeText: Color get() = Color(0xFFD8B36A)
    val mdQuoteBar: Color get() = Color(0xFF6BA3D4)
    val mdRuleLine: Color get() = Color(0xFF3A3A46)
    val mdHeadingAccent: Color get() = Color(0xFF6BA3D4)
    val mdLink: Color get() = Color(0xFF7FB3E0)
}
