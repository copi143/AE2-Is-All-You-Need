package allyouneed.resgen

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

data class CellEntry(
    val id: String,
    val displayName: String,
    val color: String,
    val isCreative: Boolean = false,
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
    CellEntry("1g_energy_cell", "Ultimate Energy Cell", "#FF3D00"),
    CellEntry("4g_energy_cell", "Singular Energy Cell", "#D500F9"),
    CellEntry("16g_energy_cell", "Quantum Energy Cell", "#AA00FF"),
    CellEntry("64g_energy_cell", "Stellar Energy Cell", "#651FFF"),
    CellEntry("256g_energy_cell", "Cosmic Energy Cell", "#304FFE"),
    CellEntry("creative_energy_cell", "Creative Energy Cell", "#E040FB", isCreative = true),
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

        simpleBlock("me_io_drive", "ME IO Drive")

        // Adaptive Pattern item (just an item model, no block)
        item("adaptive_pattern", "Adaptive Pattern")

        translation("item.$modId.creative_me_cell", "Creative ME Storage Cell")
        translation("item.$modId.dimensional_cell", "Dimensional Storage Cell")

        // Adaptive Pattern Terminal block
        simpleBlock("adaptive_pattern_terminal", "Adaptive Pattern Terminal")
    }

    retexture(output) {
        source(sourceTextures, "#9fc3e4")

        val levelCells = cells.filter { !it.isCreative }
        for (cell in levelCells) {
            target("energy_cell", cell.id, cell.color)
        }

        val creative = cells.first { it.isCreative }
        targetSingle("creative_energy_cell", creative.id, creative.color)
    }

    val texOut = output.resolve("textures/block")
    texOut.createDirectories()
    sourceTextures.resolve("me_io_drive.png").copyTo(texOut.resolve("me_io_drive.png"), overwrite = true)

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
