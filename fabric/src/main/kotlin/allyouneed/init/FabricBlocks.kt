package allyouneed.fabric.init

import allyouneed.Constants
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlock
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlockEntity
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object FabricBlocks {
    val PSEUDO_PATTERN_TERMINAL: PseudoPatternTerminalBlock = PseudoPatternTerminalBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var PSEUDO_PATTERN_TERMINAL_BE: net.minecraft.world.level.block.entity.BlockEntityType<PseudoPatternTerminalBlockEntity>

    fun register() {
        val blockId = ResourceLocation(Constants.MOD_ID, "pseudo_pattern_terminal")

        PSEUDO_PATTERN_TERMINAL_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> PseudoPatternTerminalBlockEntity(PSEUDO_PATTERN_TERMINAL_BE, pos, state) },
            PSEUDO_PATTERN_TERMINAL
        ).build()

        Registry.register(BuiltInRegistries.BLOCK, blockId, PSEUDO_PATTERN_TERMINAL)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, blockId, PSEUDO_PATTERN_TERMINAL_BE)
        Registry.register(BuiltInRegistries.ITEM, blockId, BlockItem(PSEUDO_PATTERN_TERMINAL, Item.Properties()))
    }
}
