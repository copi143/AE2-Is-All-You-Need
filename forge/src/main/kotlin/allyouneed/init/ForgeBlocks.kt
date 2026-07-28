package allyouneed.forge.init

import allyouneed.iodrive.MEIODriveBlock
import allyouneed.iodrive.MEIODriveBlockEntity
import allyouneed.iodrive.MEIODriveRegistration
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlock
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlockEntity
import allyouneed.pattern.adaptive.AdaptivePatternTerminalRegistration
import allyouneed.rl
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlock
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlockEntity
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalItem
import allyouneed.util.MODID
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
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
    val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID)

    val PSEUDO_PATTERN_TERMINAL: RegistryObject<PseudoPatternTerminalBlock> =
        BLOCKS.register("pseudo_pattern_terminal") {
            PseudoPatternTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f))
        }

    val PSEUDO_PATTERN_TERMINAL_BE: RegistryObject<BlockEntityType<PseudoPatternTerminalBlockEntity>> =
        BLOCK_ENTITIES.register("pseudo_pattern_terminal") {
            BlockEntityType.Builder.of(
                { pos, state -> PseudoPatternTerminalBlockEntity(PSEUDO_PATTERN_TERMINAL_BE.get(), pos, state) },
                PSEUDO_PATTERN_TERMINAL.get()
            ).build(null as com.mojang.datafixers.types.Type<*>?)
        }

    val WIRELESS_PSEUDO_PATTERN_TERMINAL: RegistryObject<WirelessPseudoPatternTerminalItem> =
        ForgeItems.ITEMS.register("wireless_pseudo_pattern_terminal") {
            WirelessPseudoPatternTerminalItem(Item.Properties().stacksTo(1))
        }

    // Adaptive Pattern Terminal
    val ADAPTIVE_PATTERN_TERMINAL: RegistryObject<AdaptivePatternTerminalBlock> =
        BLOCKS.register("adaptive_pattern_terminal") {
            AdaptivePatternTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f))
        }

    val ADAPTIVE_PATTERN_TERMINAL_BE: RegistryObject<BlockEntityType<AdaptivePatternTerminalBlockEntity>> =
        BLOCK_ENTITIES.register("adaptive_pattern_terminal") {
            val type = BlockEntityType.Builder.of(
                { pos, state -> AdaptivePatternTerminalBlockEntity(ADAPTIVE_PATTERN_TERMINAL_BE.get(), pos, state) },
                ADAPTIVE_PATTERN_TERMINAL.get()
            ).build(null as com.mojang.datafixers.types.Type<*>?)
            AdaptivePatternTerminalRegistration.setBlockEntityType(type)
            type
        }

    val ADAPTIVE_PATTERN_TERMINAL_ITEM: RegistryObject<BlockItem> =
        ForgeItems.ITEMS.register("adaptive_pattern_terminal") {
            BlockItem(ADAPTIVE_PATTERN_TERMINAL.get(), Item.Properties())
        }

    // ME IO Drive
    val ME_IO_DRIVE_INSTANCE = MEIODriveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f))
    val ME_IO_DRIVE_ITEM_INSTANCE = BlockItem(ME_IO_DRIVE_INSTANCE, Item.Properties())

    val ME_IO_DRIVE: RegistryObject<MEIODriveBlock> =
        BLOCKS.register("me_io_drive") { ME_IO_DRIVE_INSTANCE }

    val ME_IO_DRIVE_BE: RegistryObject<BlockEntityType<MEIODriveBlockEntity>> =
        BLOCK_ENTITIES.register("me_io_drive") {
            val type = BlockEntityType.Builder.of(
                { pos, state -> MEIODriveBlockEntity(ME_IO_DRIVE_BE.get(), pos, state) },
                ME_IO_DRIVE.get()
            ).build(null as com.mojang.datafixers.types.Type<*>?)
            @Suppress("UNCHECKED_CAST")
            (ME_IO_DRIVE_INSTANCE as appeng.block.AEBaseEntityBlock<MEIODriveBlockEntity>).setBlockEntity(
                MEIODriveBlockEntity::class.java, type, null, null
            )
            MEIODriveRegistration.setBlockEntityType(type)
            type
        }

    val ME_IO_DRIVE_ITEM: RegistryObject<BlockItem> =
        ForgeItems.ITEMS.register("me_io_drive") { ME_IO_DRIVE_ITEM_INSTANCE }

    val ME_IO_DRIVE_DEF: BlockDefinition<MEIODriveBlock> =
        BlockDefinition("ME IO Drive", "me_io_drive".rl, ME_IO_DRIVE_INSTANCE, ME_IO_DRIVE_ITEM_INSTANCE).also {
            MainCreativeTab.add(it)
        }

    fun register(bus: IEventBus) {
        BLOCKS.register(bus)
        BLOCK_ENTITIES.register(bus)
    }
}
