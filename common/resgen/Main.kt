package allyouneed.resgen

import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        println("Arguments: ${args.joinToString()}")
        println("Error: No arguments are expected, as this is a simple asset generator.")
        return
    }

    val modId = "ae2isallyouneed"
    val output = Path.of("common/res").resolve("assets/$modId")
    val sourceTextures = Path.of("common/resgen/textures")

    assetGen(modId, output) {
        cubeAllWithFullness("simple_energy_cell")
        cubeAllWithFullness("normal_energy_cell")
        cubeAllWithFullness("advanced_energy_cell")
        cubeAllWithFullness("dense_energy_cell")
        simpleBlock("creative_energy_cell")
    }

    retexture(output) {
        source(sourceTextures, "#00A2E8")

        target("energy_cell", "simple_energy_cell", "#00A2E8")
        target("energy_cell", "normal_energy_cell", "#39FF14")
        target("energy_cell", "advanced_energy_cell", "#FF1744")
        target("energy_cell", "dense_energy_cell", "#FFD600")
        targetSingle("creative_energy_cell", "creative_energy_cell", "#E040FB")
    }
}
