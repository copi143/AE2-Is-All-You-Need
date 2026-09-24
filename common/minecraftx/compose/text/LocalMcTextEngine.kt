package minecraftx.compose.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import minecraftx.compose.text.msdf.MsdfTextEngine
import minecraftx.compose.theme.McThemeSettings

/** Built-in engines. */
object McTextEngines {
    val vanilla = VanillaTextEngine("vanilla")

    private var msdfInstance: MsdfTextEngine? = null
    val msdf: McTextEngine
        get() = msdfInstance ?: MsdfTextEngine().also { msdfInstance = it }

    val all: List<McTextEngine>
        get() = listOfNotNull(vanilla, msdfInstance)

    fun byId(id: String): McTextEngine = when (id) {
        vanilla.id -> vanilla
        "msdf" -> msdf
        else -> vanilla
    }

    /**
     * Releases the msdf engine's GPU objects (shader program, atlas texture, VAO/VBO); they are
     * lazily recreated on the next paint while the CPU-side glyph cache survives. Must be called
     * on the render thread — wired to client resource reload by each platform.
     */
    fun releaseMsdfGl() {
        msdfInstance?.releaseGl()
    }
}

/**
 * Active text engine for the current composition scope. The default falls back to the global
 * setting from [McThemeSettings]; [minecraftx.compose.theme.McTheme] explicitly provides the
 * configured engine so config changes re-compose. Override locally with CompositionLocalProvider
 * to switch engines for a subtree.
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
