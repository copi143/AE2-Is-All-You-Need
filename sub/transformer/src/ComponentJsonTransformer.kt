package allyouneed.transformer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * 文本组件 JSON 流式化：
 * - `Component$Serializer` 的 `toJson(Component)`/`fromJson(String)`/`fromJsonLenient(String)`
 *   方法体整体替换为委托到 `allyouneed.gsonfast.ComponentJsonFast`（其内部含异常回退）。
 * - `Style$Serializer` 注入三个公开静态访问器，暴露 Style 的私有字段（五态布尔 flags 位掩码、
 *   原始 font 引用、按原版语义构造 Style），避免反射/模块访问问题。
 *
 * 特征缺失一律 no-op。
 */
object ComponentJsonTransformer {
    private const val STYLE = "net/minecraft/network/chat/Style"
    private const val STYLE_SERIALIZER = "$STYLE\$Serializer"
    private const val COMPONENT_SERIALIZER = "net/minecraft/network/chat/Component\$Serializer"
    private const val FAST = "allyouneed/gsonfast/ComponentJsonFast"
    private const val COMPONENT = "net/minecraft/network/chat/Component"
    private const val MUTABLE_COMPONENT = "net/minecraft/network/chat/MutableComponent"
    private const val TEXT_COLOR = "net/minecraft/network/chat/TextColor"
    private const val CLICK_EVENT = "net/minecraft/network/chat/ClickEvent"
    private const val HOVER_EVENT = "net/minecraft/network/chat/HoverEvent"
    private const val RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation"

    const val FLAGS_METHOD = "allyouneed\$rawStyleFlags"
    const val FONT_METHOD = "allyouneed\$rawFont"
    const val BUILD_METHOD = "allyouneed\$buildStyle"

    private val flagFields = arrayOf("bold", "italic", "underlined", "strikethrough", "obfuscated")

    fun isTarget(className: String): Boolean =
        className == COMPONENT_SERIALIZER || className == STYLE_SERIALIZER ||
            className == ComponentJsonNames.INTERMEDIARY_COMPONENT_SERIALIZER ||
            className == ComponentJsonNames.INTERMEDIARY_STYLE_SERIALIZER

    fun apply(cn: ClassNode): Boolean {
        val names = ComponentJsonNames.forClass(cn)
        return when (cn.name) {
            names.type(COMPONENT_SERIALIZER) -> applyComponentSerializer(cn, names)
            names.type(STYLE_SERIALIZER) -> applyStyleSerializer(cn, names)
            else -> false
        }
    }

    private fun applyComponentSerializer(cn: ClassNode, names: ComponentJsonNames): Boolean {
        val expected = mapOf(
            "toJson" to "(L$COMPONENT;)Ljava/lang/String;",
            "fromJson" to "(Ljava/lang/String;)L$MUTABLE_COMPONENT;",
            "fromJsonLenient" to "(Ljava/lang/String;)L$MUTABLE_COMPONENT;",
        )
        if (expected.any { (name, desc) -> cn.methods.none {
            it.name == names.method(name) && it.desc == names.descriptor(desc) && it.access and Opcodes.ACC_STATIC != 0
        } }) {
            logger.warn("component json: missing entry points in {}; leaving original methods intact", cn.name)
            return false
        }
        var done = 0
        for (mn in cn.methods) {
            val replacement: InsnList = when {
                mn.name == names.method("toJson") && mn.desc == names.descriptor(expected.getValue("toJson")) -> InsnList().apply {
                    add(VarInsnNode(Opcodes.ALOAD, 0))
                    add(MethodInsnNode(Opcodes.INVOKESTATIC, FAST, "toJson", "(L$COMPONENT;)Ljava/lang/String;", false))
                    add(InsnNode(Opcodes.ARETURN))
                }

                mn.name == names.method("fromJson") && mn.desc == names.descriptor(expected.getValue("fromJson")) -> InsnList().apply {
                    add(VarInsnNode(Opcodes.ALOAD, 0))
                    add(InsnNode(Opcodes.ICONST_0))
                    add(MethodInsnNode(Opcodes.INVOKESTATIC, FAST, "fromJson", "(Ljava/lang/String;Z)L$MUTABLE_COMPONENT;", false))
                    add(InsnNode(Opcodes.ARETURN))
                }

                mn.name == names.method("fromJsonLenient") && mn.desc == names.descriptor(expected.getValue("fromJsonLenient")) -> InsnList().apply {
                    add(VarInsnNode(Opcodes.ALOAD, 0))
                    add(InsnNode(Opcodes.ICONST_1))
                    add(MethodInsnNode(Opcodes.INVOKESTATIC, FAST, "fromJson", "(Ljava/lang/String;Z)L$MUTABLE_COMPONENT;", false))
                    add(InsnNode(Opcodes.ARETURN))
                }

                else -> continue
            }
            names.remap(replacement)
            mn.instructions = replacement
            mn.tryCatchBlocks?.clear()
            mn.localVariables?.clear()
            done++
        }
        if (done > 0) logger.info("replaced {} component json entry points in {}", done, cn.name.replace('/', '.'))
        if (done != 3) logger.warn("component json: expected 3 entry points, replaced {} in {}", done, cn.name.replace('/', '.'))
        return done > 0
    }

    private fun applyStyleSerializer(cn: ClassNode, names: ComponentJsonNames): Boolean {
        if (cn.methods.any { it.name == FLAGS_METHOD }) return false
        cn.methods.add(names.remap(buildFlagsMethod()))
        cn.methods.add(names.remap(buildFontMethod()))
        cn.methods.add(names.remap(buildStyleMethod()))
        logger.info("injected style field accessors into {}", cn.name.replace('/', '.'))
        return true
    }

    private fun buildFlagsMethod(): MethodNode {
        val mn = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, FLAGS_METHOD, "(L$STYLE;)I", null, null)
        val insns = mn.instructions
        insns.add(InsnNode(Opcodes.ICONST_0))
        insns.add(VarInsnNode(Opcodes.ISTORE, 1))
        flagFields.forEachIndexed { idx, field ->
            val presentBit = 1 shl (idx * 2)
            val valueBit = 2 shl (idx * 2)
            val skip = LabelNode()
            val falsy = LabelNode()
            insns.add(VarInsnNode(Opcodes.ALOAD, 0))
            insns.add(FieldInsnNode(Opcodes.GETFIELD, STYLE, field, "Ljava/lang/Boolean;"))
            insns.add(JumpInsnNode(Opcodes.IFNULL, skip))
            insns.add(VarInsnNode(Opcodes.ILOAD, 1))
            insns.add(org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, presentBit))
            insns.add(InsnNode(Opcodes.IOR))
            insns.add(VarInsnNode(Opcodes.ISTORE, 1))
            insns.add(VarInsnNode(Opcodes.ALOAD, 0))
            insns.add(FieldInsnNode(Opcodes.GETFIELD, STYLE, field, "Ljava/lang/Boolean;"))
            insns.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false))
            insns.add(JumpInsnNode(Opcodes.IFEQ, falsy))
            insns.add(VarInsnNode(Opcodes.ILOAD, 1))
            insns.add(org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, valueBit))
            insns.add(InsnNode(Opcodes.IOR))
            insns.add(VarInsnNode(Opcodes.ISTORE, 1))
            insns.add(falsy)
            insns.add(skip)
        }
        insns.add(VarInsnNode(Opcodes.ILOAD, 1))
        insns.add(InsnNode(Opcodes.IRETURN))
        mn.maxStack = 3
        mn.maxLocals = 2
        return mn
    }

    private fun buildFontMethod(): MethodNode {
        val mn = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, FONT_METHOD, "(L$STYLE;)L$RESOURCE_LOCATION;", null, null)
        mn.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        mn.instructions.add(FieldInsnNode(Opcodes.GETFIELD, STYLE, "font", "L$RESOURCE_LOCATION;"))
        mn.instructions.add(InsnNode(Opcodes.ARETURN))
        mn.maxStack = 1
        mn.maxLocals = 1
        return mn
    }

    private fun buildStyleMethod(): MethodNode {
        val desc = "(L$TEXT_COLOR;IL$CLICK_EVENT;L$HOVER_EVENT;Ljava/lang/String;L$RESOURCE_LOCATION;)L$STYLE;"
        val mn = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, BUILD_METHOD, desc, null, null)
        val insns = mn.instructions
        insns.add(org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, STYLE))
        insns.add(InsnNode(Opcodes.DUP))
        insns.add(VarInsnNode(Opcodes.ALOAD, 0))
        for (idx in 0 until 5) {
            val presentBit = 1 shl (idx * 2)
            val valueBit = 2 shl (idx * 2)
            val absent = LabelNode()
            val falsy = LabelNode()
            val done = LabelNode()
            insns.add(VarInsnNode(Opcodes.ILOAD, 1))
            insns.add(org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, presentBit))
            insns.add(InsnNode(Opcodes.IAND))
            insns.add(JumpInsnNode(Opcodes.IFEQ, absent))
            insns.add(VarInsnNode(Opcodes.ILOAD, 1))
            insns.add(org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, valueBit))
            insns.add(InsnNode(Opcodes.IAND))
            insns.add(JumpInsnNode(Opcodes.IFEQ, falsy))
            insns.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;"))
            insns.add(JumpInsnNode(Opcodes.GOTO, done))
            insns.add(falsy)
            insns.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;"))
            insns.add(JumpInsnNode(Opcodes.GOTO, done))
            insns.add(absent)
            insns.add(InsnNode(Opcodes.ACONST_NULL))
            insns.add(done)
        }
        insns.add(VarInsnNode(Opcodes.ALOAD, 2))
        insns.add(VarInsnNode(Opcodes.ALOAD, 3))
        insns.add(VarInsnNode(Opcodes.ALOAD, 4))
        insns.add(VarInsnNode(Opcodes.ALOAD, 5))
        insns.add(
            MethodInsnNode(
                Opcodes.INVOKESPECIAL, STYLE, "<init>",
                "(L$TEXT_COLOR;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;Ljava/lang/Boolean;L$CLICK_EVENT;L$HOVER_EVENT;Ljava/lang/String;L$RESOURCE_LOCATION;)V",
                false,
            ),
        )
        insns.add(InsnNode(Opcodes.ARETURN))
        mn.maxStack = 12
        mn.maxLocals = 6
        return mn
    }
}
