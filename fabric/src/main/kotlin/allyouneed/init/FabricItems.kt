package allyouneed.fabric.init

import allyouneed.pattern.ModItems
import allyouneed.rl
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalItem
import allyouneed.util.MODID
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item

object FabricItems {
    val MACHINE_PATTERN: allyouneed.pattern.machine.MachinePatternItem = ModItems.MACHINE_PATTERN
    val PSEUDO_PATTERN: allyouneed.pattern.pseudo.PseudoPatternItem = ModItems.PSEUDO_PATTERN
    val ENTITY_P2P_TUNNEL: appeng.items.parts.PartItem<allyouneed.parts.p2p.EntityP2PTunnelPart> =
        ModItems.ENTITY_P2P_TUNNEL
    val WIRELESS_PSEUDO_PATTERN_TERMINAL: WirelessPseudoPatternTerminalItem =
        WirelessPseudoPatternTerminalItem(Item.Properties().stacksTo(1))

    fun register() {
        Registry.register(
            BuiltInRegistries.ITEM, "machine_pattern".rl, MACHINE_PATTERN
        )
        Registry.register(
            BuiltInRegistries.ITEM, "pseudo_pattern".rl, PSEUDO_PATTERN
        )
        Registry.register(
            BuiltInRegistries.ITEM, "entity_p2p_tunnel".rl, ENTITY_P2P_TUNNEL
        )
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(MODID, "wireless_pseudo_pattern_terminal"),
            WIRELESS_PSEUDO_PATTERN_TERMINAL
        )

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register { entries ->
            entries.accept(MACHINE_PATTERN)
            entries.accept(PSEUDO_PATTERN)
            entries.accept(ENTITY_P2P_TUNNEL)
            entries.accept(WIRELESS_PSEUDO_PATTERN_TERMINAL)
        }
    }
}
