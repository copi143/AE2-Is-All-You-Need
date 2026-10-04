package allyouneed.resgen

import com.google.gson.JsonObject

/** Generated item model layers, ordered as layer0..layer4 (also their ItemColors tint indices). */
@AssetGenDsl
class ItemModelLayers {
    private val textures = mutableListOf<String>()

    /** Unqualified textures use the current mod namespace; explicit namespaces are preserved. */
    fun layer(texture: String) {
        require(texture.isNotBlank()) { "Item layer texture must not be blank" }
        require(textures.size < 5) { "Generated item models support at most 5 layers" }
        textures += texture
    }

    internal fun build(modId: String): JsonObject {
        require(textures.isNotEmpty()) { "An item model needs at least one layer" }
        return JsonObject().apply {
            addProperty("parent", "minecraft:item/generated")
            add("textures", JsonObject().apply {
                for ((index, texture) in textures.withIndex()) {
                    addProperty("layer$index", if (':' in texture) texture else "$modId:$texture")
                }
            })
        }
    }
}
