package minecraftx.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * The default scheme: the classic dark Minecraft panel look (translucent near-black panels, white
 * text and borders, grey slots and scrollbars). This is also the scheme used when no [McTheme]
 * provider is present above a component.
 */
object DarkColorScheme : McColorScheme

/**
 * A light scheme for the same component set: light panels, dark text. Every slot keeps the value
 * from the dark defaults unless overridden here, so the two schemes stay structurally identical.
 */
object LightColorScheme : McColorScheme {
    override val textPrimary: Color get() = Color(0xFF202020)

    override val panelBackground: Color get() = Color(0xFFE8E8E8)
    override val panelBorder: Color get() = Color(0xFF808080)

    override val closeButtonBackground: Color get() = Color(0xFFD0D0D0)
    override val closeButtonBorder: Color get() = Color(0xFF606060)

    override val contentBackground: Color get() = Color(0x55FFFFFF)
    override val contentBorder: Color get() = Color(0xFFA0A0A0)

    override val slotBackground: Color get() = Color(0x66909090)
    override val slotBorder: Color get() = Color(0xFF707070)
    override val slotHoverOverlay: Color get() = Color(0x55FFFFFF)

    override val scrollbarTrack: Color get() = Color(0x44A0A0A0)
    override val scrollbarBar: Color get() = Color(0xFF606060)

    override val tooltipBackground: Color get() = Color(0xFFF4F4F4)
    override val tooltipBorder: Color get() = Color(0xFF808080)
}
