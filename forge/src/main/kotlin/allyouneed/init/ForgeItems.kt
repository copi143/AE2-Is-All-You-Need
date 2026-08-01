package allyouneed.forge.init

import allyouneed.parts.p2p.EntityP2PTunnelPart
import allyouneed.pattern.ModItems
import allyouneed.pattern.adaptive.AdaptivePatternItem
import allyouneed.pattern.pseudo.PseudoPatternItem
import allyouneed.util.MODID
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

    val ENTITY_P2P_TUNNEL: RegistryObject<PartItem<EntityP2PTunnelPart>> =
        ITEMS.register("entity_p2p_tunnel") { ModItems.ENTITY_P2P_TUNNEL }

    fun register(bus: IEventBus) {
        ITEMS.register(bus)
    }
}
