package allyouneed.cell

import allyouneed.logic.aekey.EnergyKey
import allyouneed.util.idify
import allyouneed.util.rl
import appeng.block.AEBaseEntityBlock
import appeng.block.networking.CreativeEnergyCellBlock
import appeng.block.networking.EnergyCellBlock
import appeng.block.networking.EnergyCellBlockItem
import appeng.blockentity.AEBaseBlockEntity
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity
import appeng.blockentity.networking.EnergyCellBlockEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
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

    override val itemFactory = if (size < 0) null else BiFunction<Block, Item.Properties, BlockItem> { block, props ->
        EnergyCellBlockItem(block, props)
    }

    private val blockEntityType: BlockEntityType<*> by lazy {
        when {
            isCreative -> creativeBlockEntityType
            isSelfPowered -> selfPoweredBlockEntityType
            else -> normalBlockEntityType
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun registerBlockEntity() {
        when {
            isCreative -> (define.block() as AEBaseEntityBlock<CreativeEnergyCellBlockEntity>).setBlockEntity(
                CreativeEnergyCellBlockEntity::class.java,
                creativeBlockEntityType,
                null,
                null,
            )

            isSelfPowered -> (define.block() as AEBaseEntityBlock<SelfPoweredEnergyCellBlockEntity>).setBlockEntity(
                SelfPoweredEnergyCellBlockEntity::class.java,
                selfPoweredBlockEntityType,
                null,
                null,
            )

            else -> (define.block() as AEBaseEntityBlock<EnergyCellBlockEntity>).setBlockEntity(
                EnergyCellBlockEntity::class.java,
                normalBlockEntityType,
                null,
                null,
            )
        }
        AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, define.asItem())
    }

    @Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    companion object {
        val entries = sizeList.map { EnergyCell(it) } + sizeList.map { EnergyCell(it, true) } + EnergyCell()

        val creativeBlockEntityType: BlockEntityType<CreativeEnergyCellBlockEntity> by lazy {
            BlockEntityType.Builder.of(
                { pos, state -> CreativeEnergyCellBlockEntity(creativeBlockEntityType, pos, state) },
                *entries.filter { it.isCreative }.map { it.define.block() }.toTypedArray(),
            ).build(null)
        }

        val selfPoweredBlockEntityType: BlockEntityType<SelfPoweredEnergyCellBlockEntity> by lazy {
            BlockEntityType.Builder.of(
                { pos, state -> SelfPoweredEnergyCellBlockEntity(selfPoweredBlockEntityType, pos, state) },
                *entries.filter { it.isSelfPowered }.map { it.define.block() }.toTypedArray(),
            ).build(null)
        }

        val normalBlockEntityType: BlockEntityType<EnergyCellBlockEntity> by lazy {
            BlockEntityType.Builder.of(
                { pos, state -> EnergyCellBlockEntity(normalBlockEntityType, pos, state) },
                *entries.filter { !it.isCreative && !it.isSelfPowered }.map { it.define.block() }.toTypedArray(),
            ).build(null)
        }

        val registry = mapOf(
            "creative_energy_cell" to creativeBlockEntityType,
            "self_powered_energy_cell" to selfPoweredBlockEntityType,
            "energy_cell" to normalBlockEntityType,
        )
    }
}
