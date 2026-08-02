package allyouneed.multiblock

import allyouneed.async.AsyncCraftingStructure
import allyouneed.util.logger
import net.minecraft.nbt.NbtIo
import net.minecraft.server.packs.resources.ResourceManager
import java.io.File

/**
 * Holds the currently loaded multiblock structure definitions. Refreshed from the server datapack
 * on resource reload (`/reload`). On reload failure the previous definition is kept.
 *
 * When the dev-only editor has exported a pattern to the override directory, the override file is
 * preferred over the datapack resource so edits take effect immediately.
 */
object MultiblockPatterns {

    @Volatile
    var async: MultiblockPattern = AsyncCraftingStructure.defaultPattern()
        private set

    @Volatile
    var overrideDir: File? = null

    fun reload(manager: ResourceManager) {
        var loaded = overrideDir?.let { dir ->
            val file = File(dir, "async_crafting.nbt")
            if (file.exists()) {
                try {
                    MultiblockPatternLoader.fromNbt(NbtIo.readCompressed(file))
                } catch (e: Exception) {
                    logger.error("Failed to read override pattern {}", file, e)
                    null
                }
            } else {
                null
            }
        }
        if (loaded == null) {
            loaded = MultiblockPatternLoader.loadFromResource(manager, "async_crafting")
        }
        if (loaded != null) {
            async = loaded
            logger.info(
                "Reloaded async crafting multiblock pattern ({}x{}x{}, offset {})",
                loaded.width,
                loaded.height,
                loaded.depth,
                loaded.offset,
            )
        } else {
            logger.warn("Async crafting multiblock pattern reload failed, keeping previous definition")
        }
    }
}
