package mapaccumulation

import allyouneed.transformer.MapAccumulationTransformer
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import mapaccumulation.fixtures.AccumulationCalls
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.lang.reflect.InvocationTargetException
import java.util.function.IntBinaryOperator as JdkOperator
import java.util.jar.JarFile

class MapAccumulationTest {
    private val fixtureName = AccumulationCalls::class.java.name
    private fun fixtureNode(): ClassNode = ClassNode().also {
        ClassReader(javaClass.classLoader.getResourceAsStream(fixtureName.replace('.', '/') + ".class")!!).accept(it, 0)
    }
    private fun transformed(): Class<*> {
        val node = fixtureNode()
        val fields = node.fields.map { it.name to it.desc }
        assertEquals(8, MapAccumulationTransformer.rewrite(node))
        assertEquals(fields, node.fields.map { it.name to it.desc }, "call-site optimization must not add reflective fields")
        assertEquals(0, MapAccumulationTransformer.rewrite(node), "second pass must not wrap fallback calls")
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        return object : ClassLoader(javaClass.classLoader) {
            fun define(): Class<*> = defineClass(fixtureName, writer.toByteArray(), 0, writer.toByteArray().size)
        }.define()
    }
    private fun invoke(cls: Class<*>, name: String, map: Object2IntMap<Any>?, key: Any?, delta: Int, vararg extra: Any): Any? {
        try {
            return cls.methods.single { it.name == name }.invoke(null, map, key, delta, *extra)
        } catch (e: InvocationTargetException) { throw e.cause!! }
    }

    @Test
    fun valuesDefaultsNullKeysAndOverflowMatch() {
        val fast = transformed()
        for (method in listOf("direct", "throughInterface", "localAlias", "boxed", "stackPrefix")) {
            for (default in listOf(0, -7, Int.MAX_VALUE)) {
                val control = Object2IntOpenHashMap<Any>().also { it.defaultReturnValue(default) }
                val actual = Object2IntOpenHashMap<Any>().also { it.defaultReturnValue(default) }
                for (key in listOf(null, "same", 42, Any())) {
                    for (delta in listOf(0, 1, -3, Int.MAX_VALUE, Int.MIN_VALUE)) {
                        assertEquals(invoke(AccumulationCalls::class.java, method, control, key, delta),
                            invoke(fast, method, actual, key, delta), "$method default=$default delta=$delta")
                        assertEquals(control, actual)
                    }
                }
            }
        }
    }

    @Test
    fun provenBranchesAndUnknownBranchesAreDistinguished() {
        val node = fixtureNode()
        MapAccumulationTransformer.rewrite(node)
        for (name in listOf("unknownBranch", "captured", "maximum", "missingCallback")) {
            assertFalse(node.methods.single { it.name == name }.instructions.filterIsInstance<MethodInsnNode>()
                .any { it.owner.endsWith("MapAccumulationSupport") }, name)
        }
        val cls = transformed()
        val map = Object2IntOpenHashMap<Any>()
        assertEquals(2, invoke(cls, "knownBranch", map, "key", 2, true))
        assertEquals(5, invoke(cls, "knownBranch", map, "key", 3, false))
        assertEquals(15, invoke(cls, "unknownBranch", map, "key", 3, false, JdkOperator { a, b -> a * b }))
        assertEquals(22, invoke(cls, "captured", map, "key", 3, 4))
        assertThrows(NullPointerException::class.java) { invoke(cls, "missingCallback", map, "key", 2) }
    }

    class OverridingMap : Object2IntOpenHashMap<Any>() {
        override fun mergeInt(key: Any?, value: Int, op: JdkOperator): Int = 991
    }

    @Test
    fun exactClassGuardPreservesSubclassOverridesAndNullReceiver() {
        val cls = transformed()
        assertEquals(991, invoke(cls, "direct", OverridingMap(), "key", 1))
        assertThrows(NullPointerException::class.java) { invoke(cls, "evaluation", null, "key", 1) }
        assertEquals("rkd", cls.getField("trace").get(null))
        assertEquals(4, invoke(cls, "throughInterface", Object2IntArrayMap(), "key", 4))
        assertEquals(1, invoke(cls, "evaluation", Object2IntOpenHashMap(), "key", 1))
        assertEquals("rkd", cls.getField("trace").get(null))
    }

    class CountingKey {
        var calls = 0
        override fun hashCode(): Int { calls++; return 7 }
    }
    class ThrowingKey {
        var calls = 0
        override fun hashCode(): Int { if (++calls == 2) throw IllegalStateException("second hash"); return 3 }
    }
    class HostileStoredKey {
        override fun hashCode(): Int = "safe".hashCode()
        override fun equals(other: Any?): Boolean = throw AssertionError("stored.equals must not be called")
    }

    @Test
    fun unknownKeyEffectsAndExceptionsUseOriginalPath() {
        val cls = transformed()
        val first = CountingKey()
        val second = CountingKey()
        assertEquals(1, invoke(AccumulationCalls::class.java, "direct", Object2IntOpenHashMap(), first, 1))
        assertEquals(1, invoke(cls, "direct", Object2IntOpenHashMap(), second, 1))
        assertEquals(first.calls, second.calls)
        for (type in listOf(AccumulationCalls::class.java, cls)) {
            val map = Object2IntOpenHashMap<Any>()
            assertThrows(IllegalStateException::class.java) { invoke(type, "direct", map, ThrowingKey(), 1) }
            assertTrue(map.isEmpty())
        }
    }

    @Test
    fun collisionComparisonDirectionIsPreserved() {
        val cls = transformed()
        for (type in listOf(AccumulationCalls::class.java, cls)) {
            val map = Object2IntOpenHashMap<Any>()
            map.put(HostileStoredKey(), 3)
            assertEquals(1, invoke(type, "direct", map, "safe", 1))
            assertEquals(2, map.size)
        }
    }

    @Test
    fun originalExceptionHandlersStillCoverBothPaths() {
        val cls = transformed()
        assertEquals(-23, invoke(cls, "caught", null, "key", 1))
        assertEquals(-23, invoke(cls, "caught", Object2IntOpenHashMap(), ThrowingKey(), 1))
        assertEquals(1, invoke(cls, "caught", Object2IntOpenHashMap(), "key", 1))
    }

    @Test
    fun analysisBudgetLeavesLargeMethodsUntouched() {
        val node = fixtureNode()
        node.methods.removeIf { it.name != "direct" }
        repeat(4100) { node.methods.single().instructions.insert(InsnNode(Opcodes.NOP)) }
        assertEquals(0, MapAccumulationTransformer.rewrite(node))
    }

    @Test
    fun actualGtceuBytecodeMatchesWithoutModSpecificRules() {
        val bytes = JarFile(System.getProperty("mapAccumulation.gtJar")).use { jar ->
            jar.getInputStream(jar.getJarEntry("com/gregtechceu/gtceu/api/recipe/lookup/StagingRecipeDB.class")).use { it.readBytes() }
        }
        val node = ClassNode()
        ClassReader(bytes).accept(node, 0)
        assertEquals(2, MapAccumulationTransformer.rewrite(node))
        val transformedMethods = node.methods.filter { method -> method.instructions.filterIsInstance<MethodInsnNode>()
            .any { it.owner.endsWith("MapAccumulationSupport") } }
        assertEquals(2, transformedMethods.size)
        assertTrue(transformedMethods.all { it.name.startsWith("lambda\$inputFrequencies\$") })
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        assertTrue(writer.toByteArray().isNotEmpty())
    }
}
