package allyouneed.forge.init

import allyouneed.Constants
import allyouneed.pattern.ModItems
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ForgeItems {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, Constants.MOD_ID)

    val MACHINE_PATTERN: RegistryObject<allyouneed.pattern.machine.MachinePatternItem> =
        ITEMS.register("machine_pattern") { ModItems.MACHINE_PATTERN }

    val PSEUDO_PATTERN: RegistryObject<allyouneed.pattern.pseudo.PseudoPatternItem> =
        ITEMS.register("pseudo_pattern") { ModItems.PSEUDO_PATTERN }

    fun register(bus: IEventBus) {
        ITEMS.register(bus)
    }
}
