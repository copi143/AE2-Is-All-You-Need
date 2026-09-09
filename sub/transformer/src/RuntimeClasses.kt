package allyouneed.transformer

import java.lang.invoke.MethodHandles

object RuntimeClasses {
    const val PREFIX = "META-INF/inject/"
    const val INDEX = PREFIX + "classes.txt"

    @Volatile
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        val loader = findLoader()
        val names = classNames()
        Class.forName("appeng.api.stacks.AEKey", false, loader)
            .install(names.filter { it.startsWith("appeng.api.stacks.") })
        Class.forName("net.minecraft.resources.ResourceLocation", false, loader)
            .install(names.filter { it.startsWith("net.minecraft.resources.") })
        installed = true
    }

    private fun Class<*>.install(inject: List<String>) {
        val self = RuntimeClasses::class.java.module
        val other = this.module
        if (!self.canRead(other)) self.addReads(other)
        val lookup = MethodHandles.privateLookupIn(this, MethodHandles.lookup())
        for (name in inject) {
            define(lookup, this.classLoader, name)
            logger.info(
                "defined intern runtime classes: {} into module {} (loader {})",
                name,
                other.name,
                other.classLoader.javaClass.name,
            )
        }
    }

    private fun classNames(): List<String> {
        val text =
            RuntimeClasses::class.java.classLoader.getResourceAsStream(INDEX)?.use { it.readBytes().decodeToString() }
                ?: throw IllegalStateException("missing $INDEX")
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    internal fun findLoader(): ClassLoader {
        var cl: ClassLoader? = Thread.currentThread().contextClassLoader
        while (cl != null) {
            if (cl.javaClass.name.contains("TransformingClassLoader")) return cl
            cl = cl.parent
        }
        return Thread.currentThread().contextClassLoader ?: RuntimeClasses::class.java.classLoader
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
