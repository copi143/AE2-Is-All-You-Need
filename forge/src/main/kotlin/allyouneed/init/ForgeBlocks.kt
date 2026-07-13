package allyouneed.forge.init

import allyouneed.Constants
import allyouneed.terminal.PseudoPatternTerminalBlock
import allyouneed.terminal.PseudoPatternTerminalBlockEntity
import allyouneed.terminal.PseudoPatternTerminalMenu
import allyouneed.terminal.WirelessPseudoPatternTerminalItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ForgeBlocks {
    val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(ForgeRegistries.BLOCKS, Constants.MOD_ID)
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Constants.MOD_ID)

    val PSEUDO_PATTERN_TERMINAL: RegistryObject<PseudoPatternTerminalBlock> = BLOCKS.register("pseudo_pattern_terminal") {
        PseudoPatternTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f))
    }

    val PSEUDO_PATTERN_TERMINAL_BE: RegistryObject<BlockEntityType<PseudoPatternTerminalBlockEntity>> = BLOCK_ENTITIES.register("pseudo_pattern_terminal") {
        BlockEntityType.Builder.of(
            { pos, state -> PseudoPatternTerminalBlockEntity(PSEUDO_PATTERN_TERMINAL_BE.get(), pos, state) },
            PSEUDO_PATTERN_TERMINAL.get()
        ).build(null as com.mojang.datafixers.types.Type<*>?)
    }

    val WIRELESS_PSEUDO_PATTERN_TERMINAL: RegistryObject<WirelessPseudoPatternTerminalItem> =
        allyouneed.forge.init.ForgeItems.ITEMS.register("wireless_pseudo_pattern_terminal") {
            WirelessPseudoPatternTerminalItem(Item.Properties().stacksTo(1))
        }

    fun register(bus: IEventBus) {
        BLOCKS.register(bus)
        BLOCK_ENTITIES.register(bus)
    }
}
