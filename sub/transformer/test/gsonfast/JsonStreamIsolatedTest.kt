package gsonfast

import allyouneed.transformer.JsonStreamTransformer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile

/**
 * 端到端隔离测试：独立 ClassLoader 加载真实 gson 2.10 字节码，
 * 对 Streams.write 应用流式钩子、对 fixture 序列化器应用委托者改写，
 * 经真实 Gson + TreeTypeAdapter 路径输出，与未变换对照 loader 逐字节一致，
 * 且确认输出确实走了流式路径（streamed > 0、物化 0 次）。
 */
class JsonStreamIsolatedTest {
    private class ChildCL(
        gsonJarFile: File,
        private val extra: Map<String, ByteArray>,
        private val transform: Boolean,
        parent: ClassLoader,
    ) : ClassLoader(parent) {
        private val jar = JarFile(gsonJarFile)

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (extra.containsKey(name) || name.startsWith("com.google.gson.")) {
                synchronized(getClassLoadingLock(name)) {
                    findLoadedClass(name)?.let { return it }
                    var bytes = extra[name] ?: jarEntry(name) ?: throw ClassNotFoundException(name)
                    if (transform) {
                        bytes = when {
                            name == "com.google.gson.internal.Streams" ->
                                rewrite(bytes) { JsonStreamTransformer.applyStreamsHook(it) }

                            name == "gsonfast.pojos.FakeTreeSerializer" ->
                                rewrite(bytes) { JsonStreamTransformer.apply(it, listOf(FIXTURE_ENTRY)) }

                            else -> bytes
                        }
                    }
                    return defineClass(name, bytes, 0, bytes.size)
                }
            }
            return super.loadClass(name, resolve)
        }

        private fun rewrite(bytes: ByteArray, apply: (ClassNode) -> Boolean): ByteArray {
            val cn = ClassNode()
            ClassReader(bytes).accept(cn, 0)
            check(apply(cn)) { "transform must apply" }
            val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            cn.accept(cw)
            return cw.toByteArray()
        }

        private fun jarEntry(name: String): ByteArray? {
            val entry = jar.getEntry(name.replace('.', '/') + ".class") ?: return null
            return jar.getInputStream(entry).use { it.readBytes() }
        }
    }

    private fun bridgeParent(): ClassLoader {
        val testCL = javaClass.classLoader
        return object : ClassLoader(ClassLoader.getPlatformClassLoader()) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> =
                if (name.startsWith("org.objectweb.asm.") || name.startsWith("org.slf4j.")) {
                    testCL.loadClass(name)
                } else {
                    super.loadClass(name, resolve)
                }
        }
    }

    private fun injectClasses(): Map<String, ByteArray> {
        val pkg = "com/google/gson/internal/bind"
        val url = Thread.currentThread().contextClassLoader.getResource("$pkg/GsonFastPath.class")
            ?: error("inject classes not on test classpath")
        val dir = File(url.toURI()).parentFile
        val result = LinkedHashMap<String, ByteArray>()
        dir.listFiles { f -> f.name.endsWith(".class") }!!.forEach { f ->
            result["$pkg.${f.name.removeSuffix(".class")}".replace('/', '.')] = f.readBytes()
        }
        result["gsonfast.pojos.FakeTreeSerializer"] = resourceBytes("gsonfast/pojos/FakeTreeSerializer.class")
        result["gsonfast.pojos.FakeTreeSerializer\$Status"] = resourceBytes("gsonfast/pojos/FakeTreeSerializer\$Status.class")
        return result
    }

    @Test
    fun endToEndStreamedOutputMatches() {
        val jar = gsonJar()
        val inject = injectClasses()
        val control = ChildCL(jar, inject.filterKeys { it.startsWith("gsonfast.") }, false, bridgeParent())
        val fast = ChildCL(jar, inject, true, bridgeParent())

        val expected = control.loadClass("gsonfast.pojos.FakeTreeSerializer")
            .getMethod("scenario").invoke(null) as List<*>
        val actual = fast.loadClass("gsonfast.pojos.FakeTreeSerializer")
            .getMethod("scenario").invoke(null) as List<*>
        assertEquals(expected, actual, "streamed output mismatch")

        val sjo = fast.loadClass("com.google.gson.internal.bind.StreamJsonObject")
        val streamed = sjo.getMethod("streamedCount").invoke(null) as Long
        val materialized = sjo.getMethod("materializationCount", String::class.java)
            .invoke(null, "gsonfast.pojos.FakeTreeSerializer.serialize") as Long
        assertTrue(streamed >= expected.size.toLong(), "expected streaming writes, got $streamed")
        assertEquals(0L, materialized, "hot path must not materialize")
    }

    private companion object {
        const val FIXTURE_ENTRY =
            "gsonfast/pojos/FakeTreeSerializer#serialize " +
                "(Lgsonfast/pojos/FakeTreeSerializer\$Status;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;"
    }
}
