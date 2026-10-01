package minecraftx.compose.text

import allyouneed.client.compose.platform.McGraphics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Pluggable text rendering engine behind every minecraftx display component.
 *
 * Engines split text work into two phases:
 *  - [layout]: measure + wrap a [McStyledString] into an engine-agnostic [McTextLayout]. Pure
 *    computation, safe to cache (see [rememberTextLayout]).
 *  - [paint]: draw a cached layout at the current pose origin. Text is deferred through
 *    [allyouneed.client.compose.platform.McGraphics.defer] by callers, so implementations always
 *    see the live GUI-stage graphics via [allyouneed.client.compose.platform.McGraphics.current].
 *
 * All coordinates are in MC GUI px (the ComposeOwner density space). The active engine is resolved
 * per composition via [LocalMcTextEngine]; components never reference a concrete engine.
 */
interface McTextEngine {

    /** Stable identifier used by settings persistence and demo switchers ("vanilla", "msdf", ...). */
    val id: String

    /** Height of one line in px. */
    val lineHeight: Int

    /**
     * Wrap [text] to [maxWidth] px. With [singleLine] no wrapping happens: content is truncated at
     * the last character that fits (matches the legacy single-line McText behavior).
     */
    fun layout(text: McStyledString, maxWidth: Int = Int.MAX_VALUE, singleLine: Boolean = false): McTextLayout

    /**
     * Draw [layout] with its top-left at the current pose origin. Runs without an explicit
     * color use [fallbackColor]. [shadow] renders the vanilla drop shadow (+1,+1, quarter
     * intensity) behind the text.
     */
    fun paint(layout: McTextLayout, fallbackColor: Color, shadow: Boolean = false)

    /** Advance width of [text] in px, equivalent to a single-line unbounded [layout]. */
    fun widthOf(text: String, style: McSpanStyle? = null): Int

    /**
     * Largest UTF-16 index such that `text.substring(0, index)` fits in [width] px (vanilla
     * `plainSubstrByWidth` semantics).
     */
    fun indexAtWidth(text: String, width: Int, style: McSpanStyle? = null): Int
}

/**
 * Records a pose translate to ([x], [y]) + a deferred [McTextEngine.paint] + a pose restore into
 * the active draw-pass recording (or draws directly in live mode), keeping the replay order intact.
 * Replaces `translate(x, y) { with(engine) { paint(...) } }`, which cannot be deferred safely.
 */
fun DrawScope.paintDeferred(
    engine: McTextEngine,
    layout: McTextLayout,
    color: Color,
    x: Float,
    y: Float,
    shadow: Boolean = false,
) {
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate(x, y)
    McGraphics.defer { engine.paint(layout, color, shadow) }
    canvas.restore()
}
