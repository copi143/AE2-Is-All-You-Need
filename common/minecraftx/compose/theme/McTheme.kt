package minecraftx.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import minecraftx.compose.text.LocalMcTextEngine
import minecraftx.compose.text.McTextEngines

/**
 * Theme plumbing for the Minecraft component set. A complete theme = style strategy ([McStyle],
 * controls how components render) + color strategy ([McColorScheme], controls semantic colors).
 *
 * Provide a scheme/style around a subtree to re-skin everything under it — either globally (wrap a
 * whole screen's content) or locally (wrap a single panel). Defaults come from [McThemeSettings]
 * (client config `ae2isallyouneed-client.properties`); the dark/light [McThemeSettings.colorScheme]
 * only applies to the minimal style, other styles always start from their own palette.
 *
 * ```kotlin
 * McTheme { McPanel(width = 200.dp, height = 100.dp) { McText(...) } }
 * McTheme(style = McStyles.scifi) { ... }
 * ```
 */
@Composable
fun McTheme(
    colorScheme: McColorScheme? = null,
    style: McStyle? = null,
    shapes: McShapes = McShapes.Default,
    content: @Composable () -> Unit,
) {
    val resolvedStyle = style ?: McThemeSettings.style
    val resolved = colorScheme
        ?: if (resolvedStyle.id == MinimalMcStyle.id) McThemeSettings.colorScheme else resolvedStyle.defaultColors
    val engine = McTextEngines.byId(McThemeSettings.textEngineId)
    CompositionLocalProvider(
        LocalColorScheme provides resolved,
        LocalStyle provides resolvedStyle,
        LocalShapes provides shapes,
        LocalMcTextEngine provides engine,
        content = content,
    )
}

/** Convenience accessor: `McTheme.colors` / `McTheme.style` / `McTheme.shapes`. */
object McTheme {
    val colors: McColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalColorScheme.current

    val style: McStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalStyle.current

    val shapes: McShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalShapes.current
}

internal val LocalColorScheme: ProvidableCompositionLocal<McColorScheme> =
    compositionLocalOf { McThemeSettings.colorScheme }
internal val LocalStyle: ProvidableCompositionLocal<McStyle> =
    compositionLocalOf { McThemeSettings.style }
internal val LocalShapes: ProvidableCompositionLocal<McShapes> = compositionLocalOf { McShapes.Default }
