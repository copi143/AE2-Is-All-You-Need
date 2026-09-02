package allyouneed.transformer

import java.lang.invoke.MethodHandles

object RuntimeClasses {
    const val PREFIX = "META-INF/inject/"
    const val INDEX = PREFIX + "classes.txt"

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val loader = findLoader()
            val aeKey = Class.forName("appeng.api.stacks.AEKey", false, loader)
            val resourceLocation = Class.forName("net.minecraft.resources.ResourceLocation", false, loader)
            val self = RuntimeClasses::class.java.module
            val ae2 = aeKey.module
            if (!self.canRead(ae2)) self.addReads(ae2)
            val mc = resourceLocation.module
            if (!self.canRead(mc)) self.addReads(mc)
            val aeLookup = MethodHandles.privateLookupIn(aeKey, MethodHandles.lookup())
            val mcLookup = MethodHandles.privateLookupIn(resourceLocation, MethodHandles.lookup())
            val names = classNames()
            val mcNames = names.filter { it.startsWith("net.minecraft.resources.") }
            val aeNames = names.filter { !it.startsWith("net.minecraft.resources.") }
            for (name in aeNames) define(aeLookup, aeKey.classLoader, name)
            for (name in mcNames) define(mcLookup, resourceLocation.classLoader, name)
            installed = true
            logger.info(
                "defined intern runtime classes: {} into module {} (loader {}), {} into module {} (loader {})",
                aeNames.size,
                ae2.name,
                aeKey.classLoader.javaClass.name,
                mcNames.size,
                mc.name,
                resourceLocation.classLoader.javaClass.name,
            )
        }
    }

    private fun classNames(): List<String> {
        val text = RuntimeClasses::class.java.classLoader.getResourceAsStream(INDEX)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("missing $INDEX")
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    internal fun findLoader(): ClassLoader {
        var cl: ClassLoader? = Thread.currentThread().contextClassLoader
        while (cl != null) {
            if (cl.javaClass.name.contains("TransformingClassLoader")) return cl
            cl = cl.parent
        }
        return Thread.currentThread().contextClassLoader
            ?: RuntimeClasses::class.java.classLoader
    }

    private fun define(lookup: MethodHandles.Lookup, loader: ClassLoader, name: String) {
        try {
            Class.forName(name, false, loader)
            return
        } catch (_: ClassNotFoundException) {
        }
        val path = PREFIX + name.replace('.', '/') + ".class"
        val bytes = RuntimeClasses::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: throw IllegalStateException("missing $path")
        lookup.defineClass(bytes)
    }
}
