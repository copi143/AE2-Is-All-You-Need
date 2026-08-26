package minecraftx.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import minecraftx.compose.text.LocalMcTextEngine
import minecraftx.compose.text.McTextEngines

/**
 * Theme plumbing for the Minecraft component set.
 *
 * Provide a scheme around a subtree to re-skin everything under it — either globally (wrap a whole
 * screen's content) or locally (wrap a single panel). The default scheme is [McThemeSettings]
 * (client config `ae2isallyouneed-client.properties`). Components that do not wrap themselves in
 * [McTheme] fall back to the same setting.
 *
 * ```kotlin
 * McTheme { McPanel(width = 200.dp, height = 100.dp) { McText(...) } }
 * McThemeSettings.toggle()
 * ```
 */
@Composable
fun McTheme(
    colorScheme: McColorScheme? = null,
    typography: McTypography = McTypography.Default,
    shapes: McShapes = McShapes.Default,
    content: @Composable () -> Unit,
) {
    val resolved = colorScheme ?: McThemeSettings.colorScheme
    val engine = McTextEngines.byId(McThemeSettings.textEngineId)
    CompositionLocalProvider(
        LocalColorScheme provides resolved,
        LocalTypography provides typography,
        LocalShapes provides shapes,
        LocalMcTextEngine provides engine,
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

internal val LocalColorScheme: ProvidableCompositionLocal<McColorScheme> =
    staticCompositionLocalOf { McThemeSettings.colorScheme }
internal val LocalTypography: ProvidableCompositionLocal<McTypography> = staticCompositionLocalOf { McTypography.Default }
internal val LocalShapes: ProvidableCompositionLocal<McShapes> = staticCompositionLocalOf { McShapes.Default }
