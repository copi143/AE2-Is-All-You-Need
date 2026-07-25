package allyouneed.energy

import allyouneed.rl
import allyouneed.util.Ki
import allyouneed.util.Mi
import appeng.api.ids.AEBlockIds
import appeng.block.AEBaseBlock
import appeng.block.AEBaseBlockItem
import appeng.block.AEBaseEntityBlock
import appeng.blockentity.AEBaseBlockEntity
import appeng.block.networking.CreativeEnergyCellBlock
import appeng.block.networking.EnergyCellBlock
import appeng.block.networking.EnergyCellBlockItem
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity
import appeng.blockentity.networking.EnergyCellBlockEntity
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Supplier

enum class EnergyCell(
    val blockName: String,
    val blockId: ResourceLocation,
    val blockSupplier: Supplier<Block>,
    val itemFactory: BiFunction<Block, Item.Properties, BlockItem>? = null,
) {
    Simple(
        "Simple Energy Cell",
        "simple_energy_cell".rl,
        { EnergyCellBlock(256.0.Ki, 16.0.Ki, 400) },
        { block: Block, props: Item.Properties -> EnergyCellBlockItem(block, props) },
    ),

    Normal(
        "Normal Energy Cell",
        "normal_energy_cell".rl,
        { EnergyCellBlock(1.0.Mi, 64.0.Ki, 1600) },
        { block: Block, props: Item.Properties -> EnergyCellBlockItem(block, props) },
    ),

    Advanced(
        "Advanced Energy Cell",
        "advanced_energy_cell".rl,
        { EnergyCellBlock(4.0.Mi, 256.0.Ki, 6400) },
        { block: Block, props: Item.Properties -> EnergyCellBlockItem(block, props) },
    ),

    Dense(
        "Dense Energy Cell",
        "dense_energy_cell".rl,
        { EnergyCellBlock(16.0.Mi, 1.0.Mi, 25600) },
        { block: Block?, props: Item.Properties? -> EnergyCellBlockItem(block, props) },
    ),

    Creative(
        "Creative Energy Cell",
        AEBlockIds.CREATIVE_ENERGY_CELL,
        { CreativeEnergyCellBlock() },
    );

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

        val typeRef = AtomicReference<BlockEntityType<*>>()

        if (this == Creative) {
            typeRef.set(BlockEntityType.Builder.of({ pos, state ->
                CreativeEnergyCellBlockEntity(typeRef.get() as BlockEntityType<CreativeEnergyCellBlockEntity>, pos, state)
            }, block).build(null as com.mojang.datafixers.types.Type<*>?))
            blockEntityType = typeRef.get()
            (block as AEBaseEntityBlock<CreativeEnergyCellBlockEntity>).setBlockEntity(
                CreativeEnergyCellBlockEntity::class.java,
                blockEntityType as BlockEntityType<CreativeEnergyCellBlockEntity>,
                null,
                null,
            )
        } else {
            typeRef.set(BlockEntityType.Builder.of({ pos, state ->
                EnergyCellBlockEntity(typeRef.get() as BlockEntityType<EnergyCellBlockEntity>, pos, state)
            }, block).build(null as com.mojang.datafixers.types.Type<*>?))
            blockEntityType = typeRef.get()
            (block as AEBaseEntityBlock<EnergyCellBlockEntity>).setBlockEntity(
                EnergyCellBlockEntity::class.java,
                blockEntityType as BlockEntityType<EnergyCellBlockEntity>,
                null,
                null,
            )
        }

        AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, define.asItem())
    }
}
