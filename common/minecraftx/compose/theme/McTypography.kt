package minecraftx.compose.theme

/**
 * Text metrics shared by the framework's text-based components. The Minecraft font has a single
 * size, so typography here only carries layout-relevant numbers (paint colors live in
 * [McColorScheme]).
 */
class McTypography(
    val lineHeight: Int = 10,
) {
    companion object {
        val Default = McTypography()
    }
}
