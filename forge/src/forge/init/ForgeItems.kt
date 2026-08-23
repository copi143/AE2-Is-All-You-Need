package allyouneed.forge.init

import allyouneed.parts.p2p.EntityP2PTunnelPart
import allyouneed.parts.planebus.PlaneBusPart
import allyouneed.pattern.ModItems
import allyouneed.pattern.adaptive.AdaptivePatternItem
import allyouneed.pattern.machine.MachinePatternItem
import allyouneed.pattern.pseudo.PseudoPatternItem
import allyouneed.pattern.term.UnifiedPatternEncodingTermPart
import allyouneed.util.MODID
import appeng.items.parts.ColoredPartItem
import appeng.items.parts.PartItem
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ForgeItems {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, MODID)

    val PSEUDO_PATTERN: RegistryObject<PseudoPatternItem> = ITEMS.register("pseudo_pattern") { ModItems.PSEUDO_PATTERN }

    val ADAPTIVE_PATTERN: RegistryObject<AdaptivePatternItem> =
        ITEMS.register("adaptive_pattern") { ModItems.ADAPTIVE_PATTERN }

    val MACHINE_PATTERN: RegistryObject<MachinePatternItem> =
        ITEMS.register("machine_pattern") { ModItems.MACHINE_PATTERN }

    val ENTITY_P2P_TUNNEL: RegistryObject<PartItem<EntityP2PTunnelPart>> =
        ITEMS.register("entity_p2p_tunnel") { ModItems.ENTITY_P2P_TUNNEL }

    val PLANE_BUS: RegistryObject<ColoredPartItem<PlaneBusPart>> =
        ITEMS.register("plane_bus") { ModItems.PLANE_BUS }

    val PATTERN_ENCODING_TERMINAL: RegistryObject<PartItem<UnifiedPatternEncodingTermPart>> =
        ITEMS.register("pattern_encoding_terminal") { ModItems.PATTERN_ENCODING_TERMINAL }

    fun register(bus: IEventBus) {
        ITEMS.register(bus)
    }
}
