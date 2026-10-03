package gsonfast

import allyouneed.transformer.GsonFastPathTransformer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.jar.JarFile

class GsonFastPathTransformerTest {
    @Test
    fun hookInstalledIntoRealGson() {
        val bytes = JarFile(gsonJar()).use { jar ->
            jar.getInputStream(jar.getEntry("com/google/gson/internal/bind/ReflectiveTypeAdapterFactory.class")).readBytes()
        }
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, 0)
        assertTrue(GsonFastPathTransformer.apply(cn), "transformer should apply to gson ${'$'}{gsonVersion()}")
        val create = cn.methods.first { it.name == "create" }
        assertTrue(
            create.instructions.toArray().any {
                it is MethodInsnNode && it.owner == "com/google/gson/internal/bind/GsonFastPath" && it.name == "wrap"
            },
            "wrap hook should be present in create()",
        )
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cn.accept(cw)
        cw.toByteArray()
        assertTrue(cn.fields.any { it.name == GsonFastPathTransformer.HOOK_MARKER })
        assertFalse(GsonFastPathTransformer.apply(cn), "the factory hook must be idempotent")
    }

    @Test
    fun unrelatedClassUntouched() {
        val cn = ClassNode()
        cn.name = "some/other/Class"
        assertFalse(GsonFastPathTransformer.apply(cn))
    }

    @Test
    fun callSitesRewritten() {
        val cn = ClassNode()
        ClassReader(resourceBytes("gsonfast/pojos/GsonConsumer.class")).accept(cn, 0)
        assertEquals(3, GsonFastPathTransformer.applyCallSites(cn))
        val insns = cn.methods.flatMap { it.instructions.toArray().toList() }
        assertEquals(2, insns.count {
            it is MethodInsnNode && it.owner == "com/google/gson/internal/bind/GsonFastPath" && it.name == "create"
        })
        assertEquals(1, insns.count {
            it is MethodInsnNode && it.owner == "com/google/gson/internal/bind/GsonFastPath" && it.name == "newGson"
        })
        assertEquals(0, insns.count {
            it is MethodInsnNode && it.name == "create" && it.owner == "com/google/gson/GsonBuilder"
        })
        // 可被序列化（COMPUTE_FRAMES 需要能处理 NOP 替换后的帧）
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cn.accept(cw)
        cw.toByteArray()
    }

    @Test
    fun callSitesSkippedForGsonPackage() {
        val cn = ClassNode()
        ClassReader(resourceBytes("gsonfast/pojos/GsonConsumer.class")).accept(cn, 0)
        cn.name = "com/google/gson/Something"
        assertEquals(0, GsonFastPathTransformer.applyCallSites(cn))
    }

    private fun gsonVersion(): String = gsonJar().name
}
