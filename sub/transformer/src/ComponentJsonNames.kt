package allyouneed.transformer

import org.objectweb.asm.tree.*

/** MC 1.20.1 names. These strings are not remapped by Loom or Forge's reobfuscator. */
internal class ComponentJsonNames(
    private val classes: Map<String, String> = emptyMap(),
    private val methods: Map<String, String> = emptyMap(),
    private val fields: Map<String, String> = emptyMap(),
) {
    fun type(name: String): String = classes[name] ?: name
    fun method(name: String): String = methods[name] ?: name
    fun descriptor(desc: String): String = classes.entries.fold(desc) { value, (from, to) ->
        value.replace("L$from;", "L$to;")
    }

    fun remap(method: MethodNode): MethodNode {
        method.desc = descriptor(method.desc)
        remap(method.instructions)
        return method
    }

    fun remap(insns: InsnList) {
        for (insn in insns) when (insn) {
            is FieldInsnNode -> {
                if (insn.owner == STYLE) insn.name = fields[insn.name] ?: insn.name
                insn.owner = type(insn.owner)
                insn.desc = descriptor(insn.desc)
            }
            is MethodInsnNode -> {
                insn.owner = type(insn.owner)
                insn.desc = descriptor(insn.desc)
            }
            is TypeInsnNode -> insn.desc = type(insn.desc)
        }
    }

    companion object {
        const val INTERMEDIARY_COMPONENT_SERIALIZER = "net/minecraft/class_2561\$class_2562"
        const val INTERMEDIARY_STYLE_SERIALIZER = "net/minecraft/class_2583\$class_2584"
        private const val CHAT = "net/minecraft/network/chat/"
        private const val STYLE = CHAT + "Style"
        val NAMED = ComponentJsonNames()
        val SRG = ComponentJsonNames(
            methods = mapOf("toJson" to "m_130703_", "fromJson" to "m_130701_", "fromJsonLenient" to "m_130714_"),
            fields = mapOf(
                "bold" to "f_131102_", "italic" to "f_131103_", "underlined" to "f_131104_",
                "strikethrough" to "f_131105_", "obfuscated" to "f_131106_", "font" to "f_131110_",
            ),
        )
        val INTERMEDIARY = ComponentJsonNames(
            classes = mapOf(
                CHAT + "Component" to "net/minecraft/class_2561",
                CHAT + "Component\$Serializer" to INTERMEDIARY_COMPONENT_SERIALIZER,
                CHAT + "MutableComponent" to "net/minecraft/class_5250",
                STYLE to "net/minecraft/class_2583",
                STYLE + "\$Serializer" to INTERMEDIARY_STYLE_SERIALIZER,
                CHAT + "TextColor" to "net/minecraft/class_5251",
                CHAT + "ClickEvent" to "net/minecraft/class_2558",
                CHAT + "HoverEvent" to "net/minecraft/class_2568",
                "net/minecraft/resources/ResourceLocation" to "net/minecraft/class_2960",
            ),
            methods = mapOf("toJson" to "method_10867", "fromJson" to "method_10877", "fromJsonLenient" to "method_10873"),
            fields = mapOf(
                "bold" to "field_11856", "italic" to "field_11852", "underlined" to "field_11851",
                "strikethrough" to "field_11857", "obfuscated" to "field_11861", "font" to "field_24361",
            ),
        )

        fun forClass(cn: ClassNode): ComponentJsonNames = when {
            cn.name.startsWith("net/minecraft/class_") -> INTERMEDIARY
            cn.methods.any { it.name == "m_130703_" || it.instructions.any { insn ->
                insn is FieldInsnNode && insn.owner == STYLE && insn.name == "f_131102_"
            } } -> SRG
            else -> NAMED
        }
    }
}
