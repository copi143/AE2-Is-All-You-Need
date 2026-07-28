package allyouneed.fabric.init

import allyouneed.iodrive.MEIODriveBlock
import allyouneed.iodrive.MEIODriveBlockEntity
import allyouneed.iodrive.MEIODriveRegistration
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlock
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlockEntity
import allyouneed.pattern.adaptive.AdaptivePatternTerminalRegistration
import allyouneed.rl
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlock
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlockEntity
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object FabricBlocks {
    val PSEUDO_PATTERN_TERMINAL: PseudoPatternTerminalBlock = PseudoPatternTerminalBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var PSEUDO_PATTERN_TERMINAL_BE: net.minecraft.world.level.block.entity.BlockEntityType<PseudoPatternTerminalBlockEntity>

    val ADAPTIVE_PATTERN_TERMINAL: AdaptivePatternTerminalBlock = AdaptivePatternTerminalBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var ADAPTIVE_PATTERN_TERMINAL_BE: net.minecraft.world.level.block.entity.BlockEntityType<AdaptivePatternTerminalBlockEntity>

    val ME_IO_DRIVE: MEIODriveBlock = MEIODriveBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var ME_IO_DRIVE_BE: net.minecraft.world.level.block.entity.BlockEntityType<MEIODriveBlockEntity>

    fun register() {
        val blockId = "pseudo_pattern_terminal".rl

        PSEUDO_PATTERN_TERMINAL_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> PseudoPatternTerminalBlockEntity(PSEUDO_PATTERN_TERMINAL_BE, pos, state) },
            PSEUDO_PATTERN_TERMINAL
        ).build()

        Registry.register(BuiltInRegistries.BLOCK, blockId, PSEUDO_PATTERN_TERMINAL)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, blockId, PSEUDO_PATTERN_TERMINAL_BE)
        Registry.register(BuiltInRegistries.ITEM, blockId, BlockItem(PSEUDO_PATTERN_TERMINAL, Item.Properties()))

        // Adaptive Pattern Terminal
        val adaptiveId = "adaptive_pattern_terminal".rl

        ADAPTIVE_PATTERN_TERMINAL_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> AdaptivePatternTerminalBlockEntity(ADAPTIVE_PATTERN_TERMINAL_BE, pos, state) },
            ADAPTIVE_PATTERN_TERMINAL
        ).build()
        @Suppress("UNCHECKED_CAST")
        (ADAPTIVE_PATTERN_TERMINAL as appeng.block.AEBaseEntityBlock<AdaptivePatternTerminalBlockEntity>).setBlockEntity(
            AdaptivePatternTerminalBlockEntity::class.java, ADAPTIVE_PATTERN_TERMINAL_BE, null, null
        )
        AdaptivePatternTerminalRegistration.setBlockEntityType(ADAPTIVE_PATTERN_TERMINAL_BE)

        Registry.register(BuiltInRegistries.BLOCK, adaptiveId, ADAPTIVE_PATTERN_TERMINAL)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, adaptiveId, ADAPTIVE_PATTERN_TERMINAL_BE)
        val adaptiveItem = BlockItem(ADAPTIVE_PATTERN_TERMINAL, Item.Properties())
        Registry.register(BuiltInRegistries.ITEM, adaptiveId, adaptiveItem)

        BlockDefinition("Adaptive Pattern Terminal", adaptiveId, ADAPTIVE_PATTERN_TERMINAL, adaptiveItem).also {
            MainCreativeTab.add(it)
        }

        // ME IO Drive
        val ioDriveId = "me_io_drive".rl

        ME_IO_DRIVE_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> MEIODriveBlockEntity(ME_IO_DRIVE_BE, pos, state) },
            ME_IO_DRIVE
        ).build()
        @Suppress("UNCHECKED_CAST")
        (ME_IO_DRIVE as appeng.block.AEBaseEntityBlock<MEIODriveBlockEntity>).setBlockEntity(
            MEIODriveBlockEntity::class.java, ME_IO_DRIVE_BE, null, null
        )
        MEIODriveRegistration.setBlockEntityType(ME_IO_DRIVE_BE)

        Registry.register(BuiltInRegistries.BLOCK, ioDriveId, ME_IO_DRIVE)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ioDriveId, ME_IO_DRIVE_BE)
        val ioDriveItem = BlockItem(ME_IO_DRIVE, Item.Properties())
        Registry.register(BuiltInRegistries.ITEM, ioDriveId, ioDriveItem)

        BlockDefinition("ME IO Drive", ioDriveId, ME_IO_DRIVE, ioDriveItem).also {
            MainCreativeTab.add(it)
        }
    }
}
