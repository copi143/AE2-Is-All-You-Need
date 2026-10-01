package gsonfast

import allyouneed.transformer.JsonStreamTransformer
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.StringWriter
import java.lang.reflect.Proxy
import java.util.UUID
import com.google.gson.stream.JsonWriter

/**
 * 直接行为测试：变换 fixture 序列化器后与原版逐字节对比（流式写出 + 物化路径），
 * 白名单外方法拒绝改写，熔断器在物化阈值后切 eager。
 */
class JsonStreamTransformerTest {
    private val fixtureEntry =
        "gsonfast/pojos/FakeTreeSerializer#serialize " +
            "(Lgsonfast/pojos/FakeTreeSerializer\$Status;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;"
    private val badEntry =
        "gsonfast/pojos/FakeTreeSerializer#serializeBad " +
            "(Lgsonfast/pojos/FakeTreeSerializer\$Status;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;"

    private class ChildCL(private val name: String, private val bytes: ByteArray, parent: ClassLoader) :
        ClassLoader(parent) {
        override fun loadClass(n: String, resolve: Boolean): Class<*> =
            if (n == name) {
                findLoadedClass(n) ?: defineClass(n, bytes, 0, bytes.size)
            } else {
                super.loadClass(n, resolve)
            }
    }

    private fun transformedFixture(vararg entries: String): Class<*> {
        val bytes = resourceBytes("gsonfast/pojos/FakeTreeSerializer.class")
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, 0)
        check(JsonStreamTransformer.apply(cn, entries.toList())) { "transform must apply" }
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cn.accept(cw)
        return ChildCL("gsonfast.pojos.FakeTreeSerializer", cw.toByteArray(), javaClass.classLoader)
            .loadClass("gsonfast.pojos.FakeTreeSerializer")
    }

    private fun statusClass(): Class<*> = Class.forName("gsonfast.pojos.FakeTreeSerializer\$Status")

    private fun newStatus(motd: String?, max: Int?, online: Boolean?, sample: Array<String>?, extra: JsonElement?): Any =
        statusClass()
            .getConstructor(String::class.java, Integer::class.java, java.lang.Boolean::class.java, Array<String>::class.java, JsonElement::class.java)
            .newInstance(motd, max?.let { Integer.valueOf(it) }, online?.let { java.lang.Boolean.valueOf(it) }, sample, extra)

    private fun ctx(): JsonSerializationContext =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(JsonSerializationContext::class.java)) { _, _, args ->
            val v = args[0]
            if (v == null) JsonNull.INSTANCE else JsonPrimitive(v.toString())
        } as JsonSerializationContext

    private fun serialize(serializer: Any, status: Any): JsonElement =
        serializer.javaClass
            .getMethod("serialize", statusClass(), java.lang.reflect.Type::class.java, JsonSerializationContext::class.java)
            .invoke(serializer, status, statusClass(), ctx()) as JsonElement

    private fun hookWrite(element: JsonElement): String {
        val sw = StringWriter()
        val jw = JsonWriter(sw)
        jw.setLenient(true)
        jw.setSerializeNulls(false)
        val sjo = Class.forName("com.google.gson.internal.bind.StreamJsonObject")
        val handled = sjo.getMethod("writeHook", JsonElement::class.java, JsonWriter::class.java).invoke(null, element, jw) as Boolean
        assertTrue(handled, "writeHook should handle transformed result")
        jw.flush()
        return sw.toString()
    }

    private fun controlJson(status: Any): String {
        val root = JsonObject()
        val cls = statusClass()
        val motd = cls.getField("motd").get(status) as String?
        val max = cls.getField("max").get(status) as Number?
        val online = cls.getField("online").get(status) as Boolean?
        val sample = cls.getField("sample").get(status) as Array<String>?
        val extra = cls.getField("extra").get(status) as JsonElement?
        if (motd != null) root.addProperty("motd", motd)
        max?.let { root.addProperty("max", it) }
        online?.let { root.addProperty("online", it) }
        if (extra != null) root.add("extra", extra)
        if (sample != null) {
            val arr = com.google.gson.JsonArray()
            for (name in sample) {
                val p = JsonObject()
                p.addProperty("name", name)
                p.addProperty("id", name.length)
                arr.add(p)
            }
            root.add("sample", arr)
        }
        root.add("ctx", if (motd == null) JsonNull.INSTANCE else JsonPrimitive(motd))
        return Gson().toJson(root)
    }

    @Test
    fun streamingOutputMatchesTree() {
        val serializer = transformedFixture(fixtureEntry).getConstructor().newInstance()
        val statuses = listOf(
            newStatus("hello", 20, true, arrayOf("a", "bb"), null),
            newStatus(null, null, null, null, null),
            newStatus("", 0, false, arrayOf(), JsonObject()),
            newStatus("x\"y\nz", -1, null, arrayOf(""), JsonObject().apply { addProperty("k", "v") }),
            newStatus("nullSample", 1, true, null, JsonNull.INSTANCE),
        )
        for (status in statuses) {
            val tree = serialize(serializer, status)
            assertEquals(
                "com.google.gson.internal.bind.StreamJsonObject",
                tree.javaClass.name,
                "transformed serialize must return the delegate",
            )
            val expected = controlJson(status)
            assertEquals(expected, hookWrite(tree), "streamed output mismatch for $expected")
            // 物化路径（无钩子环境/树消费）也必须逐字节一致
            assertEquals(expected, Gson().toJson(tree), "materialized output mismatch for $expected")
        }
    }

    @Test
    fun whitelistMismatchIsRejected() {
        val bytes = resourceBytes("gsonfast/pojos/FakeTreeSerializer.class")
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, 0)
        assertFalse(
            cn.methods.first { it.name == "serializeBad" }.let {
                JsonStreamTransformer.apply(cn, listOf(badEntry))
            },
            "size()/entrySet() usage must reject the method",
        )
        // 未变换的 serializeBad 仍返回真实 JsonObject
        val serializer = gsonfast.pojos.FakeTreeSerializer()
        val status = newStatus("m", 1, true, null, null)
        val tree = serializer.serializeBad(
            status as gsonfast.pojos.FakeTreeSerializer.Status,
            gsonfast.pojos.FakeTreeSerializer.Status::class.java,
            ctx(),
        )
        assertTrue(tree is JsonObject)
    }

    @Test
    fun circuitBreakerSwitchesToEager() {
        val tag = "test.breaker." + UUID.randomUUID()
        val sjo = Class.forName("com.google.gson.internal.bind.StreamJsonObject")
        val ctor = sjo.getConstructor(String::class.java)
        val materializations = sjo.getMethod("materializationCount", String::class.java)
        repeat(64) {
            val inst = ctor.newInstance(tag)
            sjo.getMethod("add", String::class.java, JsonElement::class.java).invoke(inst, "k", JsonPrimitive(it))
            sjo.getMethod("materialize").invoke(inst)
        }
        assertEquals(64L, materializations.invoke(null, tag))
        // 熔断后新实例为 eager：materialize 不再计数
        val eager = ctor.newInstance(tag)
        sjo.getMethod("addProperty", String::class.java, String::class.java).invoke(eager, "x", "y")
        assertEquals(64L, materializations.invoke(null, tag), "eager instance must not count materialization")
        assertEquals("""{"x":"y"}""", hookWrite(eager as JsonElement))
    }

    @Test
    fun streamsHookInstalls() {
        val bytes = java.util.jar.JarFile(gsonJar()).use { jar ->
            jar.getInputStream(jar.getEntry("com/google/gson/internal/Streams.class")).use { it.readBytes() }
        }
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, 0)
        assertTrue(JsonStreamTransformer.apply(cn, emptyList()), "Streams hook must install on gson 2.10 bytes")
        val write = cn.methods.first { it.name == "write" }
        val insns = write.instructions.toArray().filterIsInstance<MethodInsnNode>()
        assertTrue(
            insns.any {
                it.opcode == Opcodes.INVOKESTATIC &&
                    it.owner == "com/google/gson/internal/bind/StreamJsonObject" && it.name == "writeHook"
            },
            "hook call missing",
        )
    }
}
