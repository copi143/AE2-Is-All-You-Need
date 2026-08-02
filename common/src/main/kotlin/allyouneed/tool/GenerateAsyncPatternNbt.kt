package allyouneed.tool

import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import java.io.File

/**
 * Generates the default async crafting multiblock NBT pattern matching the built-in fallback
 * structure in `AsyncCraftingStructure`. Run via the `generateAsyncPatternNbt` Gradle task.
 */
object GenerateAsyncPatternNbt {

    @JvmStatic
    fun main(args: Array<String>) {
        val target = File(args[0])

        val blocks = ListTag()
        listOf(
            "ae2isallyouneed:async_processing_host",
            "ae2isallyouneed:async_processing_connector",
            "ae2isallyouneed:async_processing_storage",
            "ae2isallyouneed:async_processing_wall",
            "ae2isallyouneed:async_processing_glass",
        ).forEach { id ->
            val t = CompoundTag()
            t.putString("id", id)
            blocks.add(t)
        }

        // Layer strings are ordered bottom -> top, each character is west -> east.
        val layersStr = listOf(
            listOf("FWF", "WCW", "FWF"), // z = 0: back face (connector)
            listOf("WSW", "SSS", "WSW"), // z = 1
            listOf("WSW", "SHS", "WSW"), // z = 2 (host)
            listOf("FWF", "WGW", "FWF"), // z = 3: front face (glass window)
        )
        val idOfChar: (Char) -> Int = { c ->
            when (c) {
                'H' -> 0
                'C' -> 1
                'S' -> 2
                'W', 'F' -> 3
                'G' -> 4
                else -> 3
            }
        }

        val layers = ListTag()
        for (z in layersStr) {
            val zTag = ListTag()
            for (y in z) {
                zTag.add(ByteArrayTag(ByteArray(y.length) { x -> idOfChar(y[x]).toByte() }))
            }
            layers.add(zTag)
        }

        val root = CompoundTag()
        root.putIntArray("offset", intArrayOf(1, 1, 2))
        root.put("blocks", blocks)
        root.put("layers", layers)

        target.parentFile.mkdirs()
        NbtIo.writeCompressed(root, target)
        println("Generated $target")
    }
}
