package allyouneed.resgen

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

data class CellEntry(
    val id: String,
    val displayName: String,
    val color: String,
    val isCreative: Boolean = false,
)

private val cells = listOf(
    CellEntry("1k_energy_cell", "Micro Energy Cell", "#B0BEC5"),
    CellEntry("4k_energy_cell", "Simple Energy Cell", "#00A2E8"),
    CellEntry("16k_energy_cell", "Basic Energy Cell", "#40C4FF"),
    CellEntry("64k_energy_cell", "Normal Energy Cell", "#39FF14"),
    CellEntry("256k_energy_cell", "Enhanced Energy Cell", "#76FF03"),
    CellEntry("1m_energy_cell", "Advanced Energy Cell", "#FF1744"),
    CellEntry("4m_energy_cell", "Reinforced Energy Cell", "#FF9100"),
    CellEntry("16m_energy_cell", "Dense Energy Cell", "#FFD600"),
    CellEntry("64m_energy_cell", "Hyper Energy Cell", "#FFEA00"),
    CellEntry("256m_energy_cell", "Ultra Energy Cell", "#FF6D00"),
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

        for (cell in cells) {
            if (cell.isCreative) {
                simpleBlock(cell.id, cell.displayName)
            } else {
                cubeAllWithFullness(cell.id, cell.displayName)
            }
        }

        simpleBlock("me_io_drive", "ME IO Drive")
    }

    retexture(output) {
        source(sourceTextures, "#00A2E8")

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
}
