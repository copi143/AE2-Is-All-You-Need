package allyouneed.resgen

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
     * When [hasFormed] is false the block has no `formed` property: only the unformed model is
     * emitted and the blockstate varies on `facing` (or is property-less), e.g. GT machines whose
     * runtime blockstate is generated from their rotation state.
     */
    fun asyncBlock(
        name: String,
        displayName: String,
        hasFacing: Boolean,
        hasPowered: Boolean,
        faces: List<String>? = null,
        formedFaces: List<String>? = null,
        hasFormed: Boolean = true,
    ) {
        translations["block.$modId.$name"] = displayName

        val (unformedModel, formedModel) = if (faces == null) {
            cubeAllModel("$modId:block/async/$name") to cubeAllModel("$modId:block/async/${name}_formed")
        } else {
            faceCubeModel(faces) to faceCubeModel(formedFaces ?: faces)
        }
        blockModels += GeneratedFile("models/block/async/$name.json", unformedModel)
        if (hasFormed) {
            blockModels += GeneratedFile("models/block/async/${name}_formed.json", formedModel)
        }

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

            hasFacing && hasFormed -> for (dir in dirs) for (formed in listOf("false", "true")) {
                addVariant(
                    "facing=$dir,formed=$formed",
                    if (formed == "false") name else "${name}_formed",
                    yaw.getValue(dir)
                )
            }

            hasFacing -> for (dir in dirs) {
                addVariant("facing=$dir", name, yaw.getValue(dir))
            }

            hasFormed -> for (formed in listOf("false", "true")) {
                addVariant("formed=$formed", if (formed == "false") name else "${name}_formed")
            }

            else -> addVariant("", name)
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
     * direction, set when the neighbour is another frame); for each mask the formed per-face
     * texture is `h` when both in-plane horizontal neighbours connect, `v` when both in-plane
     * vertical neighbours connect, else `c`. The connection bands only take effect when formed:
     * every unformed face uses the plain `frame_c` texture, so a half-built frame reads as
     * unconnected until the structure is complete. Models are emitted once per unique face
     * assignment (deduped across the 64 masks), each with an unformed and a `_formed` (animated
     * gradient) variant.
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
                unformedTextures.addProperty(face, "$modId:block/async/frame_c")
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

    // ---------------------------------------------------------------------------------------------
    // GT ME dynamo hatch: GT energy-output-hatch model structure as static per-tier assets
    // ---------------------------------------------------------------------------------------------

    /**
     * GT ME dynamo hatch block: per-tier/per-amperage blockstate + `gtceu:machine` loader model that
     * mirror what GTCEu's `overlayTieredHullModel` would emit through datagen, written as static
     * assets so the machine renders without running GTCEu datagen. Each block reuses GT's
     * `energy_output_hatch` layout: the loader model declares `replaceable_textures`
     * (bottom/top/side) so that once formed the hull is retextured with the controller's casing
     * like any other GT machine part, while the overlay layers (tier-tinted plate + ring +
     * emissive) stay. Per GT's rule the 2A variant reuses the 1A overlay set; 4A/16A/64A use their
     * own amperage sets. The tinted plate and ring are GT's own textures (referenced directly, never
     * bundled); only the emissive arrow is our AE-purple re-theme
     * (`overlay_energy_{n}a_ae_emissive`).
     */
    fun gtDynamoHatchBlock(tiers: List<Pair<String, String>>) {
        for (amp in GT_HATCH_AMPERAGES) {
            val a = if (amp == 2) "1a" else "${amp}a"
            val partName = if (amp == 2) "ae_power_hatch" else "ae_power_hatch_${amp}a"

            blockModels += GeneratedFile("models/block/machine/part/$partName.json", JsonObject().apply {
                addProperty("parent", "gtceu:block/overlay/2_layer/tinted/front")
                add("textures", JsonObject().apply {
                    addProperty("overlay_tint", "gtceu:block/overlay/machine/overlay_energy_${a}_tinted")
                    addProperty("overlay_in", "gtceu:block/overlay/machine/overlay_energy_${a}_in")
                    addProperty(
                        "overlay_out_emissive",
                        "$modId:block/overlay/machine/overlay_energy_${a}_ae_emissive",
                    )
                })
                add("elements", GT_HATCH_ELEMENTS)
            })

            for ((tierId, display) in tiers) {
                if (amp == 64 && tierId !in GT_HATCH_64A_TIERS) continue
                val name = if (amp == 2) "${tierId}_ae_power_hatch" else "${tierId}_ae_power_hatch_${amp}a"
                val displayName = if (amp == 2) "$display AE Power Hatch" else "$display ${amp}A AE Power Hatch"
                translations["block.$modId.$name"] = displayName

                val loaderModelPath = "block/machine/$name"
                val hullTextures = JsonObject().apply {
                    addProperty("bottom", "gtceu:block/casings/voltage/$tierId/bottom")
                    addProperty("top", "gtceu:block/casings/voltage/$tierId/top")
                    addProperty("side", "gtceu:block/casings/voltage/$tierId/side")
                }
                val formedVariant = JsonObject().apply {
                    add("model", JsonObject().apply {
                        addProperty("parent", "$modId:block/machine/part/$partName")
                        add("textures", hullTextures)
                    })
                }
                blockModels += GeneratedFile("models/$loaderModelPath.json", JsonObject().apply {
                    addProperty("parent", "minecraft:block/block")
                    addProperty("loader", "gtceu:machine")
                    addProperty("machine", "$modId:$name")
                    add("replaceable_textures", JsonArray().apply {
                        add("bottom")
                        add("top")
                        add("side")
                    })
                    add("variants", JsonObject().apply {
                        add("is_formed=false", formedVariant)
                        add("is_formed=true", formedVariant)
                    })
                })

                // Facing rotations match GT's generated machine blockstate (x/y Euler + `gtceu:z`).
                val variants = JsonObject().apply {
                    fun addVariant(facing: String, y: Int = 0, x: Int = 0, z: Int = 0) {
                        add("facing=$facing", JsonObject().apply {
                            addProperty("model", "$modId:$loaderModelPath")
                            if (z != 0) addProperty("gtceu:z", z)
                            if (y != 0) addProperty("y", y)
                            if (x != 0) addProperty("x", x)
                        })
                    }
                    addVariant("down", x = 90)
                    addVariant("up", x = 270, z = 180)
                    addVariant("north")
                    addVariant("south", y = 180)
                    addVariant("west", y = 270)
                    addVariant("east", y = 90)
                }
                blockStates += GeneratedFile(
                    "blockstates/$name.json",
                    JsonObject().apply { add("variants", variants) },
                )

                itemModels += GeneratedFile("models/item/$name.json", JsonObject().apply {
                    addProperty("parent", "$modId:$loaderModelPath")
                })
            }
        }
    }

    /**
     * The four elements shared by every dynamo hatch overlay, identical to GT's
     * `models/block/machine/part/energy_output_hatch.json`: the hull (tintindex 1), the
     * tier-tinted overlay (tintindex 2), the ring, and the glowing emissive layer.
     */
    private val GT_HATCH_ELEMENTS: JsonArray = JsonParser.parseString(
        """
        [
            {
                "from": [  0,  0,  0 ],
                "to":   [ 16, 16, 16 ],
                "faces": {
                    "down":  { "texture": "#bottom", "cullface": "down",  "tintindex": 1 },
                    "up":    { "texture": "#top",    "cullface": "up",    "tintindex": 1 },
                    "north": { "texture": "#side",   "cullface": "north", "tintindex": 1 },
                    "south": { "texture": "#side",   "cullface": "south", "tintindex": 1 },
                    "west":  { "texture": "#side",   "cullface": "west",  "tintindex": 1 },
                    "east":  { "texture": "#side",   "cullface": "east",  "tintindex": 1 }
                }
            },
            {
                "from": [ -0.01, -0.01, -0.01 ],
                "to":   [ 16.01, 16.01, 16.01 ],
                "faces": {
                    "north": { "uv": [0, 0, 16, 16], "texture": "#overlay_tint",  "cullface": "north", "tintindex": 2 }
                }
            },
            {
                "from": [ -0.02, -0.02, -0.02 ],
                "to":   [ 16.02, 16.02, 16.02 ],
                "faces": {
                    "north": { "uv": [0, 0, 16, 16], "texture": "#overlay_in",  "cullface": "north" }
                }
            },
            {
                "from": [ -0.02, -0.02, -0.02 ],
                "to":   [ 16.02, 16.02, 16.02 ],
                "forge_data": { "block_light": 15, "sky_light": 15 },
                "shade": false,
                "faces": {
                    "north": { "uv": [0, 0, 16, 16], "texture": "#overlay_out_emissive",  "cullface": "north" }
                }
            }
        ]
        """.trimIndent(),
    ) as JsonArray

    /**
     * The amperage versions of the ME dynamo hatch, matching GT's energy-output-hatch style: 2A is
     * the base variant (no suffix) and, per GT's rule, reuses the 1A overlay set; 4A/16A/64A carry
     * their own suffix and amperage overlay sets, and the 64A variant only exists from EV upward.
     */
    private val GT_HATCH_AMPERAGES = listOf(2, 4, 16, 64)

    /** 64A exists only from EV upward, matching GT's high-amperage hatches. */
    private val GT_HATCH_64A_TIERS = listOf(
        "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max",
    )

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
                val (vars, entries) = splitLangTemplate(localeJson)
                val expanded = expandLangTemplate(entries, vars, enKeys)
                val localeKeys = expanded.keys

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

                val outJson = JsonObject().apply {
                    for ((key, value) in expanded) addProperty(key, value)
                }
                langOut.resolve(file.fileName).writeText(gson.toJson(outJson))
            }
        }
    }

    /**
     * 语言文件模板：顶层 `+vars` 定义 `{name}` 占位符的取值集合，其余条目中 key/value 含
     * `{name}` 的即为模板条目，含 `{name}` 的 key/value 由 [expandLangTemplate] 展开。
     *
     * Splits the language file template into the `+vars` placeholder sets and the remaining
     * entries (plain keys plus `{name}` template entries).
     */
    private fun splitLangTemplate(localeJson: JsonObject): Pair<Map<String, List<String>>, Map<String, String>> {
        val vars = linkedMapOf<String, List<String>>()
        val entries = linkedMapOf<String, String>()
        for ((key, value) in localeJson.entrySet()) {
            if (key == VARS_KEY) {
                for ((name, listEl) in value.asJsonObject.entrySet()) {
                    vars[name] = listEl.asJsonArray.map { it.asString }
                }
            } else {
                entries[key] = value.asString
            }
        }
        return vars to entries
    }

    /**
     * 展开语言文件模板条目：对每个 `{name}` 占位符按 `+vars` 集合做笛卡尔积。key 中占位符
     * 替换为 id 形式（如 `1K` → `1k`、`LuV` → `luv`），value 中保留原样。展开后 key 若不在
     * 英文键集合 [enKeys] 中则跳过，避免为英文不存在的条目生成翻译。
     *
     * Expands `{name}` template entries against the `+vars` sets (Cartesian product). Expanded
     * keys absent from [enKeys] are skipped, so only entries that exist in en_us are emitted.
     */
    private fun expandLangTemplate(
        entries: Map<String, String>,
        vars: Map<String, List<String>>,
        enKeys: Set<String>,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for ((key, value) in entries) {
            val names = PLACEHOLDER_REGEX.findAll(key).map { it.groupValues[1] }.toList() +
                PLACEHOLDER_REGEX.findAll(value).map { it.groupValues[1] }
            if (names.isEmpty()) {
                result[key] = value
                continue
            }
            for (name in names.toSet()) {
                require(name in vars) {
                    "Undefined placeholder {$name} in lang template '$key'; add it under \"$VARS_KEY\""
                }
            }
            for (combo in placeholderCombinations(names.distinct(), vars)) {
                val expandedKey = renderPlaceholders(key, combo, idify = true)
                if (expandedKey !in enKeys) continue
                result[expandedKey] = renderPlaceholders(value, combo, idify = false)
            }
        }
        return result
    }

    /** 各占位符取值集合的笛卡尔积。 */
    private fun placeholderCombinations(
        names: List<String>,
        vars: Map<String, List<String>>,
    ): List<Map<String, String>> {
        if (names.isEmpty()) return listOf(emptyMap())
        val head = names.first()
        return vars.getValue(head).flatMap { headValue ->
            placeholderCombinations(names.drop(1), vars).map { it + (head to headValue) }
        }
    }

    private fun renderPlaceholders(
        template: String,
        combo: Map<String, String>,
        idify: Boolean,
    ): String {
        var out = template
        for ((name, value) in combo) {
            val rendered = if (idify) idifyPlaceholder(value) else value
            out = out.replace("{$name}", rendered)
        }
        return out
    }

    /** 占位符值在 key 中的 id 形式（小写 snake），与 `CellEntry.id` 保持一致。 */
    private fun idifyPlaceholder(value: String): String =
        value.lowercase().replace(" ", "_").replace("-", "_").replace(".", "_")

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
// Language file templates: `+vars` placeholder sets + `{name}` template entries
// ---------------------------------------------------------------------------------------------

/** Top-level key of a language file holding the `{name}` placeholder value sets. */
private const val VARS_KEY = "+vars"

/** Matches `{name}` placeholders inside language file keys/values. */
private val PLACEHOLDER_REGEX = Regex("""\{([a-zA-Z0-9_]+)}""")

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
