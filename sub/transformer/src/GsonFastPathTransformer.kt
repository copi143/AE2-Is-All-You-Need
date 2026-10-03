package allyouneed.transformer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * 向 `com.google.gson.internal.bind.ReflectiveTypeAdapterFactory.create(Gson, TypeToken)`
 * 返回 `FieldReflectionAdapter` 的位置插入 [Constants.GSON_FAST_PATH_HOOK] `.wrap(...)` 调用，
 * 由其决定返回生成的专用适配器还是原反射适配器（任何异常都在 wrap 内部回退）。
 *
 * 只依赖方法调用结构特征（getRawType / ConstructorConstructor.get /
 * FieldReflectionAdapter.<init> 及其后的 ARETURN），局部变量槽位全部从字节码推导；
 * 特征任一缺失都 no-op，保证 gson 版本变动时静默回退。
 */
object GsonFastPathTransformer {
    const val HOOK_MARKER = "allyouneed\$gsonFastPathHooked"
    private const val CREATE_DESC = "(Lcom/google/gson/Gson;Lcom/google/gson/reflect/TypeToken;)Lcom/google/gson/TypeAdapter;"
    private const val GET_RAW_TYPE = "com/google/gson/reflect/TypeToken"
    private const val CTOR_CTOR = "com/google/gson/internal/ConstructorConstructor"
    private const val FRA = Constants.GSON_RTAF + "\$FieldReflectionAdapter"
    private const val WRAP_DESC =
        "(Lcom/google/gson/TypeAdapter;Lcom/google/gson/Gson;Ljava/lang/Class;Lcom/google/gson/internal/ObjectConstructor;)Lcom/google/gson/TypeAdapter;"
    private const val GSON = "com/google/gson/Gson"
    private const val GSON_BUILDER = "com/google/gson/GsonBuilder"
    private const val GSON_CREATE_DESC = "()Lcom/google/gson/Gson;"
    private const val GSON_CREATE_HOOK_DESC = "(Lcom/google/gson/GsonBuilder;)Lcom/google/gson/Gson;"
    private const val GSON_NEW_HOOK_DESC = "()Lcom/google/gson/Gson;"

    fun apply(cn: ClassNode): Boolean {
        if (cn.name != Constants.GSON_RTAF) return false
        if (cn.fields.any { it.name == HOOK_MARKER }) return false
        val create = cn.methods.firstOrNull { it.name == "create" && it.desc == CREATE_DESC } ?: return miss("create")
        val insns = create.instructions.toArray()

        val rawLocal = localAfterStore(insns) { it is MethodInsnNode && it.owner == GET_RAW_TYPE && it.name == "getRawType" }
            ?: return miss("rawType local")
        val ctorLocal = localAfterStore(insns) { it is MethodInsnNode && it.owner == CTOR_CTOR && it.name == "get" }
            ?: return miss("constructor local")
        val initIndex = insns.indexOfFirst {
            it is MethodInsnNode && it.opcode == Opcodes.INVOKESPECIAL && it.owner == FRA && it.name == "<init>"
        }
        if (initIndex < 0) return miss("FieldReflectionAdapter construction")
        var ret: AbstractInsnNode? = insns[initIndex].next
        while (ret != null && ret.opcode != Opcodes.ARETURN) ret = ret.next
        if (ret == null) return miss("adapter return")

        val hook = InsnList()
        hook.add(VarInsnNode(Opcodes.ALOAD, 1))
        hook.add(VarInsnNode(Opcodes.ALOAD, rawLocal))
        hook.add(VarInsnNode(Opcodes.ALOAD, ctorLocal))
        hook.add(MethodInsnNode(Opcodes.INVOKESTATIC, Constants.GSON_FAST_PATH_HOOK, "wrap", WRAP_DESC, false))
        create.instructions.insertBefore(ret, hook)
        cn.fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
            HOOK_MARKER, "Z", null, 1))
        logger.info("installed gson fast path hook into ReflectiveTypeAdapterFactory.create")
        return true
    }

    /**
     * Forge 场景的替代入口：gson 在 BOOT 层不可变换，改写 GAME 层调用点：
     * - `GsonBuilder.create()` → `GsonFastPath.create(builder)`
     * - `new Gson()`（标准 NEW/DUP/<init> 三指令形态）→ `GsonFastPath.newGson()`
     * 两者在构造后把反射工厂包装为生成适配器工厂；不匹配标准形态的 `new Gson` 直接跳过。
     */
    fun applyCallSites(cn: ClassNode): Int {
        if (cn.name.startsWith("com/google/gson/")) return 0
        var rewritten = 0
        for (mn in cn.methods) {
            val list = mn.instructions ?: continue
            var insn = list.first
            while (insn != null) {
                var next = insn.next
                if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKEVIRTUAL &&
                    insn.owner == GSON_BUILDER && insn.name == "create" && insn.desc == GSON_CREATE_DESC
                ) {
                    list.set(insn, MethodInsnNode(Opcodes.INVOKESTATIC, Constants.GSON_FAST_PATH_HOOK, "create", GSON_CREATE_HOOK_DESC, false))
                    rewritten++
                } else if (insn is TypeInsnNode && insn.opcode == Opcodes.NEW && insn.desc == GSON) {
                    val dup = insn.next
                    val init = dup?.next
                    if (dup != null && dup.opcode == Opcodes.DUP &&
                        init is MethodInsnNode && init.opcode == Opcodes.INVOKESPECIAL &&
                        init.owner == GSON && init.name == "<init>" && init.desc == "()V"
                    ) {
                        next = init.next
                        list.remove(dup)
                        list.remove(insn)
                        list.set(init, MethodInsnNode(Opcodes.INVOKESTATIC, Constants.GSON_FAST_PATH_HOOK, "newGson", GSON_NEW_HOOK_DESC, false))
                        rewritten++
                    }
                }
                insn = next
            }
        }
        if (rewritten > 0) {
            logger.info("rewrote {} gson construction sites in {}", rewritten, cn.name.replace('/', '.'))
        }
        return rewritten
    }

    private fun localAfterStore(insns: Array<AbstractInsnNode>, pred: (AbstractInsnNode) -> Boolean): Int? {
        for (i in insns.indices) {
            if (!pred(insns[i])) continue
            var n = insns[i].next
            while (n != null && n.opcode < 0) n = n.next
            if (n is VarInsnNode && n.opcode == Opcodes.ASTORE) return n.`var`
            return null
        }
        return null
    }

    private fun miss(what: String): Boolean {
        logger.warn("gson fast path hook not installed: {} not found; gson internals changed?", what)
        return false
    }
}
