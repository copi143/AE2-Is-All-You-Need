package allyouneed.transformer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * 通用 JSON 序列化流式化（serialize 侧 POC）。
 *
 * 对声明的入口方法（每格式一行，见 [ENTRIES]）做乐观委托者改写：
 * 方法体内的 `new JsonObject()` 全部换成 `new StreamJsonObject(tag)`，add/addProperty
 * 调用按同名同描述符换 owner。写出经 `Streams.write` 的方法头钩子分流到
 * `StreamJsonObject.writeHook`；树消费路径由 StreamJsonObject 自身物化兜底，
 * 本变换器不需要在方法内保留回退分支。
 *
 * 白名单约束（任一不满足整个方法跳过）：
 * - 方法返回类型必须是 JsonElement（委托者不是 JsonObject 子类）；
 * - 方法描述符中不得出现 JsonObject（排除外部流入的实例，保证所有 JsonObject 接收者
 *   都源自方法内的 NEW 点）；
 * - JsonObject 只允许：NEW、<init>()V、add、四种 addProperty；出现其它任何以
 *   JsonObject 为 owner/参数/返回值/字段类型的指令即放弃。
 */
object JsonStreamTransformer {
    private const val JSON_OBJECT = "com/google/gson/JsonObject"
    private const val JSON_OBJECT_TYPE = "Lcom/google/gson/JsonObject;"
    private const val JSON_ELEMENT_TYPE = "Lcom/google/gson/JsonElement;"
    private const val SJO = "com/google/gson/internal/bind/StreamJsonObject"
    private const val STRING_TYPE = "Ljava/lang/String;"

    private val ENABLED = System.getProperty("allyouneed.jsonstream") != "false"

    private const val ADD_DESC = "(Ljava/lang/String;Lcom/google/gson/JsonElement;)V"
    private val ADD_PROPERTY = setOf(
        "addProperty(Ljava/lang/String;Ljava/lang/String;)V",
        "addProperty(Ljava/lang/String;Ljava/lang/Number;)V",
        "addProperty(Ljava/lang/String;Ljava/lang/Boolean;)V",
        "addProperty(Ljava/lang/String;Ljava/lang/Character;)V",
    )

    /**
     * 每格式一行：owner#method desc。
     *
     * 当前为空：1.20.1 原版没有热的 Gson 树序列化路径——ServerStatus/进度包都已是
     * Codec/二进制，唯一的热路径 Component 已由手写流式化（ComponentJsonFast）覆盖。
     * 机制本身（委托者 + Streams 钩子 + 熔断）保持武装，发现符合白名单形状的
     * serialize 方法时按行添加即可（mod 的 JsonSerializer 实现是典型候选）。
     */
    private val ENTRIES = listOf<String>()

    fun isTarget(className: String): Boolean =
        className == Constants.GSON_STREAMS || ENTRIES.any { it.substringBefore('#') == className }

    fun apply(cn: ClassNode): Boolean = apply(cn, ENTRIES)

    internal fun apply(cn: ClassNode, entries: List<String>): Boolean {
        if (!ENABLED) return false
        if (cn.name == Constants.GSON_STREAMS) return applyStreamsHook(cn)
        var done = 0
        for (entry in entries) {
            val owner = entry.substringBefore('#')
            if (owner != cn.name) continue
            val name = entry.substringAfter('#').substringBefore(' ')
            val desc = entry.substringAfter(' ')
            val mn = cn.methods.firstOrNull { it.name == name && it.desc == desc } ?: continue
            if (transformMethod(cn, mn)) done++
        }
        if (done > 0) logger.info("stream-rewrote {} serializer methods in {}", done, cn.name.replace('/', '.'))
        return done > 0
    }

    /**
     * `com.google.gson.internal.Streams.write(JsonElement, JsonWriter)` 方法头插入：
     * `if (StreamJsonObject.writeHook(e, w)) return;`
     * gson 2.10 里所有树的写出（TreeTypeAdapter、Gson.toJson(JsonElement)、
     * JsonElement.toString）都汇聚于这一个方法，单点钩子全覆盖。
     */
    fun applyStreamsHook(cn: ClassNode): Boolean {
        if (cn.name != Constants.GSON_STREAMS) return false
        val mn = cn.methods.firstOrNull {
            it.name == "write" && it.desc == "(Lcom/google/gson/JsonElement;Lcom/google/gson/stream/JsonWriter;)V"
        } ?: return miss("Streams.write")
        val head = mn.instructions.first ?: return miss("Streams.write body")
        val orig = LabelNode()
        val hook = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(
                MethodInsnNode(
                    Opcodes.INVOKESTATIC, SJO, "writeHook",
                    "(Lcom/google/gson/JsonElement;Lcom/google/gson/stream/JsonWriter;)Z",
                    false,
                ),
            )
            add(JumpInsnNode(Opcodes.IFEQ, orig))
            add(InsnNode(Opcodes.RETURN))
            add(orig)
        }
        mn.instructions.insertBefore(head, hook)
        logger.info("installed json stream hook into Streams.write")
        return true
    }

    private fun transformMethod(cn: ClassNode, mn: MethodNode): Boolean {
        if (!mn.desc.endsWith(")$JSON_ELEMENT_TYPE")) return miss("${cn.name}.${mn.name}: return type")
        if (mn.desc.contains(JSON_OBJECT_TYPE)) return miss("${cn.name}.${mn.name}: JsonObject in descriptor")
        val news = ArrayList<TypeInsnNode>()
        val inits = ArrayList<MethodInsnNode>()
        val calls = ArrayList<MethodInsnNode>()
        for (insn in mn.instructions) {
            when (insn) {
                is TypeInsnNode -> when {
                    insn.opcode == Opcodes.NEW && insn.desc == JSON_OBJECT -> news.add(insn)
                    insn.desc.containsJsonObject() -> return miss("${cn.name}.${mn.name}: ${insn.opcode} JsonObject")
                }

                is MethodInsnNode -> when {
                    insn.owner == JSON_OBJECT && insn.opcode == Opcodes.INVOKESPECIAL &&
                        insn.name == "<init>" && insn.desc == "()V" -> inits.add(insn)

                    insn.owner == JSON_OBJECT && insn.opcode == Opcodes.INVOKEVIRTUAL &&
                        insn.name == "add" && insn.desc == ADD_DESC -> calls.add(insn)

                    insn.owner == JSON_OBJECT && insn.opcode == Opcodes.INVOKEVIRTUAL &&
                        ADD_PROPERTY.contains(insn.name + insn.desc) -> calls.add(insn)

                    insn.owner == JSON_OBJECT || insn.desc.containsJsonObject() ->
                        return miss("${cn.name}.${mn.name}: call ${insn.owner}.${insn.name}${insn.desc}")
                }

                is FieldInsnNode ->
                    if (insn.owner == JSON_OBJECT || insn.desc.containsJsonObject()) {
                        return miss("${cn.name}.${mn.name}: field ${insn.owner}.${insn.name}")
                    }

                is LdcInsnNode ->
                    if (insn.cst is Type && (insn.cst as Type).descriptor.containsJsonObject()) {
                        return miss("${cn.name}.${mn.name}: ldc JsonObject")
                    }

                is MultiANewArrayInsnNode ->
                    if (insn.desc.containsJsonObject()) return miss("${cn.name}.${mn.name}: multianewarray JsonObject")
            }
        }
        if (news.isEmpty() || inits.size != news.size) return miss("${cn.name}.${mn.name}: JsonObject construction")

        val tag = cn.name.replace('/', '.') + "." + mn.name
        for (new in news) new.desc = SJO
        for (init in inits) {
            init.owner = SJO
            init.desc = "(Ljava/lang/String;)V"
            mn.instructions.insertBefore(init, LdcInsnNode(tag))
        }
        for (call in calls) call.owner = SJO
        mn.localVariables?.clear()
        return true
    }

    private fun String.containsJsonObject(): Boolean = contains(JSON_OBJECT_TYPE)

    private fun miss(what: String): Boolean {
        logger.warn("json stream: skipping {} (whitelist mismatch)", what)
        return false
    }
}
