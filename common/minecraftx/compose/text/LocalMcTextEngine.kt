package minecraftx.compose.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import minecraftx.compose.text.msdf.MsdfTextEngine
import minecraftx.compose.theme.McThemeSettings

/** Built-in engines. */
object McTextEngines {
    val vanilla = VanillaTextEngine("vanilla")
    val spaced = VanillaTextEngine("spaced", letterSpacing = 2)
    val msdf: McTextEngine by lazy { MsdfTextEngine() }

    val all: List<McTextEngine>
        get() = listOf(vanilla, spaced, msdf)

    fun byId(id: String): McTextEngine = all.firstOrNull { it.id == id } ?: vanilla
}

/**
 * Active text engine for the current composition scope. Falls back to the global setting from
 * [McThemeSettings] (re-evaluated on every read so config changes re-compose); override locally
 * with CompositionLocalProvider to switch engines for a subtree.
 */
val LocalMcTextEngine = compositionLocalOf<McTextEngine> {
    McTextEngines.byId(McThemeSettings.textEngineId)
}

/**
 * Computes and caches a [McTextLayout] across recompositions. The cache key covers the input
 * string, resolved engine, width and mode — any change re-runs [McTextEngine.layout].
 */
@Composable
fun rememberTextLayout(
    text: McStyledString,
    maxWidth: Int,
    singleLine: Boolean = false,
    engine: McTextEngine = LocalMcTextEngine.current,
): McTextLayout = remember(text, engine, maxWidth, singleLine) {
    engine.layout(text, maxWidth, singleLine)
}
