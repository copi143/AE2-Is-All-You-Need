package allyouneed.forge.init

import allyouneed.Constants
import allyouneed.terminal.PseudoPatternTerminalBlockEntity
import allyouneed.terminal.PseudoPatternTerminalRegistration
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ForgeTerminalRegistration {
    val BE: DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<*>> =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Constants.MOD_ID)

    // We register the BE type directly in ForgeBlocks to avoid self-reference issues.
    // This object is kept for future expansion (menu registration etc).

    fun register(bus: IEventBus) {
        BE.register(bus)
    }
}
