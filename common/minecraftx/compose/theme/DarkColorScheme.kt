package minecraftx.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * The default scheme: a solid dark slate panel with muted borders and a cool accent. This is also
 * the scheme used when no [McTheme] provider is present above a component.
 */
object DarkColorScheme : McColorScheme

/**
 * A light scheme for the same component set: warm paper panels, dark text, same accent. Every slot
 * keeps the value from the dark defaults unless overridden here.
 */
object LightColorScheme : McColorScheme {
    override val textPrimary: Color get() = Color(0xFF1C1C22)
    override val textSecondary: Color get() = Color(0xFF6A6A74)
    override val textDisabled: Color get() = Color(0xFF9A9AA4)

    override val panelBackground: Color get() = Color(0xFFF4F2EC)
    override val panelBorder: Color get() = Color(0xFFB8B6AE)

    override val closeButtonBackground: Color get() = Color(0xFFE4E2DC)
    override val closeButtonBorder: Color get() = Color(0xFF8A8A92)

    override val contentBackground: Color get() = Color(0xE6FFFFFF)
    override val contentBorder: Color get() = Color(0xFFD0CEC6)

    override val slotBackground: Color get() = Color(0xFFE8E6E0)
    override val slotBorder: Color get() = Color(0xFFB0AEA6)
    override val slotHoverOverlay: Color get() = Color(0x22000000)
    override val slotDisabledOverlay: Color get() = Color(0x55FFFFFF)
    override val slotMissingOverlay: Color get() = Color(0x44C04040)

    override val inputBackground: Color get() = Color(0xFFFFFFFF)
    override val inputBorder: Color get() = Color(0xFFB0AEA6)
    override val inputBorderFocused: Color get() = Color(0xFF3A7AB0)
    override val textCaret: Color get() = Color(0xFF1C1C22)
    override val textSelection: Color get() = Color(0x553A7AB0)

    override val scrollbarTrack: Color get() = Color(0x33B0AEA6)
    override val scrollbarBar: Color get() = Color(0xFF8A8A92)

    override val tooltipBackground: Color get() = Color(0xF2F4F2EC)
    override val tooltipBorder: Color get() = Color(0xFFB8B6AE)

    override val buttonBackground: Color get() = Color(0xFFE4E2DC)
    override val buttonBackgroundHovered: Color get() = Color(0xFFEEECE6)
    override val buttonBackgroundPressed: Color get() = Color(0xFFD4D2CC)
    override val buttonBackgroundDisabled: Color get() = Color(0xFFDCDAD4)
    override val buttonBorder: Color get() = Color(0xFFB0AEA6)
    override val buttonBorderFocused: Color get() = Color(0xFF3A7AB0)

    override val tabBackground: Color get() = Color(0xFFDCDAD4)
    override val tabBackgroundSelected: Color get() = Color(0xFFF4F2EC)
    override val tabBorder: Color get() = Color(0xFFB8B6AE)
    override val tabIndicator: Color get() = Color(0xFF3A7AB0)

    override val progressTrack: Color get() = Color(0xFFD8D6D0)
    override val progressFill: Color get() = Color(0xFF3D8B55)

    override val checkboxBackground: Color get() = Color(0xFFFFFFFF)
    override val checkboxBorder: Color get() = Color(0xFFB0AEA6)
    override val checkboxMark: Color get() = Color(0xFF1C1C22)

    override val toggleTrackOff: Color get() = Color(0xFFC8C6C0)
    override val toggleTrackOn: Color get() = Color(0xFF3A7AB0)
    override val toggleThumb: Color get() = Color(0xFFF4F2EC)

    override val error: Color get() = Color(0xFFC04040)

    override val mdCodeBackground: Color get() = Color(0xFFE8E2D4)
    override val mdCodeText: Color get() = Color(0xFF7A5210)
    override val mdQuoteBar: Color get() = Color(0xFF2D6A94)
    override val mdRuleLine: Color get() = Color(0xFFC6C0B0)
    override val mdHeadingAccent: Color get() = Color(0xFF2D6A94)
    override val mdLink: Color get() = Color(0xFF1E5A88)
}
