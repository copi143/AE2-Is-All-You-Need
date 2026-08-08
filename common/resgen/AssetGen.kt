package allyouneed.resgen

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Path
import kotlin.io.path.*

@DslMarker
annotation class AssetGenDsl

@AssetGenDsl
class AssetGen(
    private val modId: String,
    private val output: Path,
    private val langDir: Path? = null,
    /** Root for datapack resources (`data/<modid>/...`). Defaults to sibling of assets root. */
    private val dataOutput: Path? = null,
) {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val blockStates = mutableListOf<GeneratedFile>()
    private val blockModels = mutableListOf<GeneratedFile>()
    private val itemModels = mutableListOf<GeneratedFile>()
    private val dataFiles = mutableListOf<GeneratedFile>()
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

    /**
     * 写入物品标签 `data/<modId>/tags/items/<path>.json`。
     * [required] 为普通字符串；[optional] 为 `{ "id": "...", "required": false }`，缺失模组不导致加载失败。
     *
     * Writes an item tag under `data/<modId>/tags/items/<path>.json`.
     * [required] entries are plain strings; [optional] use optional id objects so missing mods do not fail load.
     */
    fun itemTag(
        path: String,
        required: List<String> = emptyList(),
        optional: List<String> = emptyList(),
        replace: Boolean = false,
    ) {
        val json = JsonObject().apply {
            addProperty("replace", replace)
            add("values", com.google.gson.JsonArray().apply {
                for (id in required) add(id)
                for (id in optional) {
                    add(JsonObject().apply {
                        addProperty("id", id)
                        addProperty("required", false)
                    })
                }
            })
        }
        dataFiles += GeneratedFile("tags/items/$path.json", json)
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
     * Async crafting multiblock unit: unformed + formed variants. By default both are `cube_all`
     * using the block's own textures; when [faces] is given the block renders as a directional cube
     * with a per-face texture per [FRAME_FACE_ORDER] entry (texture names under `block/async/`),
     * and [formedFaces] (same order) overrides the formed per-face set, defaulting to [faces].
     * [hasFacing] blocks (host/connector) also vary on `facing` (with y-rotation so the model's
     * `north` face points in the facing direction); the connector additionally varies on `powered`
     * (all formed/unformed combos are emitted so every reachable state matches a model).
     * Property-less structural blocks carry the `formed` property, so their blockstate varies
     * `formed=false/true` between the plain and the `_formed` models.
     */
    fun asyncBlock(
        name: String,
        displayName: String,
        hasFacing: Boolean,
        hasPowered: Boolean,
        faces: List<String>? = null,
        formedFaces: List<String>? = null,
    ) {
        translations["block.$modId.$name"] = displayName

        val (unformedModel, formedModel) = if (faces == null) {
            cubeAllModel("$modId:block/async/$name") to cubeAllModel("$modId:block/async/${name}_formed")
        } else {
            faceCubeModel(faces) to faceCubeModel(formedFaces ?: faces)
        }
        blockModels += GeneratedFile("models/block/async/$name.json", unformedModel)
        blockModels += GeneratedFile("models/block/async/${name}_formed.json", formedModel)

        val variants = JsonObject()

        fun addVariant(key: String, model: String, y: Int = 0) {
            variants.add(key, JsonObject().apply {
                addProperty("model", "$modId:block/async/$model")
                if (y != 0) addProperty("y", y)
            })
        }

        val dirs = listOf("north", "south", "east", "west")
        val yaw = mapOf("north" to 0, "south" to 180, "east" to 90, "west" to 270)
        when {
            hasPowered -> for (dir in dirs) for (formed in listOf("false", "true")) for (powered in listOf(
                "false",
                "true"
            )) {
                addVariant(
                    "facing=$dir,formed=$formed,powered=$powered",
                    if (formed == "false") name else "${name}_formed",
                    yaw.getValue(dir)
                )
            }

            hasFacing -> for (dir in dirs) for (formed in listOf("false", "true")) {
                addVariant(
                    "facing=$dir,formed=$formed",
                    if (formed == "false") name else "${name}_formed",
                    yaw.getValue(dir)
                )
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

    private fun cubeAllModel(texture: String): JsonObject = JsonObject().apply {
        addProperty("parent", "minecraft:block/cube_all")
        add("textures", JsonObject().apply { addProperty("all", texture) })
    }

    /** `block/cube` model with per-face textures in [FRAME_FACE_ORDER], particle = first face. */
    private fun faceCubeModel(faces: List<String>): JsonObject = JsonObject().apply {
        addProperty("parent", "minecraft:block/cube")
        add("textures", JsonObject().apply {
            for ((i, face) in FRAME_FACE_ORDER.withIndex()) {
                addProperty(face, "$modId:block/async/${faces[i]}")
            }
            addProperty("particle", "$modId:block/async/${faces[0]}")
        })
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

        val dataRoot = dataOutput ?: output.parent?.parent?.resolve("data")?.resolve(modId)
        if (dataRoot != null && dataFiles.isNotEmpty()) {
            for (file in dataFiles) {
                val path = dataRoot.resolve(file.relativePath)
                path.parent.createDirectories()
                path.writeText(gson.toJson(file.json))
            }
            println("[data] wrote ${dataFiles.size} tag file(s) to $dataRoot")
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

fun assetGen(
    modId: String,
    output: Path,
    langDir: Path? = null,
    dataOutput: Path? = null,
    init: AssetGen.() -> Unit,
) {
    AssetGen(modId, output, langDir, dataOutput).apply(init).generate()
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
