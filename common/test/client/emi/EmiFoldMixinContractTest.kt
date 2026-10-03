package allyouneed.client.integration.emi.fold

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 检查真实 EMI jar 的私有注入点和 NEW 捕获参数，避免仅编译通过却指向错误签名。 */
class EmiFoldMixinContractTest {
    @Test
    fun `当前 EMI 的布局和交互签名符合 Mixin 契约`() {
        val file = System.getenv("AE2INYA_EMI_JAR")?.let(Path::of)
        assumeTrue(file != null && Files.isRegularFile(file), "未指定本地 EMI jar")
        JarFile(file!!.toFile()).use { jar ->
            fun read(name: String): ClassNode = ClassNode().also { node ->
                jar.getInputStream(jar.getJarEntry("$name.class")).use { ClassReader(it).accept(node, 0) }
            }
            val manager = "dev/emi/emi/screen/EmiScreenManager"
            val panel = "$manager\$SidebarPanel"
            val space = "$manager\$ScreenSpace"
            val managerClass = read(manager)
            assertTrue(managerClass.fields.any { it.name == "lastWidth" && it.desc == "I" })
            assertTrue(managerClass.methods.any { it.name == "mouseClicked" && it.desc == "(DDI)Z" })
            val layout = managerClass.methods.single { it.name == "createScreenSpace" }
            val constructors = layout.instructions.toArray().filterIsInstance<MethodInsnNode>().filter { it.owner == space && it.name == "<init>" }
            assertEquals(2, constructors.size)
            assertEquals("(IIIIZLjava/util/List;Ljava/util/function/Supplier;Z)V", constructors.first().desc)
            val handler = ClassNode().also { node ->
                javaClass.classLoader.getResourceAsStream("allyouneed/mixin/emi/EmiScreenManagerLayoutMixin.class")!!.use {
                    ClassReader(it).accept(node, 0)
                }
            }.methods.single { it.name == "ae2inya\$navigationSpace" }
            assertEquals((Type.getArgumentTypes(constructors.first().desc) + Type.getArgumentTypes(layout.desc)).toList(),
                Type.getArgumentTypes(handler.desc).toList())
            assertEquals("L$space;", Type.getReturnType(handler.desc).descriptor)
            val panelClass = read(panel)
            assertTrue(panelClass.methods.any { it.name == "render" && it.desc == "(Ldev/emi/emi/runtime/EmiDrawContext;IIF)V" })
            assertTrue(panelClass.methods.any { it.name == "setSidebarPage" && it.desc == "(I)V" })
            assertTrue(panelClass.methods.any { it.name == "getBounds" && it.desc == "()Ldev/emi/emi/api/widget/Bounds;" })
            val background = panelClass.methods.single { it.name == "drawBackground" }
            val ninePatch = background.instructions.toArray().filterIsInstance<MethodInsnNode>().single {
                it.owner == "dev/emi/emi/EmiRenderHelper" && it.name == "drawNinePatch"
            }
            assertEquals("(Ldev/emi/emi/runtime/EmiDrawContext;Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V", ninePatch.desc)
            val navigation = ClassNode().also { node ->
                javaClass.classLoader.getResourceAsStream("allyouneed/mixin/emi/EmiSidebarNavigationMixin.class")!!.use {
                    ClassReader(it).accept(node, 0)
                }
            }
            val argumentIndices = navigation.methods.mapNotNull { method ->
                val annotations = method.visibleAnnotations.orEmpty() + method.invisibleAnnotations.orEmpty()
                // 运行日志证明此环境无法加载 ModifyArgs 生成的 Args$N 类。
                assertFalse(annotations.any { it.desc == "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;" })
                val annotation = annotations.singleOrNull { it.desc == "Lorg/spongepowered/asm/mixin/injection/ModifyArg;" }
                    ?: return@mapNotNull null
                val values = annotation.values.chunked(2).associate { it[0] as String to it[1] }
                val index = values.getValue("index") as Int
                assertEquals("(I)I", method.desc)
                assertEquals(Type.INT_TYPE, Type.getArgumentTypes(ninePatch.desc)[index])
                index
            }
            assertEquals(setOf(3, 5), argumentIndices.toSet())
            assertTrue(read(space).methods.any { it.name == "getStacks" && it.desc == "()Ljava/util/List;" })
        }
    }
}
