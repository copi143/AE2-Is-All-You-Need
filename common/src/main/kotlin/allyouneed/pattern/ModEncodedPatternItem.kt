package allyouneed.pattern

import appeng.crafting.pattern.EncodedPatternItem

/**
 * Base class for all pattern items in this mod.
 * Used as a type filter by [GenericPatternDecoder] so the generic decoder
 * only matches our mod's patterns, not AE2's or other mods' EncodedPatternItem subclasses.
 */
abstract class ModEncodedPatternItem(props: Properties) : EncodedPatternItem(props)
