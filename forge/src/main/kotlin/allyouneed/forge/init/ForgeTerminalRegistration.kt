package allyouneed.forge.init

import allyouneed.util.MODID
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

object ForgeTerminalRegistration {
    val BE: DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<*>> =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID)

    // We register the BE type directly in ForgeBlocks to avoid self-reference issues.
    // This object is kept for future expansion (menu registration etc).

    fun register(bus: IEventBus) {
        BE.register(bus)
    }
}
