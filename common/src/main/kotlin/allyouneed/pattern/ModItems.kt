package allyouneed.pattern

import allyouneed.api.machine.BuiltinMachineTypes
import allyouneed.pattern.machine.MachinePatternItem
import allyouneed.pattern.pseudo.PseudoPatternItem
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.stacks.GenericStack
import appeng.core.definitions.AEItems
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object ModItems {
    // We keep Processing pattern as the vanilla one.
    // We introduce our two new pattern items.
    val MACHINE_PATTERN: MachinePatternItem by lazy {
        MachinePatternItem(Item.Properties().stacksTo(1))
    }

    val PSEUDO_PATTERN: PseudoPatternItem by lazy {
        PseudoPatternItem(Item.Properties().stacksTo(1))
    }

    fun registerItems(register: (String, Item) -> Item): Pair<MachinePatternItem, PseudoPatternItem> {
        // Registration will be done in platform-specific code.
        // Here we just return the instances for use in registries.
        return MACHINE_PATTERN to PSEUDO_PATTERN
    }
}

object ModPatternDecoders {
    fun register() {
        // Register our custom pattern decoders so AE2 can understand our patterns
        PatternDetailsHelper.registerDecoder(MachinePatternDecoder)
        PatternDetailsHelper.registerDecoder(PseudoPatternDecoder)
    }
}
