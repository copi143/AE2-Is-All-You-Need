package minecraftx.compose.itemdetail

/**
 * Shared geometry for the item-details screen. The panel is a fixed rectangle
 * so that the EMI, JEI and vanilla fallback renderers all align their header
 * and content areas.
 */
object ItemDetailsLayout {
    const val WIDTH = 340
    const val HEIGHT = 250

    const val PADDING = 8
    const val SLOT_SIZE = 18
    const val TITLE_X = PADDING + SLOT_SIZE + 4

    /** Top of the scrollable content area, measured from the panel top. */
    const val CONTENT_TOP = PADDING + SLOT_SIZE + 8

    const val LINE_HEIGHT = 10
    const val SECTION_GAP = 4

    const val CONTENT_WIDTH = WIDTH - PADDING * 2
    const val CONTENT_HEIGHT = HEIGHT - CONTENT_TOP - PADDING
}
