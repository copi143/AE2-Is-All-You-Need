package allyouneed.fabric.early

import allyouneed.transformer.KeyClassScanner
import allyouneed.transformer.NewCallTransformer
import allyouneed.transformer.RuntimeClasses
import allyouneed.transformer.logger
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.game.patch.GameTransformer
import sun.misc.Unsafe
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class FabricPreLaunch : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        try {
            install()
        } catch (t: Throwable) {
            logger.error("AEKey intern transformer not installed; falling back to stock constructors", t)
        }
    }

    private fun install() {
        RuntimeClasses.install()
        val paths = scanPaths()
        logger.info("Fabric preLaunch starting, {} scan paths", paths.size)
        val scan = KeyClassScanner.scan(paths)
        val knot = Thread.currentThread().contextClassLoader
        val delegate = field(knot, "delegate") ?: throw IllegalStateException("Knot delegate missing")
        val provider = field(delegate, "provider") as GameProvider
        val wrappedTx = InterningTransformer(provider.entrypointTransformer, scan.keys, scan.targets, knot)
        val proxy = Proxy.newProxyInstance(
            provider.javaClass.classLoader,
            arrayOf(GameProvider::class.java),
        ) { _, method, args ->
            if (method.name == "getEntrypointTransformer") wrappedTx
            else if (args == null) method.invoke(provider)
            else method.invoke(provider, *args)
        }
        putField(delegate, "provider", proxy)
        logger.info("Wrapped Knot GameProvider for {} AEKey intern targets", scan.targets.size)
    }

    private fun scanPaths(): List<Path> {
        val out = LinkedHashSet<Path>()
        try {
            val mods = net.fabricmc.loader.api.FabricLoader.getInstance().gameDir.resolve("mods")
            if (Files.isDirectory(mods)) {
                Files.list(mods).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".jar") }.forEach { out.add(it) }
                }
            }
        } catch (_: Throwable) {
        }
        try {
            val self = javaClass.protectionDomain.codeSource?.location
            if (self != null) out.add(Path.of(self.toURI()))
        } catch (_: Throwable) {
        }
        return out.toList()
    }

    private fun field(owner: Any, name: String): Any? {
        var c: Class<*>? = owner.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(owner)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    private fun putField(owner: Any, name: String, value: Any) {
        var c: Class<*>? = owner.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                val unsafe = unsafe()
                unsafe.putObject(owner, unsafe.objectFieldOffset(f), value)
                return
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun unsafe(): Unsafe {
        val f = Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        return f.get(null) as Unsafe
    }
}

private class InterningTransformer(
    private val original: GameTransformer,
    private val keys: Set<String>,
    private val targets: Set<String>,
    private val loader: ClassLoader,
) : GameTransformer() {
    private val cache = HashMap<String, ByteArray>()

    override fun transform(className: String): ByteArray? {
        cache[className]?.let { return it }
        val orig = original.transform(className)
        if (className.replace('.', '/') !in targets) return orig
        val bytes = orig ?: loader.getResourceAsStream(className.replace('.', '/') + ".class")?.use { it.readBytes() }
            ?: return orig
        val out = NewCallTransformer.apply(bytes, keys)
        cache[className] = out
        logger.debug("fabric transform {}", className)
        return out
    }
}
