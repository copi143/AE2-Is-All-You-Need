package allyouneed.energy

import allyouneed.rl
import appeng.api.ids.AEBlockIds
import appeng.block.AEBaseBlock
import appeng.block.AEBaseBlockItem
import appeng.block.networking.CreativeEnergyCellBlock
import appeng.block.networking.EnergyCellBlock
import appeng.block.networking.EnergyCellBlockItem
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
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
}

val Double.Ki get() = this * 1024.0
val Double.Mi get() = this * 1024.0 * 1024.0
val Double.Gi get() = this * 1024.0 * 1024.0 * 1024.0
val Double.Ti get() = this * 1024.0 * 1024.0 * 1024.0 * 1024.0
val Double.Pi get() = this * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0
val Double.Ei get() = this * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0
