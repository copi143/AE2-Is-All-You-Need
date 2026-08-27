package allyouneed.transformer

import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import java.util.zip.ZipFile

class SelfModLocator : AbstractJarFileModLocator() {
    private val filesystems = ArrayList<FileSystem>()

    override fun name(): String = "ae2isallyouneed_self"

    override fun initArguments(arguments: Map<String, *>) {}

    override fun scanCandidates(): Stream<Path> {
        val mods = FMLPaths.MODSDIR.get()
        if (!Files.isDirectory(mods)) return Stream.empty()
        val found = ArrayList<Path>()
        try {
            Files.newDirectoryStream(mods, "*.jar").use { stream ->
                for (jar in stream) {
                    nestedGame(jar)?.let { found.add(it) }
                }
            }
        } catch (t: Throwable) {
            logger.error("embedded game jar scan failed", t)
        }
        if (found.isEmpty()) {
            logger.info("no embedded META-INF/mod/game.jar in mods/ (dev exploded run)")
        }
        return found.stream()
    }

    private fun nestedGame(wrapper: Path): Path? {
        if (!Files.isRegularFile(wrapper)) return null
        return try {
            ZipFile(wrapper.toFile()).use { zip ->
                if (zip.getEntry(NESTED) == null) return null
            }
            val uri = URI("jar:" + wrapper.toAbsolutePath().toUri())
            val fs = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            filesystems.add(fs)
            val nested = fs.getPath(NESTED)
            if (!Files.isRegularFile(nested)) {
                logger.warn("embedded {} missing in {}", NESTED, wrapper.fileName)
                return null
            }
            logger.info("located embedded game jar in {}", wrapper.fileName)
            nested
        } catch (t: Throwable) {
            logger.error("failed to open embedded game jar from {}", wrapper, t)
            null
        }
    }

    companion object {
        const val NESTED = "META-INF/mod/game.jar"
    }
}
