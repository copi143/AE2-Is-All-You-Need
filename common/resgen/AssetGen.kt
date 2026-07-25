package allyouneed.resgen

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@DslMarker
annotation class AssetGenDsl

@AssetGenDsl
class AssetGen(private val modId: String, private val output: Path) {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val blockStates = mutableListOf<GeneratedFile>()
    private val blockModels = mutableListOf<GeneratedFile>()
    private val itemModels = mutableListOf<GeneratedFile>()

    fun cubeAll(name: String, texture: String = "block/$name") {
        val modelJson = JsonObject().apply {
            addProperty("parent", "minecraft:block/cube_all")
            add("textures", JsonObject().apply {
                addProperty("all", "$modId:$texture")
            })
        }
        blockModels += GeneratedFile("models/block/$name.json", modelJson)
    }

    fun cubeAllWithFullness(
        name: String,
        maxFullness: Int = 4,
        texturePrefix: String = name,
        predicateKey: String = "ae2:fill_level",
    ) {
        for (i in 0..maxFullness) {
            val modelJson = JsonObject().apply {
                addProperty("parent", "minecraft:block/cube_all")
                add("textures", JsonObject().apply {
                    addProperty("all", "$modId:block/${texturePrefix}_$i")
                })
            }
            blockModels += GeneratedFile("models/block/${name}_$i.json", modelJson)
        }

        val stateJson = JsonObject().apply {
            add("variants", JsonObject().apply {
                for (i in 0..maxFullness) {
                    add("fullness=$i", JsonObject().apply {
                        addProperty("model", "$modId:block/${name}_$i")
                    })
                }
            })
        }
        blockStates += GeneratedFile("blockstates/$name.json", stateJson)

        val itemJson = JsonObject().apply {
            addProperty("parent", "$modId:block/${name}_0")
            add("overrides", com.google.gson.JsonArray().apply {
                for (i in 1..maxFullness) {
                    add(JsonObject().apply {
                        addProperty("model", "$modId:block/${name}_$i")
                        add("predicate", JsonObject().apply {
                            addProperty(predicateKey, i * (1.0 / maxFullness))
                        })
                    })
                }
            })
        }
        itemModels += GeneratedFile("models/item/$name.json", itemJson)
    }

    fun simpleBlock(name: String, texture: String = "block/$name") {
        val modelJson = JsonObject().apply {
            addProperty("parent", "minecraft:block/cube_all")
            add("textures", JsonObject().apply {
                addProperty("all", "$modId:$texture")
            })
        }
        blockModels += GeneratedFile("models/block/$name.json", modelJson)

        val stateJson = JsonObject().apply {
            add("variants", JsonObject().apply {
                add("", JsonObject().apply {
                    addProperty("model", "$modId:block/$name")
                })
            })
        }
        blockStates += GeneratedFile("blockstates/$name.json", stateJson)

        val itemJson = JsonObject().apply {
            addProperty("parent", "$modId:block/$name")
        }
        itemModels += GeneratedFile("models/item/$name.json", itemJson)
    }

    fun generate() {
        val all = blockStates + blockModels + itemModels
        for (file in all) {
            val path = output.resolve(file.relativePath)
            path.parent.createDirectories()
            path.writeText(gson.toJson(file.json))
        }
    }

    private data class GeneratedFile(val relativePath: String, val json: JsonObject)
}

fun assetGen(modId: String, output: Path, init: AssetGen.() -> Unit) {
    AssetGen(modId, output).apply(init).generate()
}
