package allyouneed.client.compose.platform

import net.minecraft.client.gui.GuiGraphics

/**
 * Hands the active [GuiGraphics] to composable draw modifiers that paint text with the Minecraft
 * font. Set around every [ComposeOwner.render] pass; text components read it from their
 * `drawBehind` scope and call GuiGraphics directly, bypassing the official text/skiko pipeline.
 * Framework users can read it from their own `drawBehind` / `Canvas` code to draw vanilla-style
 * content (items, fonts, fills) inside the node's translated frame.
 */
object McGraphics {
    var current: GuiGraphics? = null
}
