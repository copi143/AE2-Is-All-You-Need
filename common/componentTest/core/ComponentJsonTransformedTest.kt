package core

import allyouneed.transformer.ComponentJsonTransformer
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

class ComponentJsonTransformedTest {
    private class TransformedLoader(parent: ClassLoader) : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("net.minecraft.") && !name.startsWith("allyouneed.gsonfast.")) {
                return super.loadClass(name, resolve)
            }
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                var bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")?.use { it.readBytes() }
                    ?: throw ClassNotFoundException(name)
                if (ComponentJsonTransformer.isTarget(name.replace('.', '/'))) {
                    val cn = ClassNode()
                    ClassReader(bytes).accept(cn, 0)
                    check(ComponentJsonTransformer.apply(cn))
                    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
                    cn.accept(writer)
                    bytes = writer.toByteArray()
                }
                val cls = defineClass(name, bytes, 0, bytes.size)
                if (resolve) resolveClass(cls)
                return cls
            }
        }
    }

    @Test
    fun realGameClassesExecuteInjectedAccessorsAndEntryPoints() {
        val loader = TransformedLoader(javaClass.classLoader)
        val serializer = loader.loadClass("net.minecraft.network.chat.Component\$Serializer")
        val component = loader.loadClass("net.minecraft.network.chat.Component")
        val read = serializer.getMethod("fromJson", String::class.java)
        val write = serializer.getMethod("toJson", component)
        for (input in listOf(
            """{"text":"hello","bold":false,"italic":true,"font":"minecraft:uniform"}""",
            """{"text":"x","bold":true,"bold":false}""",
            """{"translate":"test.key","with":["x",{"text":"y","color":"red"}],"extra":[{"text":"!"}]}""",
        )) {
            val expected = Component.Serializer.toJson(requireNotNull(Component.Serializer.fromJson(input)))
            assertEquals(expected, write.invoke(null, read.invoke(null, input)))
        }
        val fast = loader.loadClass("allyouneed.gsonfast.ComponentJsonFast")
        val access = fast.getDeclaredField("STYLE_ACCESS").apply { isAccessible = true }.get(null)
        assertEquals("Injected", access.javaClass.simpleName, "must exercise the generated accessors, not reflective fallback")
    }
}
