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

enum class EnergyCell(
    name: String,
    size: Double = -1.0,
    val selfPowered: Boolean = false,
) {
    Micro("Micro Energy Cell", 1.0.Ki), //
    Simple("Simple Energy Cell", 4.0.Ki), //
    Basic("Basic Energy Cell", 16.0.Ki), //
    Normal("Normal Energy Cell", 64.0.Ki), //
    Enhanced("Enhanced Energy Cell", 256.0.Ki), //
    Advanced("Advanced Energy Cell", 1.0.Mi), //
    Reinforced("Reinforced Energy Cell", 4.0.Mi), //
    Dense("Dense Energy Cell", 16.0.Mi), //
    Hyper("Hyper Energy Cell", 64.0.Mi), //
    Ultra("Ultra Energy Cell", 256.0.Mi), //
    Ultimate("Ultimate Energy Cell", 1.0.Gi), //
    Singular("Singular Energy Cell", 4.0.Gi), //
    Quantum("Quantum Energy Cell", 16.0.Gi), //
    Stellar("Stellar Energy Cell", 64.0.Gi), //
    Cosmic("Cosmic Energy Cell", 256.0.Gi), //
    T1("1T Energy Cell", 1.0.Ti), //
    T4("4T Energy Cell", 4.0.Ti), //
    T16("16T Energy Cell", 16.0.Ti), //
    T64("64T Energy Cell", 64.0.Ti), //
    T256("256T Energy Cell", 256.0.Ti), //

    SpMicro("1K Self-Powered Energy Cell", 1.0.Ki, selfPowered = true), //
    SpSimple("4K Self-Powered Energy Cell", 4.0.Ki, selfPowered = true), //
    SpBasic("16K Self-Powered Energy Cell", 16.0.Ki, selfPowered = true), //
    SpNormal("64K Self-Powered Energy Cell", 64.0.Ki, selfPowered = true), //
    SpEnhanced("256K Self-Powered Energy Cell", 256.0.Ki, selfPowered = true), //
    SpAdvanced("1M Self-Powered Energy Cell", 1.0.Mi, selfPowered = true), //
    SpReinforced("4M Self-Powered Energy Cell", 4.0.Mi, selfPowered = true), //
    SpDense("16M Self-Powered Energy Cell", 16.0.Mi, selfPowered = true), //
    SpHyper("64M Self-Powered Energy Cell", 64.0.Mi, selfPowered = true), //
    SpUltra("256M Self-Powered Energy Cell", 256.0.Mi, selfPowered = true), //
    SpUltimate("1G Self-Powered Energy Cell", 1.0.Gi, selfPowered = true), //
    SpSingular("4G Self-Powered Energy Cell", 4.0.Gi, selfPowered = true), //
    SpQuantum("16G Self-Powered Energy Cell", 16.0.Gi, selfPowered = true), //
    SpStellar("64G Self-Powered Energy Cell", 64.0.Gi, selfPowered = true), //
    SpCosmic("256G Self-Powered Energy Cell", 256.0.Gi, selfPowered = true), //
    SpT1("1T Self-Powered Energy Cell", 1.0.Ti, selfPowered = true), //
    SpT4("4T Self-Powered Energy Cell", 4.0.Ti, selfPowered = true), //
    SpT16("16T Self-Powered Energy Cell", 16.0.Ti, selfPowered = true), //
    SpT64("64T Self-Powered Energy Cell", 64.0.Ti, selfPowered = true), //
    SpT256("256T Self-Powered Energy Cell", 256.0.Ti, selfPowered = true), //

    Creative("Creative Energy Cell"); //

    val blockName: String = name

    val blockId: ResourceLocation = run {
        if (!(size > 0)) {
            return@run name.lowercase().replace(" ", "_").rl
        }
        assert(size.toBits() and 0x00fffff_ffffffff == 0L)
        val suffix = if (selfPowered) "self_powered_energy_cell" else "energy_cell"
        formatScaledUnit(size.floatingExp, suffix).rl
    }

    val blockSupplier = if (size < 0) {
        Supplier<Block> { CreativeEnergyCellBlock() }
    } else if (selfPowered) {
        Supplier<Block> { EnergyCellBlock(size * EnergyKey.ENERGY_PER_BYTE, size * 4.0, size.floatingExp * 10 + 1000) }
    } else {
        Supplier<Block> { EnergyCellBlock(size * EnergyKey.ENERGY_PER_BYTE, size * 4.0, size.floatingExp * 10) }
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

            selfPowered -> {
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

            val blocks = entries.filter { it.selfPowered }.map { it.define.block() }.toTypedArray()
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
