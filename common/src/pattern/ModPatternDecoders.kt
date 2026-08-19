package allyouneed.pattern

import appeng.api.crafting.PatternDetailsHelper

object ModPatternDecoders {
    fun register() {
        PatternDetailsHelper.registerDecoder(GenericPatternDecoder)
    }
}
