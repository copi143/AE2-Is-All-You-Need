package allyouneed.resgen

import allyouneed.util.idify
import com.google.gson.JsonParser
import minecraftx.compose.itemdetail.ItemDetailsKeyBind
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

data class CellEntry(
    val displayName: String,
    val color: String,
    val isCreative: Boolean = false,
    val isSelfPowered: Boolean = false,
) {
    val id = idify(displayName)

    /** Id of the matching drive-cell block model/texture (`<tier>_<type>_cell`). */
    val itemCellId = id.removeSuffix("_storage_cell") + "_cell"
}

private val energyCells = tiers.flatMapIndexed { i, tier ->
    listOf(
        CellEntry("$tier Energy Cell", AE2_COLORS[i].hex),
        CellEntry("$tier Self-Powered Energy Cell", AE2_COLORS[i].hex, isSelfPowered = true),
    )
} + CellEntry("Creative Energy Cell", AE2_COLOR_CREATIVE.hex, isCreative = true)

private val craftingStorages = tiers.mapIndexed { i, tier ->
    CellEntry("$tier Crafting Storage", AE2_COLORS[i].hex)
} + CellEntry("Creative Crafting Storage", AE2_COLOR_CREATIVE.hex, isCreative = true)

// ME storage cell groups, one per key type, colored per AE2_COLORS like the other tiers.
private fun storageCells(label: String) = tiers.mapIndexed { i, tier ->
    CellEntry("$tier $label Storage Cell", AE2_COLORS[i].hex)
}

/** All storage cell groups by key type id (lowercase, matches CellHousings/registered ids); drives bg derivation, textures and models. */
val storageCellGroups = LinkedHashMap<String, List<CellEntry>>().apply {
    for ((label, cells) in aeKeyLabels.associateWith { storageCells(it) }) {
        set(label.lowercase(), cells)
    }
}

/**
 * Async synthesis block definition. Both definition files (with/without GT) describe the same
 * 16-block set and only differ in [isGt]: the six GT-owned blocks (the three controllers and the
 * three connectors) become GTCEu machines at runtime. Textures and models are generated from the
 * shared fields, so the two files deliberately produce identical assets.
 */
data class AsyncBlockDef(
    val id: String,
    val displayName: String,
    val color: String,
    val role: String,
    val hasFacing: Boolean,
    val hasPowered: Boolean,
    val isGt: Boolean,
)

/**
 * Reads one of the two async block definition files from the supplied definitions directory.
 */
private fun loadAsyncDefinitions(definitionsDir: Path, fileName: String): List<AsyncBlockDef> {
    val path = definitionsDir.resolve(fileName)
    require(path.exists()) { "Missing async block definitions: $path" }
    val root = JsonParser.parseReader(path.toFile().reader()).asJsonObject
    return root.getAsJsonArray("blocks").map { element ->
        val obj = element.asJsonObject
        AsyncBlockDef(
            id = obj.get("id").asString,
            displayName = obj.get("displayName").asString,
            color = obj.get("color").asString,
            role = obj.get("role").asString,
            hasFacing = obj.get("hasFacing").asBoolean,
            hasPowered = obj.get("hasPowered").asBoolean,
            isGt = obj.get("isGt").asBoolean,
        )
    }
}

private fun loadAsyncBlockSet(definitionsDir: Path): List<AsyncBlockDef> {
    val gt = loadAsyncDefinitions(definitionsDir, "async_blocks_gt.json")
    val vanilla = loadAsyncDefinitions(definitionsDir, "async_blocks_vanilla.json")

    // Both files must describe the exact same blocks; only the isGt flags may differ. The static
    // cube_all models are always emitted for all 16 so a Forge install without GTCEu (and Fabric)
    // renders everything; when GTCEu is present its registrate virtual resource pack overrides the
    // blockstate/model of the six isGt blocks, and the PNGs are the same in both cases.
    require(gt.map { it.id } == vanilla.map { it.id }) {
        "GT/vanilla async definitions must list the same blocks in the same order"
    }
    require(gt.zip(vanilla).all { (g, v) -> g.copy(isGt = v.isGt) == v }) {
        "GT/vanilla async definitions must share id/displayName/color/role/facing/powered"
    }
    val gtCount = gt.count { it.isGt }
    println("[async] ${gt.size} blocks, $gtCount GT-owned, ${gt.size - gtCount} plain")

    // Shared field set drives generation; isGt is runtime metadata only.
    return vanilla
}

class ModInfo(
    val id: String,
    val name: String,
    val author: String,
    val license: String,
    val credits: String,
    val description: String,
)

fun main(args: Array<String>) {
    require(args.isNotEmpty())
    require(args.size % 2 == 0)

    var inputDir: Path? = null
    var outputDir: Path? = null
    var modId = ""
    var modName = ""
    var modAuthor = ""
    var modLicense = ""
    var modCredits = ""
    var modDescription = ""

    args.asList().chunked(2) {
        when (it[0]) {
            "-i" -> inputDir = Path.of(it[1]).toAbsolutePath().normalize()
            "-o" -> outputDir = Path.of(it[1]).toAbsolutePath().normalize()
            "-m" -> modId = it[1]
            "-n" -> modName = it[1]
            "-a" -> modAuthor = it[1]
            "-l" -> modLicense = it[1]
            "-c" -> modCredits = it[1]
            "-d" -> modDescription = it[1]
            else -> throw IllegalArgumentException("Unknown argument `${it[0]}`")
        }
    }

    if (inputDir == null || !inputDir.exists()) throw IllegalArgumentException("Missing or invalid input directory `-i <inputDir>`")
    if (outputDir == null) throw IllegalArgumentException("Missing output directory `-o <outputDir>`")
    if (modId.isEmpty()) throw IllegalArgumentException("Missing required argument `-m <modId>`")
    if (modName.isEmpty()) modName = modId

    gen(inputDir, outputDir, ModInfo(modId, modName, modAuthor, modLicense, modCredits, modDescription))
}

fun gen(inputDir: Path, outputDir: Path, mod: ModInfo) {
    val modId = mod.id
    val output = outputDir.resolve("assets/${mod.id}")
    val dataOutput = outputDir.resolve("data/${mod.id}")
    val sourceTextures = inputDir.resolve("textures")
    val langDir = inputDir.resolve("lang")

    val asyncStructureBlocks = loadAsyncBlockSet(inputDir.resolve("definitions"))

    assetGen(mod.id, output, langDir, dataOutput) {
        // Machine assembler: recipe-category → accepted machine items (optional mods ignored)
        machineItemTags()

        // EMI 同类折叠 tooltip 文案
        tooltip("emi_fold") {
            text("group", "%s (×%s)")
            text("more", "... and %s more")
            text("hint_expand", "Alt+Click to expand")
            text("hint_collapse", "Alt+Click to collapse")
            text("member", "Group: %s · Alt+Click to collapse")
            text("hint_open", "Alt+Click to browse this group")
            text("hint_inline_expand", "Alt+Click to expand this group here")
            text("back", "Back to the previous group")
            text("root", "Back to all groups in this category")
            text("building", "Classifying...")
            text("filter", "%s · %s matching entries")
            text("search_results", "Search: %s entries")
            for ((kind, name, short) in listOf(
                Triple("all", "All", "A"), Triple("block", "Blocks", "B"), Triple("item", "Items", "I"),
                Triple("fluid", "Fluids", "F"), Triple("custom", "Other resources", "O"),
            )) {
                text("kind_$kind", name)
                text("kind_${kind}_short", short)
            }
        }

        translation("itemGroup.$modId", "AE2 Is All You Need")
        translation(ItemDetailsKeyBind.CATEGORY_ID, "AE2 Is All You Need")
        translation(ItemDetailsKeyBind.KEY_ID, "Open Item Details")
        gui("group") {
            text("all", "ALL")
            text("ae2", "AE2")
        }
        gui("adaptive_probability", "Probability (p)")
        gui("adaptive_timeout", "Timeout (T)")
        gui("machine_assembler", "Molecular Assembler")
        gui("pattern_encoding_terminal", "ME Pattern Encoding Terminal")
        gui("encoding") {
            text("machine", "Machine Pattern")
            text("processing", "Processing Pattern")
            text("probability", "Probability Pattern")
            text("pseudo", "Pseudo Pattern")
        }
        gui("encode_failed", "Cannot encode pattern")
        gui("machine_slot", "Machine")
        gui("machine_slot_no_machine", "No machine selected")
        gui("machine_slot_hint", "Click to change machine")

        // Async processing status GUI
        gui("async.status") {
            text("title", "Async Processing Status")
            text("formed", "Formed")
            text("unformed", "Not formed")
            text("connected", "Grid connected")
            text("disconnected", "No grid connection")
            text("swallowed", "Channels swallowed: %s")
            text("swallowed_infinite", "Channels swallowed: Infinite")
            text("storage", "Storage: %s MB")
            text("block_count", "Blocks: %s")
            text("working", "Working")
            text("not_working", "Not working")
        }

        gui("mac", "MAC: %s")
        gui("mac_named", "MAC (%s): %s")
        gui("mac_item", "MAC: %s")
        translation("config.jade.plugin_$modId.mac", "MAC Address")
        tooltip("plane_bus") {
            text("members", "Annihilation planes: %s | Formation planes: %s")
            text("unformed", "Structure not formed")
            text("buses", "Interconnected plane buses: %s")
        }

        gui("machine") {
            text("crafting", "Crafting")
            text("smelting", "Smelting")
            text("blasting", "Blasting")
            text("smoking", "Smoking")
            text("example_custom", "Example Custom")
        }

        // Mana key display names (MetricLevelKey: gui.<modid>.<type>.<metric>)
        gui("mana") {
            text("ae2", "AM")
            text("botania", "Botania Mana")
            text("bloodmagic", "Blood Magic LP")
            text("ars_nouveau", "Ars Nouveau Mana")
        }

        for (cell in energyCells) {
            if (cell.isCreative) {
                simpleBlock(cell.id, cell.displayName)
            } else {
                cubeAllWithFullness(cell.id, cell.displayName)
            }
        }

        for (storage in craftingStorages) {
            craftingStorageBlock(storage.id, storage.displayName)
        }

        for (async in asyncStructureBlocks) {
            when (async.role) {
                "FRAME" -> asyncFrameBlock(async.id, async.displayName)
                // Tower: directional faces (horizontal row of towers → east/west, vertical → up/down).
                "TOWER" -> asyncBlock(
                    async.id, async.displayName, async.hasFacing, async.hasPowered,
                    faces = faces {
                        all("tower")
                        east("tower_h")
                        west("tower_h")
                        up("tower_v")
                        down("tower_v")
                    },
                )
                // Module interface: the socket face (with pin holes) points in the facing direction.
                "INTERFACE" -> asyncBlock(
                    async.id, async.displayName, async.hasFacing, async.hasPowered,
                    faces = faces {
                        all("socket")
                        north("socket_up")
                    },
                    formedFaces = faces {
                        all("socket")
                        north("socket_up_formed")
                    },
                )

                else -> asyncBlock(async.id, async.displayName, async.hasFacing, async.hasPowered)
            }
        }

        // GT AE power hatch: ULV..MAX for the 2A/4A/16A/64A variants (GT style, 2A is the base
        // variant reusing the 1A overlay set). Each gets a static GT-styled blockstate + model (see
        // AssetGen.gtAEPowerHatchBlock) that mirror GTCEu's `overlayTieredHullModel` datagen output,
        // so the machine renders GT's tiered hull + overlay port without running datagen. Only the
        // emissive arrow is ours, re-themed to AE purple (AsyncTextures.generateGtAEPowerHatchOverlays);
        // the tinted plate and ring are GT's own assets referenced directly.
        gtAEPowerHatchBlock(gtMultiBlockTiers)
        block("ae_power_hatch.tooltip", "The ME network draws the stored EU directly (EU to FE to AE)")

        simpleBlock("me_io_drive", "ME IO Drive")
        simpleBlock("network_logger", "ME Network Logger")

        gui("log") {
            text("title", "ME Network Logger")
            group("cat") {
                text("topology", "Topology")
                text("device", "Devices")
                text("energy", "Energy")
                text("crafting", "Crafting")
            }
            group("status") {
                text("online", "Online · %s entries")
                text("offline", "Offline · %s entries")
                text("conflict", "Conflict · %s entries")
            }
            text("conflict_banner", "Multiple loggers on this network; all recording is stopped.")
            text("page", "%s–%s / %s")
            text("clear", "Clear")
            text("download", "Download")
            text("downloaded", "Saved logs to %s")
            text("download_failed", "Failed to save logs: %s")
            text("boot_start", "Network boot started")
            text("boot_end", "Network boot finished")
            text("controller_online", "Controller online")
            text("controller_none", "No controller")
            text("controller_conflict", "Controller conflict")
            text("channel_req", "Channel requirement changed: %s @ %s")
            text("node_added", "Device joined: %s @ %s")
            text("node_removed", "Device left: %s @ %s")
            text("node_power_on", "Device powered: %s @ %s")
            text("node_power_off", "Device unpowered: %s @ %s")
            text("node_channel_on", "Device got channel: %s @ %s")
            text("node_channel_off", "Device lost channel: %s @ %s")
            text("power_on", "Network powered")
            text("power_off", "Network lost power")
            text("craft_submit_ok", "Crafting submitted: %s")
            text("craft_submit_fail", "Crafting submit failed: %s (%s)")
            text("craft_start", "Crafting started: %s")
            text("craft_done", "Crafting finished: %s")
            text("craft_cancel", "Crafting cancelled: %s")
            text("cpu_change", "Crafting CPU changed: %s @ %s")
            text("logger_conflict", "Logger conflict (%s devices)")
            text("logger_ok", "Logger conflict cleared")
            text("unknown", "Unknown event")
        }

        // Adaptive Pattern item (just an item model, no block)
        item("adaptive_pattern", "Adaptive Pattern")

        // Storage cells: LED item model + drive-cell block model pipeline, one group per key type
        for ((_, cells) in storageCellGroups) {
            for (cell in cells) {
                item(cell.id, cell.displayName) {
                    layer("item/${cell.id}")
                    // Status LED: layer1 is tinted by ItemColors.
                    layer("item/item_storage_cell_light")
                }
                driveCellModel(cell.itemCellId)
            }
        }

        // Cell housings: one per key type, texture = <type>_storage_cell_bg + storage_cell_case_fg
        for ((type, _) in storageCellGroups) {
            val label = when (type) {
                "item" -> "Item"
                "fluid" -> "Fluid"
                "mana" -> "Mana"
                "energy" -> "Energy"
                "hp" -> "HP"
                "sta" -> "STA"
                "xp" -> "XP"
                else -> type.replaceFirstChar { it.uppercase() }
            }
            item("${type}_cell_housing", "ME $label Cell Housing")
        }

        // Cell components: 20 tiers, texture via cell_component_bg + cell_component_fg
        for ((i, tier) in tiers.withIndex()) {
            val id = "cell_component_${tier.lowercase()}"
            val display = "${tier.uppercase()} ME Storage Component"
            item(id, display)
        }

        itemLang("creative_me_cell", "Creative ME Storage Cell")
        itemLang("dimensional_cell", "Dimensional Storage Cell")

        // Machine Assembler: reuse AE2's molecular assembler shell model/texture
        parentedBlock("molecular_assembler", "Molecular Assembler", "ae2:block/molecular_assembler")

        // Machine Pattern item (just an item model, no block)
        item("machine_pattern", "Machine Pattern")
        itemLang("pattern_encoding_terminal", "ME Pattern Encoding Terminal")
        itemLang("wireless_omni_terminal", "Wireless Omni Terminal")
        itemLang("pseudo_pattern", "Pseudo Pattern")
        itemLang("plane_bus", "ME Annihilation/Formation Plane Bus")

        // Packet item: single item; icon switches per AEKeyType via model overrides.
        // 变体顺序与 AllPackets.ICON_VALUES 的谓词值一一对应（0.125 起，0.125 步进）。
        packetItem(
            "packet", "Packet", listOf(
                "item" to "item/item_icon",
                "fluid" to "item/fluid_icon",
                "energy" to "item/energy_icon",
                "mana" to "item/mana_icon",
                "hp" to "item/hp_icon",
                "sta" to "item/sta_icon",
                "xp" to "item/xp_icon",
            )
        )

        translation("item.$modId.packet.typed", "Packet (%s)")
    }

    retexture(output) {
        val gradientHex = AE2_GRADIENT.map { it.hex }

        source(sourceTextures) {
            // Energy-cell FG dominant color ≈ rgb 152,194,231.
            source("energy_cell", color = "#98C2E7") {
                for (cell in energyCells) {
                    layered(cell.id) {
                        layer("energy_cell_bg")
                        if (cell.isCreative) {
                            layer("energy_cell_fg", colors = gradientHex)
                            layer("energy_cell_creative")
                        } else {
                            layer("energy_cell_fg", color = cell.color)
                            layer("energy_cell", levels = 0..4)
                        }
                        if (cell.isSelfPowered) layer("energy_cell_self_powered")
                    }
                }
            }

            // Storage-cell FG dominant color ≈ rgb 154,130,255.
            source("storage_cell", color = "#9A82FF") {
                // Derive type backgrounds from the item template using drive-plate themes.
                // The near-neutral item background itself is an unstable chroma source.
                for (type in storageCellGroups.keys - "item") {
                    deriveTemplate(
                        source = "item_storage_cell_bg",
                        themeFrom = "drive_item_cell_bg",
                        themeTo = "drive_${type}_cell_bg",
                        output = "${type}_storage_cell_bg",
                    )
                }

                for ((type, cells) in storageCellGroups) {
                    for (cell in cells) {
                        layered(cell.id, dir = "item") {
                            layer("${type}_storage_cell_bg")
                            layer("storage_cell_fg", color = cell.color)
                        }
                        // The drive-cell model samples only rows/columns 0–6.
                        layered("drive/cells/${cell.itemCellId}") {
                            layer("drive_${type}_cell_bg")
                            layer("drive_cell_fg", color = cell.color)
                        }
                    }
                }

                // Component FG dominant color ≈ rgb 94,170,251; same source directory.
                source(".", color = "#5EAAFB") {
                    for ((i, tier) in tiers.withIndex()) {
                        layered("cell_component_${tier.lowercase()}", dir = "item") {
                            layer("cell_component_bg")
                            layer("cell_component_fg", color = AE2_COLORS[i].hex)
                        }
                    }
                }

                for (type in storageCellGroups.keys) {
                    layered("${type}_cell_housing", dir = "item") {
                        layer("${type}_storage_cell_bg")
                        layer("storage_cell_case_fg")
                    }
                }
            }

            // Crafting-storage FG dominant color ≈ rgb 235,142,75.
            source("crafting_storage", color = "#EB8E4B") {
                for (storage in craftingStorages) {
                    layered(storage.id) {
                        layer("crafting_storage_bg")
                        layer("crafting_storage_fg", color = storage.color)
                    }
                    layered("crafting/${storage.id}_light") {
                        layer("crafting_storage_light", color = storage.color)
                    }
                }
            }

            // White glow masks use flat tinting, which needs no source color.
            source("async") {
                for (variant in listOf("c", "h", "v")) {
                    layered("async/frame_${variant}_formed") {
                        layer("frame_$variant")
                        layer("frame_light_$variant", colors = gradientHex, tint = true)
                    }
                }

                // Source *_formed.png files are previews; generate the animated strips from masks.
                for (core in listOf("storage_core", "execution_core")) {
                    layered("async/async_${core}_formed") {
                        layer(core)
                        layer("${core}_formed_light", colors = gradientHex, tint = true)
                    }
                }
            }
        }

        // Async structure blocks: dedicated pixel-art textures (AsyncTextures) are generated after
        // the retexture block below, shared by both the GT and the no-GT definition files.
    }

    generateRecipes(dataOutput, modId)

    val texOut = output.resolve("textures/block")
    texOut.createDirectories()
    sourceTextures.resolve("placeholder.png").copyTo(texOut.resolve("me_io_drive.png"), overwrite = true)
    sourceTextures.resolve("placeholder.png").copyTo(texOut.resolve("network_logger.png"), overwrite = true)

    // Async machine frame: base connection textures (unformed faces) referenced by the generated
    // models. The `_formed` variants are the animated strips produced by the retexture block above.
    val asyncTexOut = texOut.resolve("async")
    asyncTexOut.createDirectories()
    fun copyAsyncTexture(source: String, dest: String = source) {
        val src = sourceTextures.resolve("async/$source.png")
        if (src.exists()) {
            src.copyTo(asyncTexOut.resolve("$dest.png"), overwrite = true)
        }
    }

    for (variant in listOf("c", "h", "v")) {
        copyAsyncTexture("frame_$variant")
    }

    // Hand-drawn cube_all block faces, renamed to the block-id textures the models reference.
    // storage_core/execution_core `_formed` are the animated strips generated above, so only their
    // unformed base is copied here.
    for ((source, blockId) in listOf(
        "wall" to "async_machine_block",
        "glass" to "async_machine_glass",
        "energy_core" to "async_energy_core",
        "computing_core" to "async_computing_core",
        "storage_core" to "async_storage_core",
        "execution_core" to "async_execution_core",
    )) {
        copyAsyncTexture(source, blockId)
        if (source != "storage_core" && source != "execution_core") {
            copyAsyncTexture("${source}_formed", "${blockId}_formed")
        }
    }

    // Directional face textures referenced by the tower (tower/tower_h/tower_v) and the module
    // interface (socket/socket_up/socket_up_formed) models, kept under their source names.
    for (face in listOf("tower", "tower_h", "tower_v")) {
        copyAsyncTexture(face)
    }
    for (face in listOf("socket", "socket_up", "socket_up_formed")) {
        copyAsyncTexture(face)
    }

    val craftingTexOut = texOut.resolve("crafting")
    craftingTexOut.createDirectories()

    // Status-LED item layer: single-pixel dot, tinted at runtime by StorageCellItem.getColor.
    val itemTexOut = output.resolve("textures/item")
    itemTexOut.createDirectories()
    sourceTextures.resolve("storage_cell/storage_cell_light.png")
        .copyTo(itemTexOut.resolve("item_storage_cell_light.png"), overwrite = true)

    // Light overlays live under block/crafting/; ensure atlas + dummy model stitch them
    // (vanilla already scans textures/block/, but keep explicit for clarity).
    val atlasDir = outputDir.resolve("assets/minecraft/atlases")
    atlasDir.createDirectories()
    atlasDir.resolve("blocks.json").writeText(
        """
        {
          "sources": [
            {
              "type": "directory",
              "source": "block/crafting",
              "prefix": "block/crafting/"
            }
          ]
        }
        """.trimIndent() + "\n",
    )

    val modelsCrafting = output.resolve("models/block/crafting")
    modelsCrafting.createDirectories()
    val texEntries = linkedMapOf("particle" to "$modId:block/crafting/${craftingStorages.first().id}_light")
    for (s in craftingStorages) {
        texEntries["light_${s.id}"] = "$modId:block/crafting/${s.id}_light"
    }
    val texJson = texEntries.entries.joinToString(",\n") { (k, v) -> """    "$k": "$v"""" }
    modelsCrafting.resolve("atlas_materials.json").writeText(
        "{\n  \"parent\": \"minecraft:block/block\",\n  \"textures\": {\n$texJson\n  }\n}\n",
    )

    val lightMcmeta = sourceTextures.resolve("crafting_storage/crafting_storage_light.png.mcmeta")
    if (lightMcmeta.exists()) {
        for (storage in craftingStorages) {
            lightMcmeta.copyTo(craftingTexOut.resolve("${storage.id}_light.png.mcmeta"), overwrite = true)
        }
    }

    val apTex = sourceTextures.resolve("placeholder.png")
    if (apTex.exists()) {
        apTex.copyTo(itemTexOut.resolve("adaptive_pattern.png"), overwrite = true)
        apTex.copyTo(itemTexOut.resolve("machine_pattern.png"), overwrite = true)
    }

    // Packet item textures: copy content icons + overlay
    for (tex in listOf(
        "packet_overlay", "energy_icon", "mana_icon", "fluid_icon", "item_icon", "hp_icon", "sta_icon", "xp_icon"
    )) {
        val src = sourceTextures.resolve("packet/$tex.png")
        require(src.exists())
        src.copyTo(itemTexOut.resolve("$tex.png"), overwrite = true)
    }

    // Async synthesis blocks: dedicated pixel-art textures (unformed + formed). Written straight to
    // textures/block/async/, the same paths referenced by both the static cube_all models (no-GT)
    // and GTRegistrate's gtceu:machine models (with-GT), so the two definition files share them.
    // Blocks with hand-drawn textures (frame + wall/glass/tower/cores/interface) are copied above
    // and skipped here; only the remaining GT-machine blocks stay procedural.
    val handDrawnAsyncBlocks = setOf(
        "async_machine_frame", "async_machine_block", "async_machine_glass",
        "singularity_alloy_reinforced_tower", "async_energy_core", "async_computing_core",
        "async_storage_core", "async_execution_core", "async_module_interface",
    )
    AsyncTextures.generate(
        asyncStructureBlocks.filter { it.id !in handDrawnAsyncBlocks },
        output.resolve("textures/block/async"),
    )

    // GT AE power hatch front overlays in an AE purple theme: GT's output arrow hue-shifted to
    // AE cable purple (`overlay_energy_{n}a_ae(_emissive).png`). The tinted plate and ring stay
    // GT's own textures (the model references gtceu: directly, we never bundle them); the tier
    // colour comes from AEPowerHatchMachine.tintColor(2) at runtime, so no per-tier files.
    AsyncTextures.generateGtAEPowerHatchOverlays(
        sourceTextures.resolve("gt"),
        output.resolve("textures/block/overlay/machine"),
    )

    // GUI textures (machine slot square + molecular_assembler GUI with a baked machine slot)
    val guiTexOut = output.resolve("textures/guis")
    guiTexOut.createDirectories()
    val guiSrc = sourceTextures.resolve("guis")
    guiSrc.resolve("machine_slot.png").copyTo(guiTexOut.resolve("machine_slot.png"), overwrite = true)
    guiSrc.resolve("molecular_assembler.png").copyTo(guiTexOut.resolve("molecular_assembler.png"), overwrite = true)
    guiSrc.resolve("async_crafting_status.png").copyTo(guiTexOut.resolve("async_crafting_status.png"), overwrite = true)
    guiSrc.toFile().listFiles()?.filter { it.name.startsWith("sort_") && it.extension.equals("png", ignoreCase = true) }
        ?.forEach { copyAlphaMaskIcon(it.toPath(), guiTexOut.resolve(it.name)) }
}

private fun copyAlphaMaskIcon(src: Path, dest: Path) {
    val img = ImageIO.read(src.toFile())
    val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until img.height) {
        for (x in 0 until img.width) {
            val a = (img.getRGB(x, y) ushr 24) and 0xFF
            out.setRGB(x, y, (a shl 24) or 0x00FFFFFF)
        }
    }
    ImageIO.write(out, "png", dest.toFile())
}
