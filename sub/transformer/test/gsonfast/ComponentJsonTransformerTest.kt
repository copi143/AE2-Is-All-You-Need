package gsonfast

import allyouneed.transformer.ComponentJsonTransformer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*

class ComponentJsonTransformerTest {
    private data class Names(
        val component: String, val serializer: String, val mutable: String,
        val style: String, val styleSerializer: String, val font: String,
        val methods: List<String>, val fields: List<String>,
    )

    private val named = Names(
        "net/minecraft/network/chat/Component", "net/minecraft/network/chat/Component\$Serializer",
        "net/minecraft/network/chat/MutableComponent", "net/minecraft/network/chat/Style",
        "net/minecraft/network/chat/Style\$Serializer", "net/minecraft/resources/ResourceLocation",
        listOf("toJson", "fromJson", "fromJsonLenient"),
        listOf("bold", "italic", "underlined", "strikethrough", "obfuscated", "font"),
    )
    private val profiles = listOf(
        named,
        named.copy(
            methods = listOf("m_130703_", "m_130701_", "m_130714_"),
            fields = listOf("f_131102_", "f_131103_", "f_131104_", "f_131105_", "f_131106_", "f_131110_"),
        ),
        Names(
            "net/minecraft/class_2561", "net/minecraft/class_2561\$class_2562", "net/minecraft/class_5250",
            "net/minecraft/class_2583", "net/minecraft/class_2583\$class_2584", "net/minecraft/class_2960",
            listOf("method_10867", "method_10877", "method_10873"),
            listOf("field_11856", "field_11852", "field_11851", "field_11857", "field_11861", "field_24361"),
        ),
    )

    private fun componentClass(names: Names) = ClassNode().apply {
        name = names.serializer
        for ((index, method) in names.methods.withIndex()) {
            methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, method,
                if (index == 0) "(L${names.component};)Ljava/lang/String;" else "(Ljava/lang/String;)L${names.mutable};", null, null).apply {
                instructions.add(InsnNode(ACONST_NULL))
                instructions.add(InsnNode(ARETURN))
            })
        }
    }

    @Test
    fun allRuntimeNamespacesDelegateWithMatchingDescriptors() {
        for (names in profiles) {
            val cn = componentClass(names)
            assertTrue(ComponentJsonTransformer.isTarget(cn.name))
            assertTrue(ComponentJsonTransformer.apply(cn))
            for ((index, method) in cn.methods.withIndex()) {
                val call = method.instructions.filterIsInstance<MethodInsnNode>().single()
                assertEquals("allyouneed/gsonfast/ComponentJsonFast", call.owner)
                assertEquals(if (index == 0) "toJson" else "fromJson", call.name)
                assertEquals(if (index == 0) method.desc else "(Ljava/lang/String;Z)L${names.mutable};", call.desc)
            }
            val style = ClassNode().apply {
                name = names.styleSerializer
                methods.add(MethodNode(ACC_PUBLIC, "serialize", "()V", null, null).apply {
                    instructions.add(FieldInsnNode(GETFIELD, names.style, names.fields[0], "Ljava/lang/Boolean;"))
                })
            }
            assertTrue(ComponentJsonTransformer.isTarget(style.name))
            assertTrue(ComponentJsonTransformer.apply(style))
            val injected = style.methods.filter { it.name.startsWith("allyouneed$") }
            assertEquals(3, injected.size)
            val fields = injected.flatMap { it.instructions.filterIsInstance<FieldInsnNode>() }.filter { it.owner == names.style }
            assertEquals(names.fields.toSet(), fields.map { it.name }.toSet())
            assertTrue(injected.all { it.desc.contains("L${names.style};") })
            assertEquals("(L${names.style};)L${names.font};", injected.single { it.name == ComponentJsonTransformer.FONT_METHOD }.desc)
            assertFalse(ComponentJsonTransformer.apply(style), "injection must be idempotent")
        }
    }

    @Test
    fun unknownMethodLayoutIsLeftIntact() {
        val cn = componentClass(named)
        cn.methods.last().name = "unknown"
        assertFalse(ComponentJsonTransformer.apply(cn))
        assertTrue(cn.methods.all { it.instructions.none { insn -> insn is MethodInsnNode } })
    }
}
