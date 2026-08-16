package allyouneed.transformer

import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream
import java.util.zip.ZipFile

class SelfModLocator : AbstractJarFileModLocator() {
    override fun name(): String = "ae2isallyouneed_self"

    override fun initArguments(arguments: Map<String, *>) {}

    override fun scanCandidates(): Stream<Path> {
        val nested = extractNested() ?: return Stream.empty()
        return Stream.of(nested)
    }

    private fun extractNested(): Path? {
        val self = selfJar() ?: return null
        if (!Files.isRegularFile(self)) return null
        ZipFile(self.toFile()).use { zip ->
            val entry = zip.getEntry(NESTED) ?: return null
            val destDir = FMLPaths.GAMEDIR.get().resolve(".ae2isallyouneed")
            Files.createDirectories(destDir)
            val dest = destDir.resolve("game.jar")
            zip.getInputStream(entry).use { input ->
                Files.copy(input, dest, StandardCopyOption.REPLACE_EXISTING)
            }
            Log.info("extracted embedded game jar to {}", dest)
            return dest
        }
    }

    private fun selfJar(): Path? {
        val url = javaClass.protectionDomain.codeSource?.location ?: return null
        return try {
            Path.of(url.toURI())
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val NESTED = "META-INF/mod/game.jar"
    }
}
