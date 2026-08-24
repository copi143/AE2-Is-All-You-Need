package minecraftx.compose.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Pluggable text rendering engine behind every minecraftx display component.
 *
 * Engines split text work into two phases:
 *  - [layout]: measure + wrap a [McStyledString] into an engine-agnostic [McTextLayout]. Pure
 *    computation, safe to cache (see [rememberTextLayout]).
 *  - [paint]: draw a cached layout at the current DrawScope origin (called from drawBehind).
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
     * Draw [layout] with its top-left at the current DrawScope origin. Runs without an explicit
     * color use [fallbackColor].
     */
    fun DrawScope.paint(layout: McTextLayout, fallbackColor: Color)
}
