package allyouneed.fabric.init

import allyouneed.multiblock.async.AsyncBlockKind
import allyouneed.multiblock.async.AsyncBlockRegistry
import allyouneed.multiblock.async.AsyncCraftingRegistration
import allyouneed.multiblock.async.AsyncRole
import allyouneed.multiblock.async.AsyncStructureBlock
import allyouneed.multiblock.async.AsyncStructureBlockEntity
import allyouneed.multiblock.async.AsyncStructureConnectorBlock
import allyouneed.multiblock.async.AsyncStructureConnectorBlockEntity
import allyouneed.multiblock.async.AsyncStructureControllerBlock
import allyouneed.multiblock.async.AsyncStructureFrameBlock
import allyouneed.multiblock.async.AsyncStructureInterfaceBlock
import allyouneed.parts.iodrive.MEIODriveBlock
import allyouneed.parts.iodrive.MEIODriveBlockEntity
import allyouneed.parts.iodrive.MEIODriveRegistration
import allyouneed.parts.logger.NetworkLoggerBlock
import allyouneed.parts.logger.NetworkLoggerBlockEntity
import allyouneed.parts.logger.NetworkLoggerRegistration
import allyouneed.parts.machineassembler.MachineAssemblerBlock
import allyouneed.parts.machineassembler.MachineAssemblerBlockEntity
import allyouneed.parts.machineassembler.MachineAssemblerRegistration
import allyouneed.util.rl
import appeng.block.AEBaseEntityBlock
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object FabricBlocks {
    val MACHINE_ASSEMBLER: MachineAssemblerBlock = MachineAssemblerBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var MACHINE_ASSEMBLER_BE: BlockEntityType<MachineAssemblerBlockEntity>

    val ME_IO_DRIVE: MEIODriveBlock = MEIODriveBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var ME_IO_DRIVE_BE: BlockEntityType<MEIODriveBlockEntity>

    val NETWORK_LOGGER: NetworkLoggerBlock = NetworkLoggerBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )

    lateinit var NETWORK_LOGGER_BE: BlockEntityType<NetworkLoggerBlockEntity>

    fun register() {
        // Machine Assembler
        val machineAssemblerId = "molecular_assembler".rl

        MACHINE_ASSEMBLER_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> MachineAssemblerBlockEntity(MACHINE_ASSEMBLER_BE, pos, state) }, MACHINE_ASSEMBLER
        ).build()
        @Suppress("UNCHECKED_CAST") (MACHINE_ASSEMBLER as AEBaseEntityBlock<MachineAssemblerBlockEntity>).setBlockEntity(
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

        // ME IO Drive
        val ioDriveId = "me_io_drive".rl

        ME_IO_DRIVE_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> MEIODriveBlockEntity(ME_IO_DRIVE_BE, pos, state) }, ME_IO_DRIVE
        ).build()
        @Suppress("UNCHECKED_CAST") (ME_IO_DRIVE as AEBaseEntityBlock<MEIODriveBlockEntity>).setBlockEntity(
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

        val loggerId = "network_logger".rl
        NETWORK_LOGGER_BE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> NetworkLoggerBlockEntity(NETWORK_LOGGER_BE, pos, state) }, NETWORK_LOGGER
        ).build()
        @Suppress("UNCHECKED_CAST") (NETWORK_LOGGER as AEBaseEntityBlock<NetworkLoggerBlockEntity>).setBlockEntity(
            NetworkLoggerBlockEntity::class.java, NETWORK_LOGGER_BE, null, null
        )
        NetworkLoggerRegistration.setBlockEntityType(NETWORK_LOGGER_BE)
        Registry.register(BuiltInRegistries.BLOCK, loggerId, NETWORK_LOGGER)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, loggerId, NETWORK_LOGGER_BE)
        val loggerItem = BlockItem(NETWORK_LOGGER, Item.Properties())
        Registry.register(BuiltInRegistries.ITEM, loggerId, loggerItem)
        BlockDefinition("ME Network Logger", loggerId, NETWORK_LOGGER, loggerItem).also {
            MainCreativeTab.add(it)
        }

        // Async synthesis structures (the 16-block set).
        val structureProps = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)

        val structureKinds = AsyncBlockKind.entries.filter {
            it.role == AsyncRole.CONTROLLER || it.role == AsyncRole.INTERFACE
        }
        val connectorKinds = AsyncBlockKind.entries.filter { it.role == AsyncRole.CONNECTOR }

        lateinit var structureBE: BlockEntityType<AsyncStructureBlockEntity>
        lateinit var structureConnectorBE: BlockEntityType<AsyncStructureConnectorBlockEntity>

        structureBE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> AsyncStructureBlockEntity(structureBE, pos, state) },
            *structureKinds.map { asyncStructureInstance(it) }.toTypedArray(),
        ).build()
        structureConnectorBE = FabricBlockEntityTypeBuilder.create(
            { pos, state -> AsyncStructureConnectorBlockEntity(structureConnectorBE, pos, state) },
            *connectorKinds.map { asyncStructureInstance(it) }.toTypedArray(),
        ).build()
        AsyncCraftingRegistration.setStructureBlockEntityType(structureBE)
        AsyncCraftingRegistration.setStructureConnectorBlockEntityType(structureConnectorBE)

        for (kind in structureKinds) {
            @Suppress("UNCHECKED_CAST") (asyncStructureInstance(kind) as AEBaseEntityBlock<AsyncStructureBlockEntity>).setBlockEntity(
                AsyncStructureBlockEntity::class.java,
                structureBE,
                null,
                null
            )
        }
        for (kind in connectorKinds) {
            @Suppress("UNCHECKED_CAST") (asyncStructureInstance(kind) as AEBaseEntityBlock<AsyncStructureConnectorBlockEntity>).setBlockEntity(
                AsyncStructureConnectorBlockEntity::class.java,
                structureConnectorBE,
                null,
                null
            )
        }

        for (kind in AsyncBlockKind.entries) {
            val block = asyncStructureInstance(kind)
            val id = kind.id.rl
            Registry.register(BuiltInRegistries.BLOCK, id, block)
            val item = BlockItem(block, Item.Properties())
            Registry.register(BuiltInRegistries.ITEM, id, item)
            AsyncBlockRegistry.register(kind, block)
            BlockDefinition(kind.displayName, id, block, item).also {
                MainCreativeTab.add(it)
            }
        }

        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "async_structure".rl, structureBE)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "async_structure_connector".rl, structureConnectorBE)
    }

    private val structureProps = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)

    private val asyncStructureInstances = HashMap<AsyncBlockKind, Block>()

    private fun asyncStructureInstance(kind: AsyncBlockKind): Block {
        return asyncStructureInstances.getOrPut(kind) {
            when (kind) {
                AsyncBlockKind.FRAME -> AsyncStructureFrameBlock(kind, structureProps)
                else -> when (kind.role) {
                    AsyncRole.CONTROLLER -> AsyncStructureControllerBlock(kind, structureProps)
                    AsyncRole.CONNECTOR -> AsyncStructureConnectorBlock(kind, structureProps)
                    AsyncRole.INTERFACE -> AsyncStructureInterfaceBlock(kind, structureProps)
                    else -> AsyncStructureBlock(kind, structureProps)
                }
            }
        }
    }
}
