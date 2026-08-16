package allyouneed.forge.early

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import kotlin.test.assertTrue

class EarlyIsolationTest {
    @Test
    fun `early package does not reference AE2 or KeyInterner`() {
        for (cls in listOf(EarlyLoaderMod::class.java)) {
            val refs = classOwners(readClass(cls))
            assertTrue(refs.none { it.startsWith("appeng/") }, "$cls refs appeng: $refs")
            assertTrue("allyouneed/core/KeyInterner" !in refs, "$cls refs KeyInterner")
        }
    }

    private fun readClass(cls: Class<*>): ByteArray {
        val path = cls.name.replace('.', '/') + ".class"
        return cls.classLoader.getResourceAsStream(path)!!.readBytes()
    }

    private fun classOwners(bytes: ByteArray): Set<String> {
        val refs = HashSet<String>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?,
                ) {
                    superName?.let(refs::add)
                    interfaces?.forEach(refs::add)
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitTypeInsn(opcode: Int, type: String) {
                        refs.add(type)
                    }

                    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                        refs.add(owner)
                    }

                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        name: String,
                        descriptor: String,
                        isInterface: Boolean,
                    ) {
                        refs.add(owner)
                    }

                    override fun visitLdcInsn(value: Any?) {
                        if (value is Type && value.sort == Type.OBJECT) refs.add(value.internalName)
                    }
                }
            },
            0,
        )
        return refs
    }
}
