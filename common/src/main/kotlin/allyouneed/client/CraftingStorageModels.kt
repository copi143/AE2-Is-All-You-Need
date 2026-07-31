package allyouneed.client

import allyouneed.cell.CraftingStorage
import allyouneed.util.MODID
import appeng.client.render.crafting.CraftingCubeModel
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation

object CraftingStorageModels {
    fun formedModelId(storage: CraftingStorage): ResourceLocation =
        ResourceLocation(MODID, "block/crafting/${storage.blockId.path}_formed")

    fun createFormedModel(storage: CraftingStorage): UnbakedModel =
        CraftingCubeModel(CraftingStorageModelProvider(storage))
}
