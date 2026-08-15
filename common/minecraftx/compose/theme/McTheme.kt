package minecraftx.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme plumbing for the Minecraft component set.
 *
 * Provide a scheme around a subtree to re-skin everything under it — either globally (wrap a whole
 * screen's content) or locally (wrap a single panel). Components that do not wrap themselves in
 * [McTheme] fall back to [DarkColorScheme], so existing screens keep their look unchanged.
 *
 * ```kotlin
 * var dark by remember { mutableStateOf(true) }
 * McTheme(colorScheme = if (dark) DarkColorScheme else LightColorScheme) {
 *     McPanel(width = 200.dp, height = 100.dp) { McText(...) }
 * }
 * ```
 */
@Composable
fun McTheme(
    colorScheme: McColorScheme = DarkColorScheme,
    typography: McTypography = McTypography.Default,
    shapes: McShapes = McShapes.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColorScheme provides colorScheme,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        content = content,
    )
}

/** Convenience accessor: `McTheme.colors` / `McTheme.typography` / `McTheme.shapes`. */
object McTheme {
    val colors: McColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalColorScheme.current

    val typography: McTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current

    val shapes: McShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalShapes.current
}

internal val LocalColorScheme: ProvidableCompositionLocal<McColorScheme> = staticCompositionLocalOf { DarkColorScheme }
internal val LocalTypography: ProvidableCompositionLocal<McTypography> = staticCompositionLocalOf { McTypography.Default }
internal val LocalShapes: ProvidableCompositionLocal<McShapes> = staticCompositionLocalOf { McShapes.Default }
