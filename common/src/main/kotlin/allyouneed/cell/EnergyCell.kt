package allyouneed.cell

import allyouneed.aekey.EnergyKey
import allyouneed.util.*
import appeng.block.AEBaseBlock
import appeng.block.AEBaseBlockItem
import appeng.block.AEBaseEntityBlock
import appeng.block.networking.CreativeEnergyCellBlock
import appeng.block.networking.EnergyCellBlock
import appeng.block.networking.EnergyCellBlockItem
import appeng.blockentity.AEBaseBlockEntity
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity
import appeng.blockentity.networking.EnergyCellBlockEntity
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import com.mojang.datafixers.types.Type
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Supplier

enum class EnergyCell(size: Double = -1.0) {
    K1(1.0.Ki), //
    K4(4.0.Ki), //
    K16(16.0.Ki), //
    K64(64.0.Ki), //
    K256(256.0.Ki), //
    M1(1.0.Mi), //
    M4(4.0.Mi), //
    M16(16.0.Mi), //
    M64(64.0.Mi), //
    M256(256.0.Mi), //
    G1(1.0.Gi), //
    G4(4.0.Gi), //
    G16(16.0.Gi), //
    G64(64.0.Gi), //
    G256(256.0.Gi), //
    T1(1.0.Ti), //
    T4(4.0.Ti), //
    T16(16.0.Ti), //
    T64(64.0.Ti), //
    T256(256.0.Ti), //

    SpK1(1.0.Ki), //
    SpK4(4.0.Ki), //
    SpK16(16.0.Ki), //
    SpK64(64.0.Ki), //
    SpK256(256.0.Ki), //
    SpM1(1.0.Mi), //
    SpM4(4.0.Mi), //
    SpM16(16.0.Mi), //
    SpM64(64.0.Mi), //
    SpM256(256.0.Mi), //
    SpG1(1.0.Gi), //
    SpG4(4.0.Gi), //
    SpG16(16.0.Gi), //
    SpG64(64.0.Gi), //
    SpG256(256.0.Gi), //
    SpT1(1.0.Ti), //
    SpT4(4.0.Ti), //
    SpT16(16.0.Ti), //
    SpT64(64.0.Ti), //
    SpT256(256.0.Ti), //

    Creative; //

    private val prefix = if (size < 0) {
        null
    } else {
        assert(size.toBits() and 0x00fffff_ffffffff == 0L)
        formatScaledUnit(size.floatingExp)
    }

    val blockName: String = (prefix?.uppercase() ?: "Creative") + " Energy Cell"
    val isSelfPowered: Boolean = name.startsWith("Sp")
    val isCreative: Boolean = size < 0

    val blockId: ResourceLocation =
        ((prefix ?: "creative") + (if (isSelfPowered) "_self_powered" else "") + "_energy_cell").rl

    val blockSupplier = when {
        isCreative -> Supplier<Block> { CreativeEnergyCellBlock() }
        isSelfPowered -> Supplier<Block> { EnergyCellBlock(size * EnergyKey.ENERGY_PER_BYTE, size * 4.0, size.floatingExp * 10 + 1000) }
        else -> Supplier<Block> { EnergyCellBlock(size * EnergyKey.ENERGY_PER_BYTE, size * 4.0, size.floatingExp * 10) }
    }

    val itemFactory = if (size < 0) null else BiFunction<Block, Item.Properties, BlockItem> { block, props ->
        EnergyCellBlockItem(block, props)
    }

    val define: BlockDefinition<Block> = run {
        val block = blockSupplier.get()

        val item = itemFactory?.apply(block, Item.Properties()) ?: if (block is AEBaseBlock) {
            AEBaseBlockItem(block, Item.Properties())
        } else {
            BlockItem(block, Item.Properties())
        }

        BlockDefinition(blockName, blockId, block, item).apply {
            MainCreativeTab.add(this)
        }
    }

    lateinit var blockEntityType: BlockEntityType<*>
        private set

    @Suppress("UNCHECKED_CAST")
    fun registerBEType() {
        val block = define.block()
        require(block is AEBaseEntityBlock<*>) { "EnergyCell block must be AEBaseEntityBlock" }

        when {
            this == Creative -> {
                val typeRef = AtomicReference<BlockEntityType<*>>()
                @Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") typeRef.set(BlockEntityType.Builder.of({ pos, state ->
                    CreativeEnergyCellBlockEntity(
                        typeRef.get() as BlockEntityType<CreativeEnergyCellBlockEntity>, pos, state
                    )
                }, block).build(null as Type<*>?))
                blockEntityType = typeRef.get()
                (block as AEBaseEntityBlock<CreativeEnergyCellBlockEntity>).setBlockEntity(
                    CreativeEnergyCellBlockEntity::class.java,
                    blockEntityType as BlockEntityType<CreativeEnergyCellBlockEntity>,
                    null,
                    null,
                )
                AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, define.asItem())
            }

            isSelfPowered -> {
                // Shared BE type registered once via registerSelfPoweredBEType()
                blockEntityType = selfPoweredBlockEntityType
                (block as AEBaseEntityBlock<SelfPoweredEnergyCellBlockEntity>).setBlockEntity(
                    SelfPoweredEnergyCellBlockEntity::class.java,
                    blockEntityType as BlockEntityType<SelfPoweredEnergyCellBlockEntity>,
                    null,
                    null,
                )
                AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, define.asItem())
            }

            else -> {
                val typeRef = AtomicReference<BlockEntityType<*>>()
                @Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") typeRef.set(BlockEntityType.Builder.of({ pos, state ->
                    EnergyCellBlockEntity(typeRef.get() as BlockEntityType<EnergyCellBlockEntity>, pos, state)
                }, block).build(null as Type<*>?))
                blockEntityType = typeRef.get()
                (block as AEBaseEntityBlock<EnergyCellBlockEntity>).setBlockEntity(
                    EnergyCellBlockEntity::class.java,
                    blockEntityType as BlockEntityType<EnergyCellBlockEntity>,
                    null,
                    null,
                )
                AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, define.asItem())
            }
        }
    }

    companion object {
        lateinit var selfPoweredBlockEntityType: BlockEntityType<SelfPoweredEnergyCellBlockEntity>
            private set

        private var selfPoweredRegistered = false

        /** Must be called once before per-entry [registerBEType] for self-powered cells. */
        @Suppress("UNCHECKED_CAST")
        fun registerSelfPoweredBEType() {
            if (selfPoweredRegistered) return
            selfPoweredRegistered = true

            val blocks = entries.filter { it.isSelfPowered }.map { it.define.block() }.toTypedArray()
            val typeRef = AtomicReference<BlockEntityType<SelfPoweredEnergyCellBlockEntity>>()
            @Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") typeRef.set(
                BlockEntityType.Builder.of(
                    { pos, state -> SelfPoweredEnergyCellBlockEntity(typeRef.get(), pos, state) },
                    *blocks,
                ).build(null as Type<*>?),
            )
            selfPoweredBlockEntityType = typeRef.get()
        }
    }
}
