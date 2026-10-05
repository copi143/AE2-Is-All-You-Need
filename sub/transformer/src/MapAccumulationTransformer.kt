package allyouneed.transformer

import allyouneed.transformer.analysis.ValueOrigins
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.AnalyzerException

/** Experimental library-semantic optimization, independent of mod class/method names. */
object MapAccumulationTransformer {
    val enabled: Boolean = System.getProperty("allyouneed.mapaccumulation") == "true"
    private const val MAP = "it/unimi/dsi/fastutil/objects/Object2IntOpenHashMap"
    private const val MAP_INTERFACE = "it/unimi/dsi/fastutil/objects/Object2IntMap"
    private const val SUPPORT = "it/unimi/dsi/fastutil/objects/MapAccumulationSupport"
    private val operators = setOf("it/unimi/dsi/fastutil/ints/IntBinaryOperator", "java/util/function/IntBinaryOperator", "java/util/function/BiFunction")
    private val bootstrap = Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false)

    fun apply(cn: ClassNode): Int = if (enabled) rewrite(cn) else 0

    internal fun rewrite(cn: ClassNode): Int {
        if (cn.name.startsWith("it/unimi/dsi/fastutil/") || cn.name.startsWith("allyouneed/transformer/") ||
            cn.access and (ACC_INTERFACE or ACC_ANNOTATION) != 0) return 0
        var total = 0
        for (method in cn.methods) {
            // Keep constructors and class initialization outside this prototype's scope.
            if (method.name == "<init>" || method.name == "<clinit>") continue
            val calls = method.instructions.filterIsInstance<MethodInsnNode>().filter { call ->
                call.name == "mergeInt" && (call.owner == MAP || call.owner == MAP_INTERFACE) &&
                    call.opcode in setOf(INVOKEVIRTUAL, INVOKEINTERFACE) &&
                    operators.any { call.desc == "(Ljava/lang/Object;IL$it;)I" }
            }
            if (calls.isEmpty()) continue
            // Detect our own guards without adding fields or changing the caller's schema.
            if (method.instructions.any { it is MethodInsnNode && it.owner == SUPPORT }) continue
            val frames = try { ValueOrigins.analyze(cn.name, method) } catch (_: AnalyzerException) { null } ?: continue
            val accepted = calls.filter { call ->
                val frame = frames[method.instructions.indexOf(call)] ?: return@filter false
                if (frame.stackSize < 4) return@filter false
                val origins = frame.getStack(frame.stackSize - 1).origins ?: return@filter false
                origins.isNotEmpty() && origins.all { isIntegerSum(it, Type.getArgumentTypes(call.desc).last().internalName) }
            }
            if (accepted.isEmpty()) continue
            // Same scratch slots can be reused by every rewritten site in this method.
            val local = method.maxLocals
            method.maxLocals += 4
            for (call in accepted) {
                val fallback = LabelNode()
                val done = LabelNode()
                val replacement = InsnList().apply {
                    add(VarInsnNode(ASTORE, local + 3)) // already evaluated callback; retain bootstrap/evaluation order
                    add(VarInsnNode(ISTORE, local + 2))
                    add(VarInsnNode(ASTORE, local + 1))
                    add(VarInsnNode(ASTORE, local))
                    add(VarInsnNode(ALOAD, local))
                    add(VarInsnNode(ALOAD, local + 1))
                    add(MethodInsnNode(INVOKESTATIC, SUPPORT, "canOptimize", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false))
                    add(JumpInsnNode(IFEQ, fallback))
                    add(VarInsnNode(ALOAD, local))
                    add(TypeInsnNode(CHECKCAST, MAP))
                    add(VarInsnNode(ALOAD, local + 1))
                    add(VarInsnNode(ILOAD, local + 2))
                    add(MethodInsnNode(INVOKESTATIC, SUPPORT, "sum", "(L$MAP;Ljava/lang/Object;I)I", false))
                    add(JumpInsnNode(GOTO, done))
                    add(fallback)
                    add(VarInsnNode(ALOAD, local))
                    add(VarInsnNode(ALOAD, local + 1))
                    add(VarInsnNode(ILOAD, local + 2))
                    add(VarInsnNode(ALOAD, local + 3))
                    add(MethodInsnNode(call.opcode, call.owner, call.name, call.desc, call.itf))
                    add(done)
                }
                method.instructions.insertBefore(call, replacement)
                method.instructions.remove(call)
                total++
            }
        }
        if (total > 0) {
            logger.info("optimized {} map accumulation sites in {}", total, cn.name.replace('/', '.'))
        }
        return total
    }

    private fun isIntegerSum(insn: AbstractInsnNode, operator: String): Boolean {
        if (insn !is InvokeDynamicInsnNode || insn.bsm != bootstrap || insn.desc != "()L$operator;" || insn.bsmArgs.size != 3) return false
        val implementation = insn.bsmArgs[1] as? Handle ?: return false
        if (implementation != Handle(H_INVOKESTATIC, "java/lang/Integer", "sum", "(II)I", false)) return false
        return if (operator == "java/util/function/BiFunction") {
            insn.name == "apply" && insn.bsmArgs[0] == Type.getMethodType("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;") &&
                insn.bsmArgs[2] == Type.getMethodType("(Ljava/lang/Integer;Ljava/lang/Integer;)Ljava/lang/Integer;")
        } else {
            insn.name == (if (operator.startsWith("java/")) "applyAsInt" else "apply") &&
                insn.bsmArgs[0] == Type.getMethodType("(II)I") && insn.bsmArgs[2] == Type.getMethodType("(II)I")
        }
    }
}
