package gsonfast

import allyouneed.transformer.GsonFastPathTransformer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile

/**
 * 端到端隔离测试：在独立的 ClassLoader 中加载真实 gson jar 字节码，
 * 对 ReflectiveTypeAdapterFactory 应用变换后跑完整 Gson roundtrip，
 * 与未变换的对照 ClassLoader 比较行为一致性，并确认快速路径确实生效。
 *
 * 该测试同时验证了生成字节码的校验（defineHiddenClass 触发 verifier）、
 * 注入类与 gson 同包的包私有访问、以及 MethodHandle 字段访问路径。
 */
class GsonFastPathIsolatedTest {
    private class ChildCL(
        private val gsonJarFile: File,
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
                    if (transform && name == "com.google.gson.internal.bind.ReflectiveTypeAdapterFactory") {
                        bytes = rewrite(bytes) { GsonFastPathTransformer.apply(it) }
                    }
                    if (name == "gsonfast.pojos.GsonConsumer" && extra.containsKey(name)) {
                        val cn = ClassNode()
                        ClassReader(bytes).accept(cn, 0)
                        if (GsonFastPathTransformer.applyCallSites(cn) > 0) {
                            val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
                            cn.accept(cw)
                            bytes = cw.toByteArray()
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
        check(url.protocol == "file") { "unexpected resource url $url" }
        val dir = File(url.toURI()).parentFile
        val result = LinkedHashMap<String, ByteArray>()
        dir.listFiles { f -> f.name.endsWith(".class") }!!.forEach { f ->
            result["$pkg.${f.name.removeSuffix(".class")}".replace('/', '.')] = f.readBytes()
        }
        result["gsonfast.pojos.IsolatedPojo"] = resourceBytes("gsonfast/pojos/IsolatedPojo.class")
        result["gsonfast.pojos.IsolatedNested"] = resourceBytes("gsonfast/pojos/IsolatedNested.class")
        result["gsonfast.pojos.GsonConsumer"] = resourceBytes("gsonfast/pojos/GsonConsumer.class")
        return result
    }

    private class Scenario(val jsons: List<String>, val adapterNames: List<String>)

    private fun runScenario(cl: ClassLoader, inputs: List<String>): Scenario {
        val gsonClass = cl.loadClass("com.google.gson.Gson")
        val gson = gsonClass.getConstructor().newInstance()
        val fromJson = gsonClass.getMethod("fromJson", String::class.java, Class::class.java)
        val toJson = gsonClass.getMethod("toJson", Any::class.java)
        val getAdapter = gsonClass.getMethod("getAdapter", Class::class.java)
        val pojo = cl.loadClass("gsonfast.pojos.IsolatedPojo")
        val nested = cl.loadClass("gsonfast.pojos.IsolatedNested")
        return Scenario(
            inputs.map { toJson.invoke(gson, fromJson.invoke(gson, it, pojo)) as String },
            listOf(pojo, nested).map { getAdapter.invoke(gson, it).javaClass.name },
        )
    }

    @Test
    fun endToEndConsistency() {
        val jar = gsonJar()
        val inputs = listOf(
            """{"a":1,"b":"x","nested":{"v":2}}""",
            """{"a":null,"bAlt":"alt","nested":null}""",
            """{"unknown":{"deep":[1,2,3]},"a":7}""",
            """{}""",
            """{"a":-2147483648,"b":"","nested":{"v":null}}""",
        )
        val ctrl = runScenario(ChildCL(jar, injectClasses().filterKeys { it.startsWith("gsonfast.") }, false, bridgeParent()), inputs)
        val fastCl = ChildCL(jar, injectClasses(), true, bridgeParent())
        val fast = runScenario(fastCl, inputs)

        assertEquals(ctrl.jsons, fast.jsons, "roundtrip output mismatch")
        val fpClass = fastCl.loadClass("com.google.gson.internal.bind.GsonFastPath")
        val generated = fpClass.getMethod("generatedCount").invoke(null) as Long
        val fallback = fpClass.getMethod("fallbackCount").invoke(null) as Long
        val lastFallback = fpClass.getField("lastFallback").get(null) as Throwable?
        assertTrue(
            fast.adapterNames.all { it.contains("FastReflectiveAdapter") },
            "generated adapters expected, got ${fast.adapterNames} (generated=$generated fallback=$fallback last=$lastFallback)",
        )
        assertTrue(
            ctrl.adapterNames.none { it.contains("FastReflectiveAdapter") },
            "control must use reflective adapters, got ${ctrl.adapterNames}",
        )
        assertTrue(generated >= 2, "at least IsolatedPojo + IsolatedNested should be generated, got $generated")
    }

    /**
     * 模拟 Forge 环境：RTAF 不变换（BOOT 层不可达），只改写调用点，
     * Gson 实例的反射工厂应在构造后被替换为 FastFactory，生成的适配器照常生效。
     */
    @Test
    fun callSiteWrappingConsistency() {
        val jar = gsonJar()
        val cl = ChildCL(jar, injectClasses(), transform = false, bridgeParent())
        val consumer = cl.loadClass("gsonfast.pojos.GsonConsumer")
        val pojo = cl.loadClass("gsonfast.pojos.IsolatedPojo")
        val gsonClass = cl.loadClass("com.google.gson.Gson")
        val gsonBuilderClass = cl.loadClass("com.google.gson.GsonBuilder")
        fun newBuilder() = gsonBuilderClass.getConstructor().newInstance()
        val controls = mapOf(
            "makeGson" to gsonClass.getConstructor().newInstance(),
            "makeGsonFromBuilder" to gsonBuilderClass.getMethod("create").invoke(newBuilder()),
            "makePrettyGson" to gsonBuilderClass.getMethod("create")
                .invoke(gsonBuilderClass.getMethod("setPrettyPrinting").invoke(newBuilder())),
        )
        val input = """{"a":3,"bAlt":"z","nested":{"v":4}}"""
        val fromJson = gsonClass.getMethod("fromJson", String::class.java, Class::class.java)
        val toJson = gsonClass.getMethod("toJson", Any::class.java)
        for (method in listOf("makeGson", "makeGsonFromBuilder", "makePrettyGson")) {
            val gson = consumer.getMethod(method).invoke(null)
            val adapter = gsonClass.getMethod("getAdapter", Class::class.java).invoke(gson, pojo)
            assertTrue(
                adapter.javaClass.name.contains("FastReflectiveAdapter"),
                "$method should yield generated adapter, got ${adapter.javaClass.name}",
            )
            val control = controls.getValue(method)
            val controlOut = toJson.invoke(control, fromJson.invoke(control, input, pojo))
            val obj = fromJson.invoke(gson, input, pojo)
            assertEquals(controlOut, toJson.invoke(gson, obj), "$method roundtrip mismatch")
        }
    }
}
