package allyouneed.resgen

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
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

    assetGen(modId, output, langDir) {
        translation("itemGroup.$modId", "AE2 Is All You Need")
        translation("gui.$modId.group.all", "ALL")
        translation("gui.$modId.group.ae2", "AE2")
        translation("gui.$modId.adaptive_probability", "Probability (p)")
        translation("gui.$modId.adaptive_timeout", "Timeout (T)")

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

        simpleBlock("me_io_drive", "ME IO Drive")

        // Adaptive Pattern item (just an item model, no block)
        item("adaptive_pattern", "Adaptive Pattern")

        translation("item.$modId.creative_me_cell", "Creative ME Storage Cell")
        translation("item.$modId.dimensional_cell", "Dimensional Storage Cell")

        // Adaptive Pattern Terminal block
        simpleBlock("adaptive_pattern_terminal", "Adaptive Pattern Terminal")
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
    }

    val texOut = output.resolve("textures/block")
    texOut.createDirectories()
    sourceTextures.resolve("me_io_drive.png").copyTo(texOut.resolve("me_io_drive.png"), overwrite = true)

    val craftingTexOut = texOut.resolve("crafting")
    craftingTexOut.createDirectories()

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
    val itemTexOut = output.resolve("textures/item")
    itemTexOut.createDirectories()
    val apTex = sourceTextures.resolve("me_io_drive.png")
    if (apTex.exists()) {
        apTex.copyTo(itemTexOut.resolve("adaptive_pattern.png"), overwrite = true)
    }
}
