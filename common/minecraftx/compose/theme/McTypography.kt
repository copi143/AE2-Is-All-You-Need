package minecraftx.compose.theme

/**
 * Text metrics shared by the framework's text-based components. Typography here only carries
 * layout-relevant numbers (paint colors live in [McColorScheme]; glyph size comes from the
 * active [minecraftx.compose.text.McTextEngine]).
 */
class McTypography(
    val lineHeight: Int = 10,
) {
    companion object {
        val Default = McTypography()
    }
}
