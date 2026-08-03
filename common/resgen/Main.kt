package allyouneed.resgen

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class CellEntry(
    val id: String,
    val displayName: String,
    val color: String,
    val isCreative: Boolean = false,
    val isSelfPowered: Boolean = false,
)

private val cells = listOf(
    CellEntry("1k_energy_cell", "Micro Energy Cell", AE2_COLORS[0].hex),
    CellEntry("4k_energy_cell", "Simple Energy Cell", AE2_COLORS[1].hex),
    CellEntry("16k_energy_cell", "Basic Energy Cell", AE2_COLORS[2].hex),
    CellEntry("64k_energy_cell", "Normal Energy Cell", AE2_COLORS[3].hex),
    CellEntry("256k_energy_cell", "Enhanced Energy Cell", AE2_COLORS[4].hex),
    CellEntry("1m_energy_cell", "Advanced Energy Cell", AE2_COLORS[5].hex),
    CellEntry("4m_energy_cell", "Reinforced Energy Cell", AE2_COLORS[6].hex),
    CellEntry("16m_energy_cell", "Dense Energy Cell", AE2_COLORS[7].hex),
    CellEntry("64m_energy_cell", "Hyper Energy Cell", AE2_COLORS[8].hex),
    CellEntry("256m_energy_cell", "Ultra Energy Cell", AE2_COLORS[9].hex),
    CellEntry("1g_energy_cell", "Ultimate Energy Cell", AE2_COLORS[10].hex),
    CellEntry("4g_energy_cell", "Singular Energy Cell", AE2_COLORS[11].hex),
    CellEntry("16g_energy_cell", "Quantum Energy Cell", AE2_COLORS[12].hex),
    CellEntry("64g_energy_cell", "Stellar Energy Cell", AE2_COLORS[13].hex),
    CellEntry("256g_energy_cell", "Cosmic Energy Cell", AE2_COLORS[14].hex),
    CellEntry("1t_energy_cell", "1T Energy Cell", AE2_COLORS[15].hex),
    CellEntry("4t_energy_cell", "4T Energy Cell", AE2_COLORS[16].hex),
    CellEntry("16t_energy_cell", "16T Energy Cell", AE2_COLORS[17].hex),
    CellEntry("64t_energy_cell", "64T Energy Cell", AE2_COLORS[18].hex),
    CellEntry("256t_energy_cell", "256T Energy Cell", AE2_COLORS[19].hex),
    // Self-powered: same tier colors + energy_cell_self_powered overlay
    CellEntry("1k_self_powered_energy_cell", "1K Self-Powered Energy Cell", AE2_COLORS[0].hex, isSelfPowered = true),
    CellEntry("4k_self_powered_energy_cell", "4K Self-Powered Energy Cell", AE2_COLORS[1].hex, isSelfPowered = true),
    CellEntry("16k_self_powered_energy_cell", "16K Self-Powered Energy Cell", AE2_COLORS[2].hex, isSelfPowered = true),
    CellEntry("64k_self_powered_energy_cell", "64K Self-Powered Energy Cell", AE2_COLORS[3].hex, isSelfPowered = true),
    CellEntry("256k_self_powered_energy_cell", "256K Self-Powered Energy Cell", AE2_COLORS[4].hex, isSelfPowered = true),
    CellEntry("1m_self_powered_energy_cell", "1M Self-Powered Energy Cell", AE2_COLORS[5].hex, isSelfPowered = true),
    CellEntry("4m_self_powered_energy_cell", "4M Self-Powered Energy Cell", AE2_COLORS[6].hex, isSelfPowered = true),
    CellEntry("16m_self_powered_energy_cell", "16M Self-Powered Energy Cell", AE2_COLORS[7].hex, isSelfPowered = true),
    CellEntry("64m_self_powered_energy_cell", "64M Self-Powered Energy Cell", AE2_COLORS[8].hex, isSelfPowered = true),
    CellEntry("256m_self_powered_energy_cell", "256M Self-Powered Energy Cell", AE2_COLORS[9].hex, isSelfPowered = true),
    CellEntry("1g_self_powered_energy_cell", "1G Self-Powered Energy Cell", AE2_COLORS[10].hex, isSelfPowered = true),
    CellEntry("4g_self_powered_energy_cell", "4G Self-Powered Energy Cell", AE2_COLORS[11].hex, isSelfPowered = true),
    CellEntry("16g_self_powered_energy_cell", "16G Self-Powered Energy Cell", AE2_COLORS[12].hex, isSelfPowered = true),
    CellEntry("64g_self_powered_energy_cell", "64G Self-Powered Energy Cell", AE2_COLORS[13].hex, isSelfPowered = true),
    CellEntry("256g_self_powered_energy_cell", "256G Self-Powered Energy Cell", AE2_COLORS[14].hex, isSelfPowered = true),
    CellEntry("1t_self_powered_energy_cell", "1T Self-Powered Energy Cell", AE2_COLORS[15].hex, isSelfPowered = true),
    CellEntry("4t_self_powered_energy_cell", "4T Self-Powered Energy Cell", AE2_COLORS[16].hex, isSelfPowered = true),
    CellEntry("16t_self_powered_energy_cell", "16T Self-Powered Energy Cell", AE2_COLORS[17].hex, isSelfPowered = true),
    CellEntry("64t_self_powered_energy_cell", "64T Self-Powered Energy Cell", AE2_COLORS[18].hex, isSelfPowered = true),
    CellEntry("256t_self_powered_energy_cell", "256T Self-Powered Energy Cell", AE2_COLORS[19].hex, isSelfPowered = true),
    CellEntry("creative_energy_cell", "Creative Energy Cell", "#E040FB", isCreative = true),
)

private val craftingStorages = listOf(
    CellEntry("1k_crafting_storage", "1K Crafting Storage", AE2_COLORS[0].hex),
    CellEntry("4k_crafting_storage", "4K Crafting Storage", AE2_COLORS[1].hex),
    CellEntry("16k_crafting_storage", "16K Crafting Storage", AE2_COLORS[2].hex),
    CellEntry("64k_crafting_storage", "64K Crafting Storage", AE2_COLORS[3].hex),
    CellEntry("256k_crafting_storage", "256K Crafting Storage", AE2_COLORS[4].hex),
    CellEntry("1m_crafting_storage", "1M Crafting Storage", AE2_COLORS[5].hex),
    CellEntry("4m_crafting_storage", "4M Crafting Storage", AE2_COLORS[6].hex),
    CellEntry("16m_crafting_storage", "16M Crafting Storage", AE2_COLORS[7].hex),
    CellEntry("64m_crafting_storage", "64M Crafting Storage", AE2_COLORS[8].hex),
    CellEntry("256m_crafting_storage", "256M Crafting Storage", AE2_COLORS[9].hex),
    CellEntry("1g_crafting_storage", "1G Crafting Storage", AE2_COLORS[10].hex),
    CellEntry("4g_crafting_storage", "4G Crafting Storage", AE2_COLORS[11].hex),
    CellEntry("16g_crafting_storage", "16G Crafting Storage", AE2_COLORS[12].hex),
    CellEntry("64g_crafting_storage", "64G Crafting Storage", AE2_COLORS[13].hex),
    CellEntry("256g_crafting_storage", "256G Crafting Storage", AE2_COLORS[14].hex),
    CellEntry("1t_crafting_storage", "1T Crafting Storage", AE2_COLORS[15].hex),
    CellEntry("4t_crafting_storage", "4T Crafting Storage", AE2_COLORS[16].hex),
    CellEntry("16t_crafting_storage", "16T Crafting Storage", AE2_COLORS[17].hex),
    CellEntry("64t_crafting_storage", "64T Crafting Storage", AE2_COLORS[18].hex),
    CellEntry("256t_crafting_storage", "256T Crafting Storage", AE2_COLORS[19].hex),
    CellEntry("creative_crafting_storage", "Creative Crafting Storage", "#E040FB", isCreative = true),
)

// Item storage cell tiers, colored per AE2_COLORS like the other storage tiers.
private val itemStorageCells = run {
    val tiers = listOf(
        "1k", "4k", "16k", "64k", "256k",
        "1m", "4m", "16m", "64m", "256m",
        "1g", "4g", "16g", "64g", "256g",
        "1t", "4t", "16t", "64t", "256t",
    )
    tiers.mapIndexed { i, tier ->
        CellEntry("${tier}_item_storage_cell", "${tier.uppercase()} Item Storage Cell", AE2_COLORS[i].hex)
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
 * Reads one of the two async block definition files from common/resgen/definitions/.
 */
private fun loadAsyncDefinitions(fileName: String): List<AsyncBlockDef> {
    val path = Path.of("common/resgen/definitions", fileName)
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

private fun loadAsyncBlockSet(): List<AsyncBlockDef> {
    val gt = loadAsyncDefinitions("async_blocks_gt.json")
    val vanilla = loadAsyncDefinitions("async_blocks_vanilla.json")

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
    println("[async] ${gt.size} blocks, ${gtCount} GT-owned, ${gt.size - gtCount} plain")

    // Shared field set drives generation; isGt is runtime metadata only.
    return vanilla
}

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        println("Arguments: ${args.joinToString()}")
        println("Error: No arguments are expected, as this is a simple asset generator.")
        return
    }

    val modId = "ae2isallyouneed"
    val output = Path.of("common/res").resolve("assets/$modId")
    val sourceTextures = Path.of("common/resgen/textures")
    val langDir = Path.of("common/resgen/lang")

    val asyncStructureBlocks = loadAsyncBlockSet()

    assetGen(modId, output, langDir) {
        translation("itemGroup.$modId", "AE2 Is All You Need")
        translation("gui.$modId.group.all", "ALL")
        translation("gui.$modId.group.ae2", "AE2")
        translation("gui.$modId.adaptive_probability", "Probability (p)")
        translation("gui.$modId.adaptive_timeout", "Timeout (T)")
        translation("gui.$modId.machine_assembler", "Molecular Assembler")
        translation("gui.$modId.machine_pattern_terminal", "Machine Pattern Terminal")
        translation("gui.$modId.machine_slot", "Machine")
        translation("gui.$modId.machine_slot_no_machine", "No machine selected")
        translation("gui.$modId.machine_slot_hint", "Click to change machine")

        // Async processing status GUI
        translation("gui.$modId.async.status.title", "Async Processing Status")
        translation("gui.$modId.async.status.formed", "Formed")
        translation("gui.$modId.async.status.unformed", "Not formed")
        translation("gui.$modId.async.status.connected", "Grid connected")
        translation("gui.$modId.async.status.disconnected", "No grid connection")
        translation("gui.$modId.async.status.swallowed", "Channels swallowed: %s")
        translation("gui.$modId.async.status.swallowed_infinite", "Channels swallowed: Infinite")
        translation("gui.$modId.async.status.storage", "Storage: %s MB")
        translation("gui.$modId.async.status.block_count", "Blocks: %s")
        translation("gui.$modId.async.status.working", "Working")
        translation("gui.$modId.async.status.not_working", "Not working")

        for (cell in cells) {
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
            if (async.role == "FRAME") {
                asyncFrameBlock(async.id, async.displayName)
            } else {
                asyncBlock(async.id, async.displayName, async.hasFacing, async.hasPowered)
            }
        }

        simpleBlock("me_io_drive", "ME IO Drive")

        // Adaptive Pattern item (just an item model, no block)
        item("adaptive_pattern", "Adaptive Pattern")

        // Item storage cells: LED item model + drive-cell block model
        for (cell in itemStorageCells) {
            cellItem(cell.id, cell.displayName)
            driveCellModel(cell.id.removeSuffix("_item_storage_cell") + "_item_cell")
        }

        translation("item.$modId.creative_me_cell", "Creative ME Storage Cell")
        translation("item.$modId.dimensional_cell", "Dimensional Storage Cell")

        // Adaptive Pattern Terminal block
        simpleBlock("adaptive_pattern_terminal", "Adaptive Pattern Terminal")

        // Machine Assembler: reuse AE2's molecular assembler shell model/texture
        parentedBlock("molecular_assembler", "Molecular Assembler", "ae2:block/molecular_assembler")

        // Machine Pattern Terminal block
        simpleBlock("machine_pattern_terminal", "Machine Pattern Terminal")

        // Machine Pattern item (just an item model, no block)
        item("machine_pattern", "Machine Pattern")
    }

    retexture(output) {
        // FG template base hue (dominant opaque pixel of energy_cell_fg.png ≈ rgb 152,194,231)
        source(sourceTextures, "#98C2E7")

        // Energy cells: bg + fg(tint) + fullness/creative + optional self-powered badge
        for (cell in cells) {
            val badge = if (cell.isSelfPowered) listOf("energy_cell_self_powered") else emptyList()
            if (cell.isCreative) {
                // AE2-style vertical strip: fg cycles through AE2_GRADIENT with interpolation
                layeredAnimated(
                    bg = "energy_cell_bg",
                    mid = "energy_cell_fg",
                    top = "energy_cell_creative",
                    outputPrefix = cell.id,
                    midColors = AE2_GRADIENT.map { it.hex },
                    frameTime = 4,
                    interpolate = true,
                    overlays = badge,
                )
            } else {
                layeredTarget(
                    bg = "energy_cell_bg",
                    mid = "energy_cell_fg",
                    top = "energy_cell",
                    outputPrefix = cell.id,
                    color = cell.color,
                    levels = 0..4,
                    overlays = badge,
                )
            }
        }

        // Item storage cell FG base hue (dominant opaque ≈ rgb 154,130,255)
        source(sourceTextures, "#9A82FF")

        // Item storage cells: bg (no tint) + fg (tint per tier), output to textures/item
        for (cell in itemStorageCells) {
            layeredTarget(
                bg = "item_storage_cell_bg",
                mid = "item_storage_cell_fg",
                top = null,
                outputPrefix = cell.id,
                color = cell.color,
                levels = null,
                dir = "item",
            )
        }

        // Drive cell faces: bg (opaque plate) + fg (tint per tier). The drive_cell model
        // samples only rows 0-6 / cols 0-6, so these templates are designed for that region.
        for (cell in itemStorageCells) {
            val driveId = cell.id.removeSuffix("_item_storage_cell") + "_item_cell"
            layeredTarget(
                bg = "drive_item_cell_bg",
                mid = "drive_item_cell_fg",
                top = null,
                outputPrefix = "drive/cells/$driveId",
                color = cell.color,
                levels = null,
            )
        }

        // Crafting storage FG base hue (dominant opaque ≈ rgb 235,142,75)
        source(sourceTextures, "#EB8E4B")

        // Crafting storage unformed: bg (no tint) + fg (tint); light still flat recolor
        for (storage in craftingStorages) {
            if (storage.isCreative) {
                layeredTarget(
                    bg = "crafting_storage_bg",
                    mid = "crafting_storage_fg",
                    top = null,
                    outputPrefix = storage.id,
                    color = null,
                    levels = null,
                )
            } else {
                layeredTarget(
                    bg = "crafting_storage_bg",
                    mid = "crafting_storage_fg",
                    top = null,
                    outputPrefix = storage.id,
                    color = storage.color,
                    levels = null,
                )
            }
            targetSingle("crafting_storage_light", "crafting/${storage.id}_light", storage.color)
        }

        // Async machine frame: animated formed-state glow strips. The base frame_* textures are
        // copied to the output below; here each light overlay (white strip, alpha 55/133) is tinted
        // flat through AE2_GRADIENT and stacked into a vertical animation strip per face variant.
        for (variant in listOf("c", "h", "v")) {
            layeredAnimatedTint(
                bg = "async/frame_$variant",
                mid = "async/frame_light_$variant",
                outputPrefix = "async/frame_${variant}_formed",
                midColors = AE2_GRADIENT.map { it.hex },
                frameTime = 4,
                interpolate = true,
            )
        }

        // Async structure blocks: dedicated pixel-art textures (AsyncTextures) are generated after
        // the retexture block below, shared by both the GT and the no-GT definition files.
    }

    val texOut = output.resolve("textures/block")
    texOut.createDirectories()
    sourceTextures.resolve("me_io_drive.png").copyTo(texOut.resolve("me_io_drive.png"), overwrite = true)

    // Async machine frame: base connection textures (unformed faces) referenced by the generated
    // models. The `_formed` variants are the animated strips produced by the retexture block above.
    val asyncTexOut = texOut.resolve("async")
    asyncTexOut.createDirectories()
    for (variant in listOf("c", "h", "v")) {
        sourceTextures.resolve("async/frame_$variant.png")
            .copyTo(asyncTexOut.resolve("frame_$variant.png"), overwrite = true)
    }

    val craftingTexOut = texOut.resolve("crafting")
    craftingTexOut.createDirectories()

    // Status-LED item layer: single-pixel dot, tinted at runtime by ItemStorageCellItem.getColor.
    val itemTexOut = output.resolve("textures/item")
    itemTexOut.createDirectories()
    sourceTextures.resolve("item_storage_cell_light.png")
        .copyTo(itemTexOut.resolve("item_storage_cell_light.png"), overwrite = true)

    // Light overlays live under block/crafting/; ensure atlas + dummy model stitch them
    // (vanilla already scans textures/block/, but keep explicit for clarity).
    val atlasDir = Path.of("common/res").resolve("assets/minecraft/atlases")
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

    val lightMcmeta = sourceTextures.resolve("crafting_storage_light.png.mcmeta")
    if (lightMcmeta.exists()) {
        for (storage in craftingStorages) {
            lightMcmeta.copyTo(craftingTexOut.resolve("${storage.id}_light.png.mcmeta"), overwrite = true)
        }
    }

    // Adaptive pattern terminal texture (placeholder - copy from pattern provider if available)
    val ptTex = sourceTextures.resolve("me_io_drive.png")
    if (ptTex.exists()) {
        ptTex.copyTo(texOut.resolve("adaptive_pattern_terminal.png"), overwrite = true)
    }

    // Adaptive pattern item texture (placeholder)
    val apTex = sourceTextures.resolve("me_io_drive.png")
    if (apTex.exists()) {
        apTex.copyTo(itemTexOut.resolve("adaptive_pattern.png"), overwrite = true)
    }

    // Machine pattern terminal + machine pattern textures (placeholder)
    if (apTex.exists()) {
        apTex.copyTo(texOut.resolve("machine_pattern_terminal.png"), overwrite = true)
        apTex.copyTo(itemTexOut.resolve("machine_pattern.png"), overwrite = true)
    }

    // Async synthesis blocks: dedicated pixel-art textures (unformed + formed). Written straight to
    // textures/block/async/, the same paths referenced by both the static cube_all models (no-GT)
    // and GTRegistrate's gtceu:machine models (with-GT), so the two definition files share them.
    AsyncTextures.generate(asyncStructureBlocks, output.resolve("textures/block/async"))

    // GUI textures (machine slot square + molecular_assembler GUI with a baked machine slot)
    val guiTexOut = output.resolve("textures/guis")
    guiTexOut.createDirectories()
    val guiSrc = Path.of("common/resgen/textures/guis")
    guiSrc.resolve("machine_slot.png").copyTo(guiTexOut.resolve("machine_slot.png"), overwrite = true)
    guiSrc.resolve("molecular_assembler.png")
        .copyTo(guiTexOut.resolve("molecular_assembler.png"), overwrite = true)
    guiSrc.resolve("async_crafting_status.png")
        .copyTo(guiTexOut.resolve("async_crafting_status.png"), overwrite = true)
}
