package allyouneed.fabric.init

import allyouneed.AllRegistries
import allyouneed.parts.p2p.EntityP2PTunnelPart
import allyouneed.pattern.ModItems
import allyouneed.pattern.adaptive.AdaptivePatternItem
import allyouneed.pattern.machine.MachinePatternItem
import allyouneed.pattern.pseudo.PseudoPatternItem
import allyouneed.rl
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalItem
import appeng.api.ids.AECreativeTabIds
import appeng.items.parts.PartItem
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item

object FabricItems {
    val PSEUDO_PATTERN: PseudoPatternItem = ModItems.PSEUDO_PATTERN
    val ADAPTIVE_PATTERN: AdaptivePatternItem = ModItems.ADAPTIVE_PATTERN
    val MACHINE_PATTERN: MachinePatternItem = ModItems.MACHINE_PATTERN
    val ENTITY_P2P_TUNNEL: PartItem<EntityP2PTunnelPart> = ModItems.ENTITY_P2P_TUNNEL
    val WIRELESS_PSEUDO_PATTERN_TERMINAL: WirelessPseudoPatternTerminalItem =
        WirelessPseudoPatternTerminalItem(Item.Properties().stacksTo(1))

    fun register() {
        Registry.register(
            BuiltInRegistries.ITEM, "pseudo_pattern".rl, PSEUDO_PATTERN
        )
        Registry.register(
            BuiltInRegistries.ITEM, "adaptive_pattern".rl, ADAPTIVE_PATTERN
        )
        Registry.register(
            BuiltInRegistries.ITEM, "machine_pattern".rl, MACHINE_PATTERN
        )
        Registry.register(
            BuiltInRegistries.ITEM, "entity_p2p_tunnel".rl, ENTITY_P2P_TUNNEL
        )
        AllRegistries.items.forEach { entry ->
            Registry.register(BuiltInRegistries.ITEM, entry.id(), entry.asItem())
        }
        Registry.register(
            BuiltInRegistries.ITEM, "wireless_pseudo_pattern_terminal".rl, WIRELESS_PSEUDO_PATTERN_TERMINAL
        )

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register { entries ->
            entries.accept(PSEUDO_PATTERN)
            entries.accept(ADAPTIVE_PATTERN)
            entries.accept(MACHINE_PATTERN)
            entries.accept(ENTITY_P2P_TUNNEL)
            entries.accept(AllRegistries.CREATIVE_ME_CELL)
            entries.accept(AllRegistries.DIMENSIONAL_CELL)
            entries.accept(WIRELESS_PSEUDO_PATTERN_TERMINAL)
        }

        // AE2 main creative tab (Fabric does not use MainCreativeTab.initExternal)
        ItemGroupEvents.modifyEntriesEvent(AECreativeTabIds.MAIN).register { entries ->
            entries.accept(AllRegistries.CREATIVE_ME_CELL)
            entries.accept(AllRegistries.DIMENSIONAL_CELL)
        }
    }
}
