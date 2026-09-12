package allyouneed.transformer

import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.ProviderNotFoundException
import java.util.stream.Stream

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
        // 注意：同一个 jar 只用 ZipFS 打开一次。以前这里先用 java.util.zip.ZipFile
        // 读一遍中央目录只为检查 entry，关掉后又用 ZipFileSystem 读第二遍；
        // 大 mod 的中央目录有几 MB，白白读两遍。现在直接开 ZipFS 并通过它检查，
        // 不命中的直接 close，不留任何常驻结构。
        val fs = try {
            openJarFs(wrapper)
        } catch (t: Throwable) {
            logger.error("failed to open embedded game jar from {}", wrapper, t)
            return null
        }
        val nested = fs.getPath(NESTED)
        if (!Files.exists(nested)) {
            // 普通 mod jar，直接关掉走人（旧逻辑里 ZipFile 检查未命中的静默路径）。
            fs.closeQuietly(wrapper)
            return null
        }
        if (!Files.isRegularFile(nested)) {
            logger.warn("embedded {} missing in {}", NESTED, wrapper.fileName)
            fs.closeQuietly(wrapper)
            return null
        }
        filesystems.add(fs)
        logger.info("located embedded game jar in {}", wrapper.fileName)
        return nested
    }

    /**
     * 打开（或复用已打开的）jar ZipFS。ModLauncher 同一 JVM 内可能多次扫描，
     * 直接 newFileSystem 会抛 FileSystemAlreadyExistsException。
     */
    private fun openJarFs(wrapper: Path): FileSystem {
        val uri = URI("jar:" + wrapper.toAbsolutePath().toUri())
        try {
            return FileSystems.getFileSystem(uri)
        } catch (_: FileSystemNotFoundException) {
        } catch (_: ProviderNotFoundException) {
        } catch (_: IllegalArgumentException) {
        }
        return FileSystems.newFileSystem(uri, emptyMap<String, Any>())
    }

    private fun FileSystem.closeQuietly(wrapper: Path) {
        try {
            close()
        } catch (t: Throwable) {
            logger.warn("failed to close jar filesystem for {}", wrapper, t)
        }
    }

    companion object {
        const val NESTED = "META-INF/mod/game.jar"
    }
}
