package allyouneed.client

import allyouneed.cell.CraftingStorage
import allyouneed.util.MODID
import appeng.client.render.crafting.AbstractCraftingUnitModelProvider
import appeng.client.render.crafting.LightBakedModel
import appeng.core.AppEng
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.Material
import net.minecraft.resources.ResourceLocation
import java.util.function.Function

class CraftingStorageModelProvider(type: CraftingStorage) :
    AbstractCraftingUnitModelProvider<CraftingStorage>(type) {

    override fun getMaterials(): List<Material> = emptyList()

    override fun getBakedModel(spriteGetter: Function<Material, TextureAtlasSprite>): BakedModel {
        @Suppress("UNCHECKED_CAST", "USELESS_CAST")
        return LightBakedModel(
            spriteGetter.apply(RING_CORNER),
            spriteGetter.apply(RING_SIDE_HOR),
            spriteGetter.apply(RING_SIDE_VER),
            spriteGetter.apply(LIGHT_BASE),
            spriteGetter.apply(lightMaterial(type)),
        ) as BakedModel
    }

    companion object {
        private val RING_CORNER = ae2Tex("ring_corner")
        private val RING_SIDE_HOR = ae2Tex("ring_side_hor")
        private val RING_SIDE_VER = ae2Tex("ring_side_ver")
        private val LIGHT_BASE = ae2Tex("light_base")

        private val LIGHTS: Map<CraftingStorage, Material> =
            CraftingStorage.entries.associateWith { storage ->
                Material(
                    TextureAtlas.LOCATION_BLOCKS,
                    ResourceLocation(MODID, "block/crafting/${storage.blockId.path}_light"),
                )
            }

        private fun lightMaterial(type: CraftingStorage): Material =
            LIGHTS[type] ?: error("Missing light material for $type")

        private fun ae2Tex(name: String): Material =
            Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation(AppEng.MOD_ID, "block/crafting/$name"))
    }
}
