package allyouneed.transformer

import cpw.mods.modlauncher.api.IEnvironment
import cpw.mods.modlauncher.api.ITransformationService
import cpw.mods.modlauncher.api.ITransformer
import cpw.mods.modlauncher.api.ITransformerVotingContext
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException
import cpw.mods.modlauncher.api.TransformerVoteResult
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path

class KeyInternTransformationService : ITransformationService {
    private var gameDir: Path? = null

    override fun name(): String = "ae2isallyouneed_core"

    override fun initialize(environment: IEnvironment) {
        gameDir = environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElse(null)
    }

    @Throws(IncompatibleEnvironmentException::class)
    override fun onLoad(env: IEnvironment, otherServices: Set<String>) {
        System.setProperty(INSTALLED_PROP, "true")
        Log.info("ITransformationService onLoad (plugin layer, before game classes)")
    }

    override fun transformers(): List<ITransformer<*>> {
        val scan = KeyClassScanner.scan(scanMods())
        Log.info("ITransformationService transformers: {} keys, {} targets", scan.keys.size, scan.targets.size)
        for (key in scan.targets) Log.info("  target {}", key.replace('/', '.'))
        return listOf(KeyInternClassTransformer(scan.keys, scan.targets))
    }

    private fun scanMods(): List<Path> {
        val dir = gameDir?.resolve("mods") ?: return emptyList()
        if (!Files.isDirectory(dir)) return emptyList()
        return try {
            Files.list(dir).use { stream ->
                val out = ArrayList<Path>()
                stream.filter { it.fileName.toString().endsWith(".jar") }.forEach { out.add(it) }
                out
            }
        } catch (t: Throwable) {
            Log.error("mods dir scan failed", t)
            emptyList()
        }
    }

    companion object {
        const val INSTALLED_PROP = "allyouneed.core.transformer"
    }
}

class KeyInternClassTransformer(
    private val keyClasses: Set<String>,
    private val targets: Set<String>,
) : ITransformer<ClassNode> {
    override fun transform(input: ClassNode, context: ITransformerVotingContext): ClassNode {
        NewCallTransformer.apply(input, keyClasses)
        return input
    }

    override fun castVote(context: ITransformerVotingContext): TransformerVoteResult = TransformerVoteResult.YES

    override fun targets(): Set<ITransformer.Target> {
        val out = LinkedHashSet<ITransformer.Target>()
        for (key in targets) out.add(ITransformer.Target.targetClass(key.replace('/', '.')))
        return out
    }
}
