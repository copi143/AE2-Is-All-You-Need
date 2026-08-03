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

    /**
     * Storage cell item: generated model with a tintable status-LED layer on top
     * (layer1 tinted via ItemColors, like vanilla item_storage_cell).
     */
    fun cellItem(name: String, displayName: String, texture: String = "item/$name") {
        translations["item.$modId.$name"] = displayName

        val itemJson = JsonObject().apply {
            addProperty("parent", "minecraft:item/generated")
            add("textures", JsonObject().apply {
                addProperty("layer0", "$modId:$texture")
                addProperty("layer1", "$modId:item/item_storage_cell_light")
            })
        }
        itemModels += GeneratedFile("models/item/$name.json", itemJson)
    }

    /**
     * Drive-cell block model (rendered inside ME drives via StorageCellModels),
     * mirroring vanilla `ae2:block/drive/cells/1k_item_cell`.
     */
    fun driveCellModel(name: String) {
        val modelJson = JsonObject().apply {
            addProperty("parent", "ae2:block/drive/drive_cell")
            add("textures", JsonObject().apply {
                addProperty("cell", "$modId:block/drive/cells/$name")
            })
        }
        blockModels += GeneratedFile("models/block/drive/cells/$name.json", modelJson)
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
     * Block whose model simply inherits another (already existing) model, e.g. reusing
     * AE2's molecular assembler shell via `ae2:block/molecular_assembler`.
     */
    fun parentedBlock(name: String, displayName: String, parent: String) {
        translations["block.$modId.$name"] = displayName

        val modelJson = JsonObject().apply {
            addProperty("parent", parent)
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

        // Empty stub; geometry from BuiltInModelHooks (same as AE2 formed models)
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

    /**
     * Async crafting multiblock unit: cube_all unformed + formed variants.
     * [hasFacing] blocks (host/connector) also vary on `facing`; the connector
     * additionally varies on `powered` (all formed/unformed combos are emitted so
     * every reachable state matches a model). Property-less structural blocks carry
     * the `formed` property, so their blockstate varies `formed=false/true` between
     * the plain and the `_formed` cube_all models.
     */
    fun asyncBlock(name: String, displayName: String, hasFacing: Boolean, hasPowered: Boolean) {
        translations["block.$modId.$name"] = displayName

        val unformedModel = JsonObject().apply {
            addProperty("parent", "minecraft:block/cube_all")
            add("textures", JsonObject().apply {
                addProperty("all", "$modId:block/async/$name")
            })
        }
        blockModels += GeneratedFile("models/block/async/$name.json", unformedModel)

        val formedModel = JsonObject().apply {
            addProperty("parent", "minecraft:block/cube_all")
            add("textures", JsonObject().apply {
                addProperty("all", "$modId:block/async/${name}_formed")
            })
        }
        blockModels += GeneratedFile("models/block/async/${name}_formed.json", formedModel)

        val variants = JsonObject()

        fun addVariant(key: String, model: String) {
            variants.add(key, JsonObject().apply { addProperty("model", "$modId:block/async/$model") })
        }

        val dirs = listOf("north", "south", "east", "west")
        when {
            hasPowered -> for (dir in dirs) for (formed in listOf("false", "true")) for (powered in listOf("false", "true")) {
                addVariant("facing=$dir,formed=$formed,powered=$powered", if (formed == "false") name else "${name}_formed")
            }
            hasFacing -> for (dir in dirs) for (formed in listOf("false", "true")) {
                addVariant("facing=$dir,formed=$formed", if (formed == "false") name else "${name}_formed")
            }
            else -> for (formed in listOf("false", "true")) {
                addVariant("formed=$formed", if (formed == "false") name else "${name}_formed")
            }
        }

        val stateJson = JsonObject().apply { add("variants", variants) }
        blockStates += GeneratedFile("blockstates/$name.json", stateJson)

        val itemJson = JsonObject().apply {
            addProperty("parent", "$modId:block/async/$name")
        }
        itemModels += GeneratedFile("models/item/$name.json", itemJson)
    }

    // ---------------------------------------------------------------------------------------------
    // Async machine frame: ME-controller-style connection textures
    // ---------------------------------------------------------------------------------------------

    /**
     * Frame block with connection textures. The block carries a [connections] mask (one bit per
     * direction, set when the neighbour is another frame); for each mask the per-face texture is
     * `h` when both in-plane horizontal neighbours connect, `v` when both in-plane vertical
     * neighbours connect, else `c`. Models are emitted once per unique face assignment (deduped
     * across the 64 masks), each with an unformed and a `_formed` (animated gradient) variant.
     */
    fun asyncFrameBlock(name: String, displayName: String) {
        translations["block.$modId.$name"] = displayName

        val assignmentByModel = LinkedHashMap<List<String>, String>()
        val modelByMask = LinkedHashMap<Int, String>()
        for (mask in 0 until 64) {
            val assignment = frameFaceTextures(mask)
            val modelId = assignmentByModel.getOrPut(assignment) {
                "async/${name}_conn_${assignmentByModel.size}"
            }
            modelByMask[mask] = modelId
        }

        for ((assignment, modelId) in assignmentByModel) {
            val unformedTextures = JsonObject()
            val formedTextures = JsonObject()
            for ((i, face) in FRAME_FACE_ORDER.withIndex()) {
                unformedTextures.addProperty(face, "$modId:block/async/frame_${assignment[i]}")
                formedTextures.addProperty(face, "$modId:block/async/frame_${assignment[i]}_formed")
            }
            unformedTextures.addProperty("particle", "$modId:block/async/frame_c")
            formedTextures.addProperty("particle", "$modId:block/async/frame_c")

            blockModels += GeneratedFile("models/block/$modelId.json", cubeModel(unformedTextures))
            blockModels += GeneratedFile("models/block/${modelId}_formed.json", cubeModel(formedTextures))
        }

        val stateJson = JsonObject().apply {
            add("variants", JsonObject().apply {
                for ((mask, modelId) in modelByMask) {
                    add("connections=$mask,formed=false", JsonObject().apply {
                        addProperty("model", "$modId:block/$modelId")
                    })
                    add("connections=$mask,formed=true", JsonObject().apply {
                        addProperty("model", "$modId:block/${modelId}_formed")
                    })
                }
            })
        }
        blockStates += GeneratedFile("blockstates/$name.json", stateJson)

        val itemJson = JsonObject().apply {
            addProperty("parent", "$modId:block/${modelByMask.getValue(0)}")
        }
        itemModels += GeneratedFile("models/item/$name.json", itemJson)
    }

    private fun cubeModel(textures: JsonObject): JsonObject = JsonObject().apply {
        addProperty("parent", "minecraft:block/cube")
        add("textures", textures)
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

// ---------------------------------------------------------------------------------------------
// Async machine frame connection-texture rule
// ---------------------------------------------------------------------------------------------

// Bit masks follow net.minecraft.core.Direction order: DOWN, UP, NORTH, SOUTH, WEST, EAST.
// (1 shl ordinal) matches AsyncStructureFrameBlock.refreshConnections.
private const val BIT_DOWN = 1 shl 0
private const val BIT_UP = 1 shl 1
private const val BIT_NORTH = 1 shl 2
private const val BIT_SOUTH = 1 shl 3
private const val BIT_WEST = 1 shl 4
private const val BIT_EAST = 1 shl 5

private val FRAME_FACE_ORDER = listOf("north", "east", "south", "west", "up", "down")

/**
 * Per face, the two in-plane direction bits along the face's u axis (horizontal) and v axis
 * (vertical), matching the vanilla cube UV convention (side faces: u = horizontal perp, v = up/down;
 * up/down faces: u = east/west, v = north/south).
 */
private val FRAME_FACE_AXES: Map<String, Pair<Int, Int>> = mapOf(
    "north" to ((BIT_EAST or BIT_WEST) to (BIT_UP or BIT_DOWN)),
    "south" to ((BIT_EAST or BIT_WEST) to (BIT_UP or BIT_DOWN)),
    "west" to ((BIT_NORTH or BIT_SOUTH) to (BIT_UP or BIT_DOWN)),
    "east" to ((BIT_NORTH or BIT_SOUTH) to (BIT_UP or BIT_DOWN)),
    "up" to ((BIT_EAST or BIT_WEST) to (BIT_NORTH or BIT_SOUTH)),
    "down" to ((BIT_EAST or BIT_WEST) to (BIT_NORTH or BIT_SOUTH)),
)

/** Per-face texture for a [connections] mask, in [FRAME_FACE_ORDER] order. */
private fun frameFaceTextures(mask: Int): List<String> = FRAME_FACE_ORDER.map { face ->
    val (horizontal, vertical) = FRAME_FACE_AXES.getValue(face)
    when {
        mask and horizontal == horizontal -> "h"
        mask and vertical == vertical -> "v"
        else -> "c"
    }
}
