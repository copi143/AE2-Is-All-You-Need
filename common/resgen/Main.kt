package allyouneed.resgen

import com.google.gson.JsonParser
import minecraftx.compose.itemdetail.ItemDetailsKeyBind
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/** Id form of a display string (lowercase snake), used for registry names. */
fun idify(value: String): String =
    value.lowercase().replace(" ", "_").replace("-", "_").replace(".", "_")

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

val tiers = listOf(
    "1k", "4k", "16k", "64k", "256k",
    "1m", "4m", "16m", "64m", "256m",
    "1g", "4g", "16g", "64g", "256g",
    "1t", "4t", "16t", "64t", "256t",
).map { it.uppercase() }

val gtMultiBlockTiers = listOf(
    "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX",
)

val gtSingleBlockTiers = listOf(
    "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV",
)

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

private val itemStorageCells = storageCells("Item")
private val fluidStorageCells = storageCells("Fluid")
private val manaStorageCells = storageCells("Mana")
private val energyStorageCells = storageCells("Energy")
private val hpStorageCells = storageCells("HP")
private val staStorageCells = storageCells("STA")
private val xpStorageCells = storageCells("XP")

/** All storage cell groups by key type id; drives bg derivation, textures and models. */
val storageCellGroups = linkedMapOf(
    "item" to itemStorageCells,
    "fluid" to fluidStorageCells,
    "mana" to manaStorageCells,
    "energy" to energyStorageCells,
    "hp" to hpStorageCells,
    "sta" to staStorageCells,
    "xp" to xpStorageCells,
)

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
    println("[async] ${gt.size} blocks, $gtCount GT-owned, ${gt.size - gtCount} plain")

    // Shared field set drives generation; isGt is runtime metadata only.
    return vanilla
}

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        println("Arguments: ${args.joinToString(" ")}")
        println("Error: No arguments are expected, as this is a simple asset generator.")
        return
    }

    val modId = "ae2isallyouneed"
    val output = Path.of("common/res").resolve("assets/$modId")
    val dataOutput = Path.of("common/res").resolve("data/$modId")
    val sourceTextures = Path.of("common/resgen/textures")
    val langDir = Path.of("common/resgen/lang")

    val asyncStructureBlocks = loadAsyncBlockSet()

    assetGen(modId, output, langDir, dataOutput) {
        // Machine assembler: recipe-category → accepted machine items (optional mods ignored)
        machineItemTags()

        translation("itemGroup.$modId", "AE2 Is All You Need")
        translation(ItemDetailsKeyBind.CATEGORY_ID, "AE2 Is All You Need")
        translation(ItemDetailsKeyBind.KEY_ID, "Open Item Details")
        gui("group.all", "ALL")
        gui("group.ae2", "AE2")
        gui("adaptive_probability", "Probability (p)")
        gui("adaptive_timeout", "Timeout (T)")
        gui("machine_assembler", "Molecular Assembler")
        gui("pattern_encoding_terminal", "ME Pattern Encoding Terminal")
        gui("encoding.machine", "Machine Pattern")
        gui("encoding.processing", "Processing Pattern")
        gui("encoding.probability", "Probability Pattern")
        gui("encoding.pseudo", "Pseudo Pattern")
        gui("encode_failed", "Cannot encode pattern")
        gui("machine_slot", "Machine")
        gui("machine_slot_no_machine", "No machine selected")
        gui("machine_slot_hint", "Click to change machine")

        // Async processing status GUI
        gui("async.status.title", "Async Processing Status")
        gui("async.status.formed", "Formed")
        gui("async.status.unformed", "Not formed")
        gui("async.status.connected", "Grid connected")
        gui("async.status.disconnected", "No grid connection")
        gui("async.status.swallowed", "Channels swallowed: %s")
        gui("async.status.swallowed_infinite", "Channels swallowed: Infinite")
        gui("async.status.storage", "Storage: %s MB")
        gui("async.status.block_count", "Blocks: %s")
        gui("async.status.working", "Working")
        gui("async.status.not_working", "Not working")

        gui("mac", "MAC: %s")
        gui("mac_named", "MAC (%s): %s")
        gui("mac_item", "MAC: %s")
        translation("config.jade.plugin_$modId.mac", "MAC Address")
        translation(
            "tooltip.$modId.plane_bus.members",
            "Annihilation planes: %s | Formation planes: %s",
        )
        translation("tooltip.$modId.plane_bus.unformed", "Structure not formed")
        translation("tooltip.$modId.plane_bus.buses", "Interconnected plane buses: %s")

        gui("machine.crafting", "Crafting")
        gui("machine.smelting", "Smelting")
        gui("machine.blasting", "Blasting")
        gui("machine.smoking", "Smoking")
        gui("machine.example_custom", "Example Custom")

        // Mana key display names (MetricLevelKey: gui.<modid>.<type>.<metric>)
        gui("mana.ae2", "AM")
        gui("mana.botania", "Botania Mana")
        gui("mana.bloodmagic", "Blood Magic LP")
        gui("mana.ars_nouveau", "Ars Nouveau Mana")

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
                    faces = listOf("tower", "tower_h", "tower", "tower_h", "tower_v", "tower_v"),
                )
                // Module interface: the socket face (with pin holes) points in the facing direction.
                "INTERFACE" -> asyncBlock(
                    async.id, async.displayName, async.hasFacing, async.hasPowered,
                    faces = listOf("socket_up", "socket", "socket", "socket", "socket", "socket"),
                    formedFaces = listOf("socket_up_formed", "socket", "socket", "socket", "socket", "socket"),
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

        gui("log.title", "ME Network Logger")
        gui("log.cat.topology", "Topology")
        gui("log.cat.device", "Devices")
        gui("log.cat.energy", "Energy")
        gui("log.cat.crafting", "Crafting")
        gui("log.status.online", "Online · %s entries")
        gui("log.status.offline", "Offline · %s entries")
        gui("log.status.conflict", "Conflict · %s entries")
        gui("log.conflict_banner", "Multiple loggers on this network; all recording is stopped.")
        gui("log.page", "%s–%s / %s")
        gui("log.clear", "Clear")
        gui("log.download", "Download")
        gui("log.downloaded", "Saved logs to %s")
        gui("log.download_failed", "Failed to save logs: %s")
        gui("log.boot_start", "Network boot started")
        gui("log.boot_end", "Network boot finished")
        gui("log.controller_online", "Controller online")
        gui("log.controller_none", "No controller")
        gui("log.controller_conflict", "Controller conflict")
        gui("log.channel_req", "Channel requirement changed: %s @ %s")
        gui("log.node_added", "Device joined: %s @ %s")
        gui("log.node_removed", "Device left: %s @ %s")
        gui("log.node_power_on", "Device powered: %s @ %s")
        gui("log.node_power_off", "Device unpowered: %s @ %s")
        gui("log.node_channel_on", "Device got channel: %s @ %s")
        gui("log.node_channel_off", "Device lost channel: %s @ %s")
        gui("log.power_on", "Network powered")
        gui("log.power_off", "Network lost power")
        gui("log.craft_submit_ok", "Crafting submitted: %s")
        gui("log.craft_submit_fail", "Crafting submit failed: %s (%s)")
        gui("log.craft_start", "Crafting started: %s")
        gui("log.craft_done", "Crafting finished: %s")
        gui("log.craft_cancel", "Crafting cancelled: %s")
        gui("log.cpu_change", "Crafting CPU changed: %s @ %s")
        gui("log.logger_conflict", "Logger conflict (%s devices)")
        gui("log.logger_ok", "Logger conflict cleared")
        gui("log.unknown", "Unknown event")

        // Adaptive Pattern item (just an item model, no block)
        item("adaptive_pattern", "Adaptive Pattern")

        // Storage cells: LED item model + drive-cell block model pipeline, one group per key type
        for ((_, cells) in storageCellGroups) {
            for (cell in cells) {
                cellItem(cell.id, cell.displayName)
                driveCellModel(cell.itemCellId)
            }
        }

        itemLang("creative_me_cell", "Creative ME Storage Cell")
        itemLang("dimensional_cell", "Dimensional Storage Cell")

        // Machine Assembler: reuse AE2's molecular assembler shell model/texture
        parentedBlock("molecular_assembler", "Molecular Assembler", "ae2:block/molecular_assembler")

        // Machine Pattern item (just an item model, no block)
        item("machine_pattern", "Machine Pattern")
        itemLang("pattern_encoding_terminal", "ME Pattern Encoding Terminal")
        itemLang("pseudo_pattern", "Pseudo Pattern")
        itemLang("plane_bus", "ME Annihilation/Formation Plane Bus")

        // Packet items: layered model (layer0=content icon, layer1=packet overlay)
        packetItem("e_packet", "Energy Packet", "item/energy_icon")
        packetItem("m_packet", "Mana Packet", "item/mana_icon")
        packetItem("f_packet", "Fluid Packet", "item/fluid_icon")
        packetItem("i_packet", "Item Packet", "item/item_icon")
        packetItem("hp_packet", "HP Packet", "item/hp_icon")
        packetItem("sta_packet", "STA Packet", "item/sta_icon")
        packetItem("xp_packet", "XP Packet", "item/xp_icon")

        translation("packet.$modId.type.energy", "Energy")
        translation("packet.$modId.type.mana", "Mana")
        translation("packet.$modId.type.fluid", "Fluid")
        translation("packet.$modId.type.item", "Item")
        translation("packet.$modId.type.hp", "HP")
        translation("packet.$modId.type.sta", "STA")
        translation("packet.$modId.type.xp", "XP")
    }

    retexture(output) {
        val gradientHex = AE2_GRADIENT.map { it.hex }

        // FG template base hue (dominant opaque pixel of energy_cell/energy_cell_fg.png ≈ rgb 152,194,231)
        source(sourceTextures, "#98C2E7")

        // Energy cells: bg + fg(tint) + fullness/creative + optional self-powered badge
        for (cell in energyCells) {
            val badge = if (cell.isSelfPowered) listOf("energy_cell/energy_cell_self_powered") else emptyList()
            if (cell.isCreative) {
                // AE2-style vertical strip: fg cycles through AE2_GRADIENT with interpolation
                layeredAnimated(
                    bg = "energy_cell/energy_cell_bg",
                    mid = "energy_cell/energy_cell_fg",
                    top = "energy_cell/energy_cell_creative",
                    outputPrefix = cell.id,
                    midColors = gradientHex,
                    frameTime = 4,
                    interpolate = true,
                    overlays = badge,
                )
            } else {
                layeredTarget(
                    bg = "energy_cell/energy_cell_bg",
                    mid = "energy_cell/energy_cell_fg",
                    top = "energy_cell/energy_cell",
                    outputPrefix = cell.id,
                    color = cell.color,
                    levels = 0..4,
                    overlays = badge,
                )
            }
        }

        // Non-item cell backgrounds: derived from the item-cell template (unified style),
        // retinted from the item drive-cell theme to each type's drive-cell theme. The theme
        // references are the tinted drive plates; the near-neutral item bg itself would be an
        // unstable chroma source. Written back under resgen/textures.
        for (type in storageCellGroups.keys - "item") {
            deriveTemplate(
                srcDir = sourceTextures.resolve("storage_cell"),
                source = "item_storage_cell_bg",
                themeFrom = "drive_item_cell_bg",
                themeTo = "drive_${type}_cell_bg",
                output = "${type}_storage_cell_bg",
            )
        }

        // Item storage cell FG base hue (dominant opaque ≈ rgb 154,130,255)
        source(sourceTextures, "#9A82FF")

        // Storage cells: bg (type-tinted, no tint applied here) + fg (tint per tier), output to textures/item
        for ((type, cells) in storageCellGroups) {
            for (cell in cells) {
                layeredTarget(
                    bg = "storage_cell/${type}_storage_cell_bg",
                    mid = "storage_cell/storage_cell_fg",
                    top = null,
                    outputPrefix = cell.id,
                    color = cell.color,
                    levels = null,
                    dir = "item",
                )
            }
        }

        // Drive cell faces: bg (opaque plate, per type) + fg (tint per tier). The drive_cell model
        // samples only rows 0-6 / cols 0-6, so these templates are designed for that region.
        for ((type, cells) in storageCellGroups) {
            for (cell in cells) {
                layeredTarget(
                    bg = "storage_cell/drive_${type}_cell_bg",
                    mid = "storage_cell/drive_cell_fg",
                    top = null,
                    outputPrefix = "drive/cells/${cell.itemCellId}",
                    color = cell.color,
                    levels = null,
                )
            }
        }

        // Crafting storage FG base hue (dominant opaque ≈ rgb 235,142,75)
        source(sourceTextures, "#EB8E4B")

        // Crafting storage unformed: bg (no tint) + fg (tint); light still flat recolor
        for (storage in craftingStorages) {
            layeredTarget(
                bg = "crafting_storage/crafting_storage_bg",
                mid = "crafting_storage/crafting_storage_fg",
                top = null,
                outputPrefix = storage.id,
                color = storage.color,
                levels = null,
            )
            targetSingle("crafting_storage/crafting_storage_light", "crafting/${storage.id}_light", storage.color)
        }

        // Async machine frame: animated formed-state glow strips. The base frame_* textures are
        // copied to the output below; here each light overlay (white strip, alpha 55/133) is tinted
        // flat through AE2_GRADIENT and stacked into a vertical animation strip per face variant.
        for (variant in listOf("c", "h", "v")) {
            layeredAnimatedTint(
                bg = "async/frame_$variant",
                mid = "async/frame_light_$variant",
                outputPrefix = "async/frame_${variant}_formed",
                midColors = gradientHex,
                frameTime = 4,
                interpolate = true,
            )
        }

        // Async cores: the hand-drawn `*_formed_light` overlays are the formed-state glow mask,
        // animated exactly like the frame lights above. The static `*_formed.png` files in the
        // source are just design previews; the output `_formed` strips are generated from the
        // base + light overlay and written under the block-id names the models reference.
        for (core in listOf("storage_core", "execution_core")) {
            layeredAnimatedTint(
                bg = "async/$core",
                mid = "async/${core}_formed_light",
                outputPrefix = "async/async_${core}_formed",
                midColors = gradientHex,
                frameTime = 4,
                interpolate = true,
            )
        }

        // Async structure blocks: dedicated pixel-art textures (AsyncTextures) are generated after
        // the retexture block below, shared by both the GT and the no-GT definition files.
    }

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

    // Status-LED item layer: single-pixel dot, tinted at runtime by ItemStorageCellItem.getColor.
    val itemTexOut = output.resolve("textures/item")
    itemTexOut.createDirectories()
    sourceTextures.resolve("storage_cell/storage_cell_light.png")
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
    for (tex in listOf("packet_overlay", "energy_icon", "mana_icon", "fluid_icon", "item_icon", "hp_icon", "sta_icon", "xp_icon")) {
        val src = sourceTextures.resolve("packet/$tex.png")
        if (src.exists()) {
            src.copyTo(itemTexOut.resolve("$tex.png"), overwrite = true)
        }
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
    val guiSrc = Path.of("common/resgen/textures/guis")
    guiSrc.resolve("machine_slot.png").copyTo(guiTexOut.resolve("machine_slot.png"), overwrite = true)
    guiSrc.resolve("molecular_assembler.png").copyTo(guiTexOut.resolve("molecular_assembler.png"), overwrite = true)
    guiSrc.resolve("async_crafting_status.png").copyTo(guiTexOut.resolve("async_crafting_status.png"), overwrite = true)
}
