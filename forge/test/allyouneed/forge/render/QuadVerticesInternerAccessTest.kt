package allyouneed.forge.render

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 重现 Mixin 方法移入 GTCEu 包之后的真实跨包调用，同时禁止普通类从 Mixin 包加载。 */
class QuadVerticesInternerAccessTest {
    @Test
    fun `GTCEu 包中的调用者能加载去重池并共享相同顶点`() {
        val resources = javaClass.classLoader
        val mixin = ClassNode().also { node ->
            resources.getResourceAsStream("allyouneed/mixin/gtceu/StaticFaceBakeryMixin.class")!!.use {
                ClassReader(it).accept(node, 0)
            }
        }
        // 从实际编译后的注入方法提取调用，避免测试只验证一个另写的正确包名。
        val call = mixin.methods.single { it.name == "allyouneed\$dedupVertices" }
            .instructions.toArray().filterIsInstance<MethodInsnNode>()
            .single { it.name == "intern" && it.desc == "([I)[I" }
        val helper = call.owner.replace('/', '.')
        val caller = "com/gregtechceu/gtceu/client/util/InternerAccessProbe"
        val bytecode = ClassWriter(0).apply {
            visit(Opcodes.V17, Opcodes.ACC_PUBLIC, caller, null, "java/lang/Object", null)
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "invoke", "([I)[I", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(call.opcode, call.owner, call.name, call.desc, call.itf)
                visitInsn(Opcodes.ARETURN)
                visitMaxs(1, 1)
                visitEnd()
            }
            visitEnd()
        }.toByteArray()
        val loader = object : ClassLoader(resources) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("allyouneed.mixin.")) throw ClassNotFoundException("普通类不能从 Mixin 包加载：$name")
                if (name == helper || name.startsWith("$helper\$")) {
                    synchronized(getClassLoadingLock(name)) {
                        val loaded = findLoadedClass(name) ?: resources.getResourceAsStream(name.replace('.', '/') + ".class")!!.use {
                            val bytes = it.readBytes()
                            defineClass(name, bytes, 0, bytes.size)
                        }
                        if (resolve) resolveClass(loaded)
                        return loaded
                    }
                }
                return super.loadClass(name, resolve)
            }

            fun callerClass(): Class<*> = defineClass(caller.replace('/', '.'), bytecode, 0, bytecode.size)
        }
        val invoke = loader.callerClass().getMethod("invoke", IntArray::class.java)
        val vertices = IntArray(32) { it * 17 }
        val first = invoke.invoke(null, vertices) as IntArray
        assertSame(vertices, first)
        assertSame(first, invoke.invoke(null, vertices.copyOf()))
        assertNotSame(first, invoke.invoke(null, vertices.copyOf().also { it[0]++ }))
        assertNull(invoke.invoke(null, *arrayOf<Any?>(null)))
    }
}
