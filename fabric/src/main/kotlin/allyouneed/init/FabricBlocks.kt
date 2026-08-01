package allyouneed.fabric.init

import allyouneed.async.AsyncCraftingBlock
import allyouneed.async.AsyncCraftingBlockEntity
import allyouneed.async.AsyncCraftingConnectorBlock
import allyouneed.async.AsyncCraftingConnectorBlockEntity
import allyouneed.async.AsyncCraftingOrientableBlock
import allyouneed.async.AsyncCraftingRegistration
import allyouneed.async.AsyncCraftingUnitRole
import allyouneed.async.AsyncCraftingUnitType
import allyouneed.iodrive.MEIODriveBlock
import allyouneed.iodrive.MEIODriveBlockEntity
import allyouneed.iodrive.MEIODriveRegistration
import allyouneed.machineassembler.MachineAssemblerBlock
import allyouneed.machineassembler.MachineAssemblerBlockEntity
import allyouneed.machineassembler.MachineAssemblerRegistration
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlock
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlockEntity
import allyouneed.pattern.adaptive.AdaptivePatternTerminalRegistration
import allyouneed.pattern.machine.MachinePatternTerminalBlock
import allyouneed.pattern.machine.MachinePatternTerminalBlockEntity
import allyouneed.pattern.machine.MachinePatternTerminalRegistration
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
import net.minecraft.world.level.block.entity.BlockEntityType
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

    val MACHINE_ASSEMBLER: MachineAssemblerBlock = MachineAssemblerBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var MACHINE_ASSEMBLER_BE: net.minecraft.world.level.block.entity.BlockEntityType<MachineAssemblerBlockEntity>

    val MACHINE_PATTERN_TERMINAL: MachinePatternTerminalBlock = MachinePatternTerminalBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var MACHINE_PATTERN_TERMINAL_BE: net.minecraft.world.level.block.entity.BlockEntityType<MachinePatternTerminalBlockEntity>

    val ME_IO_DRIVE: MEIODriveBlock = MEIODriveBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var ME_IO_DRIVE_BE: net.minecraft.world.level.block.entity.BlockEntityType<MEIODriveBlockEntity>

    val ASYNC_HOST: AsyncCraftingBlock = asyncBlock(AsyncCraftingUnitType.HOST)
    val ASYNC_CONNECTOR: AsyncCraftingBlock = asyncBlock(AsyncCraftingUnitType.CONNECTOR)
    val ASYNC_STORAGE: AsyncCraftingBlock = asyncBlock(AsyncCraftingUnitType.STORAGE)
    val ASYNC_WALL: AsyncCraftingBlock = asyncBlock(AsyncCraftingUnitType.WALL)
    val ASYNC_GLASS: AsyncCraftingBlock = asyncBlock(AsyncCraftingUnitType.GLASS)

    lateinit var ASYNC_UNIT_BE: BlockEntityType<AsyncCraftingBlockEntity>
    lateinit var ASYNC_CONNECTOR_BE: BlockEntityType<AsyncCraftingConnectorBlockEntity>

    private fun asyncBlock(unitType: AsyncCraftingUnitType): AsyncCraftingBlock {
        val props = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
        return when (unitType.role) {
            AsyncCraftingUnitRole.CONNECTOR -> AsyncCraftingConnectorBlock(props, unitType)
            AsyncCraftingUnitRole.HOST -> AsyncCraftingOrientableBlock(props, unitType)
            AsyncCraftingUnitRole.STORAGE, AsyncCraftingUnitRole.WALL, AsyncCraftingUnitRole.GLASS ->
                AsyncCraftingBlock(props, unitType)
        }
    }

    private fun blockFor(role: AsyncCraftingUnitRole): AsyncCraftingBlock = when (role) {
        AsyncCraftingUnitRole.HOST -> ASYNC_HOST
        AsyncCraftingUnitRole.CONNECTOR -> ASYNC_CONNECTOR
        AsyncCraftingUnitRole.STORAGE -> ASYNC_STORAGE
        AsyncCraftingUnitRole.WALL -> ASYNC_WALL
        AsyncCraftingUnitRole.GLASS -> ASYNC_GLASS
    }

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

        // Machine Assembler
        val machineAssemblerId = "molecular_assembler".rl

        MACHINE_ASSEMBLER_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> MachineAssemblerBlockEntity(MACHINE_ASSEMBLER_BE, pos, state) },
            MACHINE_ASSEMBLER
        ).build()
        @Suppress("UNCHECKED_CAST")
        (MACHINE_ASSEMBLER as appeng.block.AEBaseEntityBlock<MachineAssemblerBlockEntity>).setBlockEntity(
            MachineAssemblerBlockEntity::class.java, MACHINE_ASSEMBLER_BE, null, null
        )
        MachineAssemblerRegistration.setBlockEntityType(MACHINE_ASSEMBLER_BE)
        MachineAssemblerRegistration.setBlock(MACHINE_ASSEMBLER)

        Registry.register(BuiltInRegistries.BLOCK, machineAssemblerId, MACHINE_ASSEMBLER)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, machineAssemblerId, MACHINE_ASSEMBLER_BE)
        val machineAssemblerItem = BlockItem(MACHINE_ASSEMBLER, Item.Properties())
        Registry.register(BuiltInRegistries.ITEM, machineAssemblerId, machineAssemblerItem)

        BlockDefinition("Molecular Assembler", machineAssemblerId, MACHINE_ASSEMBLER, machineAssemblerItem).also {
            MainCreativeTab.add(it)
        }

        // Machine Pattern Terminal
        val machineTerminalId = "machine_pattern_terminal".rl

        MACHINE_PATTERN_TERMINAL_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> MachinePatternTerminalBlockEntity(MACHINE_PATTERN_TERMINAL_BE, pos, state) },
            MACHINE_PATTERN_TERMINAL
        ).build()
        @Suppress("UNCHECKED_CAST")
        (MACHINE_PATTERN_TERMINAL as appeng.block.AEBaseEntityBlock<MachinePatternTerminalBlockEntity>).setBlockEntity(
            MachinePatternTerminalBlockEntity::class.java, MACHINE_PATTERN_TERMINAL_BE, null, null
        )
        MachinePatternTerminalRegistration.setBlockEntityType(MACHINE_PATTERN_TERMINAL_BE)

        Registry.register(BuiltInRegistries.BLOCK, machineTerminalId, MACHINE_PATTERN_TERMINAL)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, machineTerminalId, MACHINE_PATTERN_TERMINAL_BE)
        val machineTerminalItem = BlockItem(MACHINE_PATTERN_TERMINAL, Item.Properties())
        Registry.register(BuiltInRegistries.ITEM, machineTerminalId, machineTerminalItem)

        BlockDefinition("Machine Pattern Terminal", machineTerminalId, MACHINE_PATTERN_TERMINAL, machineTerminalItem).also {
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

        // Async Processing Processor
        ASYNC_UNIT_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> AsyncCraftingBlockEntity(ASYNC_UNIT_BE, pos, state) },
            ASYNC_HOST, ASYNC_STORAGE, ASYNC_WALL, ASYNC_GLASS,
        ).build()
        ASYNC_CONNECTOR_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> AsyncCraftingConnectorBlockEntity(ASYNC_CONNECTOR_BE, pos, state) },
            ASYNC_CONNECTOR,
        ).build()
        AsyncCraftingRegistration.setUnitBlockEntityType(ASYNC_UNIT_BE)
        AsyncCraftingRegistration.setConnectorBlockEntityType(ASYNC_CONNECTOR_BE)

        for (unit in AsyncCraftingUnitType.entries.filter {
            it.role != AsyncCraftingUnitRole.CONNECTOR
        }) {
            blockFor(unit.role).setBlockEntity(AsyncCraftingBlockEntity::class.java, ASYNC_UNIT_BE, null, null)
        }
        @Suppress("UNCHECKED_CAST")
        (ASYNC_CONNECTOR as appeng.block.AEBaseEntityBlock<AsyncCraftingBlockEntity>).setBlockEntity(
            AsyncCraftingConnectorBlockEntity::class.java as Class<AsyncCraftingBlockEntity>,
            ASYNC_CONNECTOR_BE as BlockEntityType<AsyncCraftingBlockEntity>,
            null,
            null,
        )

        for (unit in AsyncCraftingUnitType.entries) {
            val block = blockFor(unit.role)
            val id = unit.id.rl
            Registry.register(BuiltInRegistries.BLOCK, id, block)
            val item = BlockItem(block, Item.Properties())
            Registry.register(BuiltInRegistries.ITEM, id, item)
            BlockDefinition(unit.displayName, id, block, item).also {
                MainCreativeTab.add(it)
            }
        }

        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            "async_processing_unit".rl,
            ASYNC_UNIT_BE,
        )
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            "async_processing_connector".rl,
            ASYNC_CONNECTOR_BE,
        )
    }
}
