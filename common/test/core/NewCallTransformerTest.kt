package allyouneed.core

import allyouneed.transformer.NewCallTransformer
import appeng.api.stacks.KeyInterner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.Label
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NewCallTransformerTest {
    @AfterEach
    fun tearDown() {
        KeyInterner.clear()
    }

    @Test
    fun `wraps new with intern and shares identity`() {
        val factoryName = "allyouneed/core/GeneratedFactory"
        val bytes = generateFactory(factoryName)
        val transformed = NewCallTransformer.apply(bytes, setOf(Type.getInternalName(SampleKey::class.java)))
        assertTrue(transformed !== bytes)
        val cn = ClassNode()
        ClassReader(transformed).accept(cn, 0)
        val make = cn.methods.first { it.name == "make" }
        val insns = make.instructions.toArray()
        assertTrue(insns.any { it.opcode == Opcodes.NEW })
        assertTrue(
            insns.any {
                it is MethodInsnNode && it.owner == NewCallTransformer.INTERNER_OWNER && it.name == "intern"
            },
        )

        val cls = ByteClassLoader(factoryName.replace('/', '.'), transformed).loadClass(factoryName.replace('/', '.'))
        val makeFn = cls.getMethod("make", String::class.java, Int::class.javaPrimitiveType)
        val a = makeFn.invoke(null, "zinc", 4)
        val b = makeFn.invoke(null, "zinc", 4)
        assertSame(a, b)
        assertEquals(SampleKey("zinc", 4), a)
    }

    @Test
    fun `wraps ternary constructor args`() {
        val factoryName = "allyouneed/core/GeneratedTernaryFactory"
        val transformed = NewCallTransformer.apply(
            generateTernaryFactory(factoryName),
            setOf(Type.getInternalName(SampleKey::class.java)),
        )
        val cls = ByteClassLoader(factoryName.replace('/', '.'), transformed).loadClass(factoryName.replace('/', '.'))
        val makeFn = cls.getMethod("make", String::class.java)
        val a = makeFn.invoke(null, "zinc")
        val b = makeFn.invoke(null, null)
        val c = makeFn.invoke(null, "zinc")
        val d = makeFn.invoke(null, null)
        assertSame(a, c)
        assertSame(b, d)
        assertEquals(SampleKey("zinc", 0), a)
        assertEquals(SampleKey("none", 0), b)
    }

    @Test
    fun `wraps new stored to local before init`() {
        val factoryName = "allyouneed/core/GeneratedStoreFactory"
        val transformed = NewCallTransformer.apply(
            generateStoreFactory(factoryName),
            setOf(Type.getInternalName(SampleKey::class.java)),
        )
        val cls = ByteClassLoader(factoryName.replace('/', '.'), transformed).loadClass(factoryName.replace('/', '.'))
        val makeFn = cls.getMethod("make", String::class.java)
        val a = makeFn.invoke(null, "zinc")
        val b = makeFn.invoke(null, "zinc")
        assertSame(a, b)
        assertEquals(SampleKey("zinc", 0), a)
    }

    @Test
    fun `wraps new with long constructor args`() {
        val factoryName = "allyouneed/core/GeneratedLongFactory"
        val transformed = NewCallTransformer.apply(
            generateLongFactory(factoryName),
            setOf(Type.getInternalName(SampleLongKey::class.java)),
        )
        val cls = ByteClassLoader(factoryName.replace('/', '.'), transformed).loadClass(factoryName.replace('/', '.'))
        val makeFn = cls.getMethod("make", Long::class.javaPrimitiveType)
        val a = makeFn.invoke(null, 7L)
        val b = makeFn.invoke(null, 7L)
        assertSame(a, b)
        assertEquals(SampleLongKey(7L, 0L), a)
    }

    @Test
    fun `retargets AEKey super and renames equals hashCode`() {
        val name = "allyouneed/core/GeneratedKey"
        val transformed = NewCallTransformer.apply(generateKey(name), setOf(name))
        val cn = ClassNode()
        ClassReader(transformed).accept(cn, 0)
        assertEquals(NewCallTransformer.AE_KEY_ASM, cn.superName)
        assertTrue(cn.methods.any { it.name == NewCallTransformer.ASM_EQUALS && it.desc == "(Ljava/lang/Object;)Z" })
        assertTrue(cn.methods.any { it.name == NewCallTransformer.ASM_HASH && it.desc == "()I" })
        assertTrue(cn.methods.none { it.name == "equals" && it.desc == "(Ljava/lang/Object;)Z" })
        assertTrue(cn.methods.none { it.name == "hashCode" && it.desc == "()I" })
        assertTrue(cn.methods.any { it.name == NewCallTransformer.ASM_DROP })
        assertTrue(cn.methods.none { it.name == NewCallTransformer.DROP_SECONDARY })
        val init = cn.methods.first { it.name == "<init>" }
        assertTrue(
            init.instructions.toArray().any {
                it is MethodInsnNode && it.opcode == Opcodes.INVOKESPECIAL &&
                    it.owner == NewCallTransformer.AE_KEY_ASM && it.name == "<init>"
            },
        )
    }

    private fun generateKey(internalName: String): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, NewCallTransformer.AE_KEY, null)
        cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, "name", "Ljava/lang/String;", null, null).visitEnd()
        val init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, NewCallTransformer.AE_KEY, "<init>", "()V", false)
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitVarInsn(Opcodes.ALOAD, 1)
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "name", "Ljava/lang/String;")
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(2, 2)
        init.visitEnd()
        val eq = cw.visitMethod(Opcodes.ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null)
        eq.visitCode()
        eq.visitVarInsn(Opcodes.ALOAD, 1)
        eq.visitTypeInsn(Opcodes.INSTANCEOF, internalName)
        val fail = org.objectweb.asm.Label()
        eq.visitJumpInsn(Opcodes.IFEQ, fail)
        eq.visitVarInsn(Opcodes.ALOAD, 0)
        eq.visitFieldInsn(Opcodes.GETFIELD, internalName, "name", "Ljava/lang/String;")
        eq.visitVarInsn(Opcodes.ALOAD, 1)
        eq.visitTypeInsn(Opcodes.CHECKCAST, internalName)
        eq.visitFieldInsn(Opcodes.GETFIELD, internalName, "name", "Ljava/lang/String;")
        eq.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        eq.visitInsn(Opcodes.IRETURN)
        eq.visitLabel(fail)
        eq.visitInsn(Opcodes.ICONST_0)
        eq.visitInsn(Opcodes.IRETURN)
        eq.visitMaxs(2, 2)
        eq.visitEnd()
        val hash = cw.visitMethod(Opcodes.ACC_PUBLIC, "hashCode", "()I", null, null)
        hash.visitCode()
        hash.visitVarInsn(Opcodes.ALOAD, 0)
        hash.visitFieldInsn(Opcodes.GETFIELD, internalName, "name", "Ljava/lang/String;")
        hash.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
        hash.visitInsn(Opcodes.IRETURN)
        hash.visitMaxs(1, 1)
        hash.visitEnd()
        val drop = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            NewCallTransformer.DROP_SECONDARY,
            "()L${NewCallTransformer.AE_KEY};",
            null,
            null,
        )
        drop.visitCode()
        drop.visitVarInsn(Opcodes.ALOAD, 0)
        drop.visitInsn(Opcodes.ARETURN)
        drop.visitMaxs(1, 1)
        drop.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun generateFactory(internalName: String): ByteArray {
        val key = Type.getInternalName(SampleKey::class.java)
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "make",
            "(Ljava/lang/String;I)L$key;",
            null,
            null,
        )
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, key)
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, key, "<init>", "(Ljava/lang/String;I)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(4, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun generateTernaryFactory(internalName: String): ByteArray {
        val key = Type.getInternalName(SampleKey::class.java)
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "make",
            "(Ljava/lang/String;)L$key;",
            null,
            null,
        )
        val notNull = Label()
        val join = Label()
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, key)
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull)
        mv.visitLdcInsn("none")
        mv.visitJumpInsn(Opcodes.GOTO, join)
        mv.visitLabel(notNull)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitLabel(join)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, key, "<init>", "(Ljava/lang/String;I)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(4, 1)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun generateStoreFactory(internalName: String): ByteArray {
        val key = Type.getInternalName(SampleKey::class.java)
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "make",
            "(Ljava/lang/String;)L$key;",
            null,
            null,
        )
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, key)
        mv.visitVarInsn(Opcodes.ASTORE, 1)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, key, "<init>", "(Ljava/lang/String;I)V", false)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(3, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun generateLongFactory(internalName: String): ByteArray {
        val key = Type.getInternalName(SampleLongKey::class.java)
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "make",
            "(J)L$key;",
            null,
            null,
        )
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, key)
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.LLOAD, 0)
        mv.visitInsn(Opcodes.LCONST_0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, key, "<init>", "(JJ)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(6, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private class ByteClassLoader(private val name: String, private val bytes: ByteArray) :
        ClassLoader(ByteClassLoader::class.java.classLoader) {
        override fun findClass(n: String): Class<*> {
            if (n == name) return defineClass(n, bytes, 0, bytes.size)
            return super.findClass(n)
        }
    }
}
