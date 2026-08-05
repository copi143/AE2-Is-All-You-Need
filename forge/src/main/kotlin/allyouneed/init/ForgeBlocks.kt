package allyouneed.forge.init

import allyouneed.async.AsyncBlockKind
import allyouneed.async.AsyncBlockRegistry
import allyouneed.async.AsyncCraftingRegistration
import allyouneed.async.AsyncRole
import allyouneed.async.AsyncStructureBlock
import allyouneed.async.AsyncStructureBlockEntity
import allyouneed.async.AsyncStructureFrameBlock
import allyouneed.async.AsyncStructureConnectorBlock
import allyouneed.async.AsyncStructureConnectorBlockEntity
import allyouneed.async.AsyncStructureControllerBlock
import allyouneed.async.AsyncStructureInterfaceBlock
import allyouneed.iodrive.MEIODriveBlock
import allyouneed.iodrive.MEIODriveBlockEntity
import allyouneed.iodrive.MEIODriveRegistration
import allyouneed.parts.machineassembler.MachineAssemblerBlock
import allyouneed.parts.machineassembler.MachineAssemblerBlockEntity
import allyouneed.parts.machineassembler.MachineAssemblerRegistration
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlock
import allyouneed.pattern.adaptive.AdaptivePatternTerminalBlockEntity
import allyouneed.pattern.adaptive.AdaptivePatternTerminalRegistration
import allyouneed.pattern.machine.MachinePatternTerminalBlock
import allyouneed.pattern.machine.MachinePatternTerminalBlockEntity
import allyouneed.pattern.machine.MachinePatternTerminalRegistration
import allyouneed.rl
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlock
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalBlockEntity
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalItem
import allyouneed.util.MODID
import allyouneed.util.Services
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

    // Machine Assembler
    val MACHINE_ASSEMBLER_INSTANCE = MachineAssemblerBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )
    val MACHINE_ASSEMBLER_ITEM_INSTANCE = BlockItem(MACHINE_ASSEMBLER_INSTANCE, Item.Properties())

    val MACHINE_ASSEMBLER: RegistryObject<MachineAssemblerBlock> =
        BLOCKS.register("molecular_assembler") { MACHINE_ASSEMBLER_INSTANCE }

    val MACHINE_ASSEMBLER_BE: RegistryObject<BlockEntityType<MachineAssemblerBlockEntity>> =
        BLOCK_ENTITIES.register("molecular_assembler") {
            val type = BlockEntityType.Builder.of(
                { pos, state -> MachineAssemblerBlockEntity(MACHINE_ASSEMBLER_BE.get(), pos, state) },
                MACHINE_ASSEMBLER.get()
            ).build(null as com.mojang.datafixers.types.Type<*>?)
            @Suppress("UNCHECKED_CAST")
            (MACHINE_ASSEMBLER_INSTANCE as appeng.block.AEBaseEntityBlock<MachineAssemblerBlockEntity>).setBlockEntity(
                MachineAssemblerBlockEntity::class.java, type, null, null
            )
            MachineAssemblerRegistration.setBlockEntityType(type)
            MachineAssemblerRegistration.setBlock(MACHINE_ASSEMBLER_INSTANCE)
            type
        }

    val MACHINE_ASSEMBLER_ITEM: RegistryObject<BlockItem> =
        ForgeItems.ITEMS.register("molecular_assembler") { MACHINE_ASSEMBLER_ITEM_INSTANCE }

    val MACHINE_ASSEMBLER_DEF: BlockDefinition<MachineAssemblerBlock> =
        BlockDefinition("Molecular Assembler", "molecular_assembler".rl, MACHINE_ASSEMBLER_INSTANCE, MACHINE_ASSEMBLER_ITEM_INSTANCE).also {
            MainCreativeTab.add(it)
        }

    // Machine Pattern Terminal
    val MACHINE_PATTERN_TERMINAL_INSTANCE = MachinePatternTerminalBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
    )
    val MACHINE_PATTERN_TERMINAL_ITEM_INSTANCE = BlockItem(MACHINE_PATTERN_TERMINAL_INSTANCE, Item.Properties())

    val MACHINE_PATTERN_TERMINAL: RegistryObject<MachinePatternTerminalBlock> =
        BLOCKS.register("machine_pattern_terminal") { MACHINE_PATTERN_TERMINAL_INSTANCE }

    val MACHINE_PATTERN_TERMINAL_BE: RegistryObject<BlockEntityType<MachinePatternTerminalBlockEntity>> =
        BLOCK_ENTITIES.register("machine_pattern_terminal") {
            val type = BlockEntityType.Builder.of(
                { pos, state -> MachinePatternTerminalBlockEntity(MACHINE_PATTERN_TERMINAL_BE.get(), pos, state) },
                MACHINE_PATTERN_TERMINAL.get()
            ).build(null as com.mojang.datafixers.types.Type<*>?)
            @Suppress("UNCHECKED_CAST")
            (MACHINE_PATTERN_TERMINAL_INSTANCE as appeng.block.AEBaseEntityBlock<MachinePatternTerminalBlockEntity>).setBlockEntity(
                MachinePatternTerminalBlockEntity::class.java, type, null, null
            )
            MachinePatternTerminalRegistration.setBlockEntityType(type)
            type
        }

    val MACHINE_PATTERN_TERMINAL_ITEM: RegistryObject<BlockItem> =
        ForgeItems.ITEMS.register("machine_pattern_terminal") { MACHINE_PATTERN_TERMINAL_ITEM_INSTANCE }

    val MACHINE_PATTERN_TERMINAL_DEF: BlockDefinition<MachinePatternTerminalBlock> =
        BlockDefinition("Machine Pattern Terminal", "machine_pattern_terminal".rl, MACHINE_PATTERN_TERMINAL_INSTANCE, MACHINE_PATTERN_TERMINAL_ITEM_INSTANCE).also {
            MainCreativeTab.add(it)
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

    // -------------------------------------------------------------------------------------------
    // Async synthesis structures (the 16-block set). The controllers and connectors are registered
    // by GTAsyncCrafting (GTRegistrate) under the same ids when GTCEu is loaded, so the common
    // blocks/items/BEs must be skipped to avoid a registry collision.
    // -------------------------------------------------------------------------------------------

    private val hasGt: Boolean = Services.platform.isModLoaded("gtceu")

    private val structureProps = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)

    /** Kinds owned by GTAsyncCrafting when GTCEu is loaded: the three controllers + three connectors. */
    private val gtOwnedKinds: Set<AsyncBlockKind> =
        if (hasGt) {
            AsyncBlockKind.entries
                .filter { it.role == AsyncRole.CONTROLLER || it.role == AsyncRole.CONNECTOR }
                .toSet()
        } else {
            emptySet()
        }

    private val asyncStructureKinds: List<AsyncBlockKind> =
        AsyncBlockKind.entries.filter { it !in gtOwnedKinds }

    /** Eagerly created block instances, registered below. Instances (not RegistryObjects) back the
     *  creative-tab definitions so that `<clinit>` never calls `RegistryObject.get()`. */
    private val asyncStructureInstances: Map<AsyncBlockKind, Block> =
        asyncStructureKinds.associateWith { kind ->
            val block = when (kind) {
                AsyncBlockKind.FRAME -> AsyncStructureFrameBlock(kind, structureProps)
                else -> when (kind.role) {
                    AsyncRole.CONTROLLER -> AsyncStructureControllerBlock(kind, structureProps)
                    AsyncRole.CONNECTOR -> AsyncStructureConnectorBlock(kind, structureProps)
                    AsyncRole.INTERFACE -> AsyncStructureInterfaceBlock(kind, structureProps)
                    else -> AsyncStructureBlock(kind, structureProps)
                }
            }
            AsyncBlockRegistry.register(kind, block)
            block
        }

    private val asyncStructureItemInstances: Map<AsyncBlockKind, BlockItem> =
        asyncStructureKinds.associateWith { kind ->
            BlockItem(asyncStructureInstances.getValue(kind), Item.Properties())
        }

    val ASYNC_STRUCTURE_BLOCKS: Map<AsyncBlockKind, RegistryObject<Block>> =
        asyncStructureKinds.associateWith { kind ->
            BLOCKS.register(kind.id) { asyncStructureInstances.getValue(kind) }
        }

    val ASYNC_STRUCTURE_ITEMS: Map<AsyncBlockKind, RegistryObject<BlockItem>> =
        asyncStructureKinds.associateWith { kind ->
            ForgeItems.ITEMS.register(kind.id) { asyncStructureItemInstances.getValue(kind) }
        }

    val ASYNC_STRUCTURE_BE: RegistryObject<BlockEntityType<AsyncStructureBlockEntity>> =
        BLOCK_ENTITIES.register("async_structure") {
            val type = BlockEntityType.Builder.of(
                { pos, state -> AsyncStructureBlockEntity(ASYNC_STRUCTURE_BE.get(), pos, state) },
                *structureEntityKinds.map { asyncStructureInstances.getValue(it) }.toTypedArray(),
            ).build(null as com.mojang.datafixers.types.Type<*>?)
            for (kind in structureEntityKinds) {
                @Suppress("UNCHECKED_CAST")
                (
                    asyncStructureInstances.getValue(kind)
                        as appeng.block.AEBaseEntityBlock<AsyncStructureBlockEntity>
                    ).setBlockEntity(AsyncStructureBlockEntity::class.java, type, null, null)
            }
            AsyncCraftingRegistration.setStructureBlockEntityType(type)
            type
        }

    val ASYNC_STRUCTURE_CONNECTOR_BE: RegistryObject<BlockEntityType<AsyncStructureConnectorBlockEntity>> =
        BLOCK_ENTITIES.register("async_structure_connector") {
            val type = BlockEntityType.Builder.of(
                { pos, state -> AsyncStructureConnectorBlockEntity(ASYNC_STRUCTURE_CONNECTOR_BE.get(), pos, state) },
                *connectorKinds.map { asyncStructureInstances.getValue(it) }.toTypedArray(),
            ).build(null as com.mojang.datafixers.types.Type<*>?)
            for (kind in connectorKinds) {
                @Suppress("UNCHECKED_CAST")
                (
                    asyncStructureInstances.getValue(kind)
                        as appeng.block.AEBaseEntityBlock<AsyncStructureConnectorBlockEntity>
                    ).setBlockEntity(AsyncStructureConnectorBlockEntity::class.java, type, null, null)
            }
            AsyncCraftingRegistration.setStructureConnectorBlockEntityType(type)
            type
        }

    val ASYNC_STRUCTURE_DEFS: Map<AsyncBlockKind, BlockDefinition<Block>> =
        asyncStructureKinds.associateWith { kind ->
            BlockDefinition(
                kind.displayName,
                kind.id.rl,
                asyncStructureInstances.getValue(kind),
                asyncStructureItemInstances.getValue(kind),
            ).also { MainCreativeTab.add(it) }
        }

    private val structureEntityKinds: List<AsyncBlockKind>
        get() = asyncStructureKinds.filter { it.role == AsyncRole.CONTROLLER || it.role == AsyncRole.INTERFACE }

    private val connectorKinds: List<AsyncBlockKind>
        get() = asyncStructureKinds.filter { it.role == AsyncRole.CONNECTOR }

    fun register(bus: IEventBus) {
        BLOCKS.register(bus)
        BLOCK_ENTITIES.register(bus)
    }
}
