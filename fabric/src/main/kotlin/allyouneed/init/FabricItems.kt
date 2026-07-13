package allyouneed.fabric.init

import allyouneed.Constants
import allyouneed.pattern.ModItems
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalItem
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item

object FabricItems {
    val MACHINE_PATTERN: allyouneed.pattern.machine.MachinePatternItem = ModItems.MACHINE_PATTERN
    val PSEUDO_PATTERN: allyouneed.pattern.pseudo.PseudoPatternItem = ModItems.PSEUDO_PATTERN
    val WIRELESS_PSEUDO_PATTERN_TERMINAL: WirelessPseudoPatternTerminalItem =
        WirelessPseudoPatternTerminalItem(Item.Properties().stacksTo(1))

    fun register() {
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(Constants.MOD_ID, "machine_pattern"),
            MACHINE_PATTERN
        )
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(Constants.MOD_ID, "pseudo_pattern"),
            PSEUDO_PATTERN
        )
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(Constants.MOD_ID, "wireless_pseudo_pattern_terminal"),
            WIRELESS_PSEUDO_PATTERN_TERMINAL
        )

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register { entries ->
            entries.accept(MACHINE_PATTERN)
            entries.accept(PSEUDO_PATTERN)
            entries.accept(WIRELESS_PSEUDO_PATTERN_TERMINAL)
        }
    }
}
