package allyouneed.resgen

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

@DslMarker
annotation class AssetGenDsl

@AssetGenDsl
class AssetGen(private val modId: String, private val output: Path, private val langDir: Path? = null) {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val blockStates = mutableListOf<GeneratedFile>()
    private val blockModels = mutableListOf<GeneratedFile>()
    private val itemModels = mutableListOf<GeneratedFile>()
    private val translations = linkedMapOf<String, String>()

    fun cubeAll(name: String, texture: String = "block/$name") {
        val modelJson = JsonObject().apply {
            addProperty("parent", "minecraft:block/cube_all")
            add("textures", JsonObject().apply {
                addProperty("all", "$modId:$texture")
            })
        }
        blockModels += GeneratedFile("models/block/$name.json", modelJson)
    }

    fun translation(key: String, value: String) {
        translations[key] = value
    }

    fun item(name: String, displayName: String, texture: String = "item/$name") {
        translations["item.$modId.$name"] = displayName

        val itemJson = JsonObject().apply {
            addProperty("parent", "minecraft:item/generated")
            add("textures", JsonObject().apply {
                addProperty("layer0", "$modId:$texture")
            })
        }
        itemModels += GeneratedFile("models/item/$name.json", itemJson)
    }

    fun cubeAllWithFullness(
        name: String,
        displayName: String,
        maxFullness: Int = 4,
        texturePrefix: String = name,
        predicateKey: String = "ae2:fill_level",
    ) {
        translations["block.$modId.$name"] = displayName
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

    fun simpleBlock(name: String, displayName: String, texture: String = "block/$name") {
        translations["block.$modId.$name"] = displayName

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

    /**
     * Crafting storage: unformed cube_all + formed built-in model stub (empty JSON loader id).
     */
    fun craftingStorageBlock(name: String, displayName: String) {
        translations["block.$modId.$name"] = displayName

        val unformedModel = JsonObject().apply {
            addProperty("parent", "minecraft:block/cube_all")
            add("textures", JsonObject().apply {
                addProperty("all", "$modId:block/$name")
            })
        }
        blockModels += GeneratedFile("models/block/$name.json", unformedModel)

        // Empty model file; actual geometry comes from BuiltInModelHooks + ModelBakeryMixin
        blockModels += GeneratedFile("models/block/crafting/${name}_formed.json", JsonObject())

        val stateJson = JsonObject().apply {
            add("variants", JsonObject().apply {
                add("formed=false", JsonObject().apply {
                    addProperty("model", "$modId:block/$name")
                })
                add("formed=true", JsonObject().apply {
                    addProperty("model", "$modId:block/crafting/${name}_formed")
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

        val langOut = output.resolve("lang")
        langOut.createDirectories()

        if (translations.isNotEmpty()) {
            val langJson = JsonObject().apply {
                for ((key, value) in translations) {
                    addProperty(key, value)
                }
            }
            langOut.resolve("en_us.json").writeText(gson.toJson(langJson))
        }

        val enKeys = translations.keys

        if (langDir != null && langDir.exists()) {
            for (file in langDir.listDirectoryEntries("*.json")) {
                val locale = file.fileName.toString().removeSuffix(".json")
                if (locale == "en_us") continue

                val localeJson = gson.fromJson(file.readText(), JsonObject::class.java)
                val localeKeys: Set<String> = localeJson.keySet().toSet()

                val missing = enKeys - localeKeys
                val extra = localeKeys - enKeys

                if (missing.isNotEmpty()) {
                    println("[lang/$locale.json] Missing keys: ${missing.joinToString()}")
                }
                if (extra.isNotEmpty()) {
                    println("[lang/$locale.json] Extra keys not in en_us: ${extra.joinToString()}")
                }
                if (missing.isEmpty() && extra.isEmpty()) {
                    println("[lang/$locale.json] OK (${localeKeys.size} keys)")
                }

                langOut.resolve(file.fileName).writeText(gson.toJson(localeJson))
            }
        }
    }

    private data class GeneratedFile(val relativePath: String, val json: JsonObject)
}

fun assetGen(modId: String, output: Path, langDir: Path? = null, init: AssetGen.() -> Unit) {
    AssetGen(modId, output, langDir).apply(init).generate()
}
