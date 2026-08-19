package allyouneed.cell

import allyouneed.logic.aekey.EnergyKey
import allyouneed.util.idify
import allyouneed.util.rl
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
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Supplier

class EnergyCell(size: Long = -1, val isSelfPowered: Boolean = false) : ICellBlock(size, "Energy Cell") {
    private fun cellBlock(priority: Int): Block = EnergyCellBlock(
        size.toDouble() * EnergyKey.ENERGY_PER_BYTE,
        size * 4.0,
        sizeExp * 10 + priority,
    )

    override val blockName = "$prefixUpper${if (isSelfPowered) " Self-Powered" else ""} Energy Cell"

    override val blockId = idify(blockName).rl

    override val blockSupplier = when {
        isCreative -> Supplier<Block> { CreativeEnergyCellBlock() }
        isSelfPowered -> Supplier<Block> { cellBlock(1000) }
        else -> Supplier<Block> { cellBlock(0) }
    }

    val itemFactory = if (size < 0) null else BiFunction<Block, Item.Properties, BlockItem> { block, props ->
        EnergyCellBlockItem(block, props)
    }

    override val define = run {
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
            isCreative -> {
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
        val entries = sizeList.map { EnergyCell(it) } + sizeList.map { EnergyCell(it, true) } + EnergyCell()

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
