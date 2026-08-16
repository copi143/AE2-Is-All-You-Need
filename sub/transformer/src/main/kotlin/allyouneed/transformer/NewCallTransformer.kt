package allyouneed.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

object NewCallTransformer {
    const val INTERNER_OWNER = "allyouneed/core/KeyInterner"
    const val CONTENT_IDENTITY = "allyouneed/core/ContentIdentity"
    const val AE_ITEM_KEY = "appeng/api/stacks/AEItemKey"
    const val MAX_STACK_SIZE = "maxStackSize"
    const val ASM_EQUALS = "asm\$equals"
    const val ASM_HASH = "asm\$hashCode"

    fun apply(cn: ClassNode, keyClasses: Set<String>): Int {
        var rewritten = 0
        for (mn in cn.methods) {
            rewritten += rewriteNews(mn, cn.name, keyClasses)
            if (cn.name == AE_ITEM_KEY) rewritten += stripMaxStackSizeWrite(mn)
        }
        if (cn.name in keyClasses) rewritten += rewriteEqualsHash(cn)
        if (rewritten > 0) {
            Log.info("rewrote {} sites in {}", rewritten, cn.name.replace('/', '.'))
        } else if (cn.name in keyClasses) {
            Log.warn("visited key class {} but matched 0 sites", cn.name.replace('/', '.'))
        }
        return rewritten
    }

    fun apply(bytes: ByteArray, keyClasses: Set<String>): ByteArray {
        val cr = ClassReader(bytes)
        val cn = ClassNode()
        cr.accept(cn, 0)
        val n = apply(cn, keyClasses)
        if (n == 0) return bytes
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)
        cn.accept(cw)
        return cw.toByteArray()
    }

    private fun rewriteNews(mn: MethodNode, owner: String, keyClasses: Set<String>): Int {
        val list = mn.instructions ?: return 0
        var count = 0
        var insn = list.first
        while (insn != null) {
            val next = insn.next
            if (insn.opcode == Opcodes.NEW && insn is TypeInsnNode && insn.desc in keyClasses) {
                if (tryRewrite(mn, insn, owner, mn.name, keyClasses)) count++
            }
            insn = next
        }
        return count
    }

    private fun tryRewrite(
        mn: MethodNode,
        newInsn: TypeInsnNode,
        owner: String,
        method: String,
        keyClasses: Set<String>,
    ): Boolean {
        val where = "${owner.replace('/', '.')}.$method"
        val key = newInsn.desc.replace('/', '.')
        val dup = skipNoise(newInsn.next)
        if (dup == null || dup.opcode != Opcodes.DUP) {
            Log.warn("unmatched NEW {} in {} (no DUP)", key, where)
            return false
        }
        val labels = HashMap<LabelNode, Int>()
        var height = 2
        var cur = dup.next
        while (cur != null) {
            if (cur is LabelNode) {
                val recorded = labels[cur]
                if (recorded != null) height = recorded else labels[cur] = height
            }
            if (cur.opcode == Opcodes.INVOKESPECIAL && cur is MethodInsnNode &&
                cur.owner == newInsn.desc && cur.name == "<init>"
            ) {
                val consume = Type.getArgumentsAndReturnSizes(cur.desc) shr 2
                if (height == consume + 1) return replace(mn, cur, newInsn.desc)
            }
            when (cur) {
                is JumpInsnNode -> labels.putIfAbsent(cur.label, height + jumpDelta(cur.opcode))
                is TableSwitchInsnNode -> {
                    val after = height - 1
                    labels.putIfAbsent(cur.dflt, after)
                    for (label in cur.labels) labels.putIfAbsent(label, after)
                }
                is LookupSwitchInsnNode -> {
                    val after = height - 1
                    labels.putIfAbsent(cur.dflt, after)
                    for (label in cur.labels) labels.putIfAbsent(label, after)
                }
            }
            val delta = stackDelta(cur) ?: run {
                Log.warn("unmatched NEW {} in {} (unsafe insn {})", key, where, cur.opcode)
                return false
            }
            height += delta
            if (height < 1) {
                Log.warn("unmatched NEW {} in {} (stack underflow)", key, where)
                return false
            }
            cur = cur.next
        }
        Log.warn("unmatched NEW {} in {}", key, where)
        return false
    }

    private fun replace(mn: MethodNode, init: MethodInsnNode, keyClass: String): Boolean {
        val invoke = MethodInsnNode(
            Opcodes.INVOKESTATIC,
            INTERNER_OWNER,
            "intern",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
        )
        val cast = TypeInsnNode(Opcodes.CHECKCAST, keyClass)
        mn.instructions.insert(init, invoke)
        mn.instructions.insert(invoke, cast)
        return true
    }

    private fun rewriteEqualsHash(cn: ClassNode): Int {
        if (cn.methods.any { it.name == ASM_EQUALS && it.desc == "(Ljava/lang/Object;)Z" }) return 0
        val eq = cn.methods.firstOrNull { it.name == "equals" && it.desc == "(Ljava/lang/Object;)Z" } ?: return 0
        val hash = cn.methods.firstOrNull { it.name == "hashCode" && it.desc == "()I" } ?: return 0
        eq.name = ASM_EQUALS
        hash.name = ASM_HASH
        for (mn in cn.methods) {
            var insn = mn.instructions?.first
            while (insn != null) {
                if (insn is MethodInsnNode && insn.owner == cn.name) {
                    if (insn.name == "equals" && insn.desc == "(Ljava/lang/Object;)Z") insn.name = ASM_EQUALS
                    if (insn.name == "hashCode" && insn.desc == "()I") insn.name = ASM_HASH
                }
                insn = insn.next
            }
        }
        if (cn.interfaces == null) cn.interfaces = ArrayList()
        if (CONTENT_IDENTITY !in cn.interfaces) cn.interfaces.add(CONTENT_IDENTITY)
        cn.methods.add(identityEquals())
        cn.methods.add(forwardingHash(cn.name))
        return 1
    }

    private fun identityEquals(): MethodNode {
        val mn = MethodNode(Opcodes.ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null)
        val fail = LabelNode()
        mn.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        mn.instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
        mn.instructions.add(JumpInsnNode(Opcodes.IF_ACMPNE, fail))
        mn.instructions.add(InsnNode(Opcodes.ICONST_1))
        mn.instructions.add(InsnNode(Opcodes.IRETURN))
        mn.instructions.add(fail)
        mn.instructions.add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        mn.instructions.add(InsnNode(Opcodes.ICONST_0))
        mn.instructions.add(InsnNode(Opcodes.IRETURN))
        mn.maxStack = 2
        mn.maxLocals = 2
        return mn
    }

    private fun forwardingHash(owner: String): MethodNode {
        val mn = MethodNode(Opcodes.ACC_PUBLIC, "hashCode", "()I", null, null)
        mn.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        mn.instructions.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, ASM_HASH, "()I", false))
        mn.instructions.add(InsnNode(Opcodes.IRETURN))
        mn.maxStack = 1
        mn.maxLocals = 1
        return mn
    }

    private fun stripMaxStackSizeWrite(mn: MethodNode): Int {
        if (mn.name != "of") return 0
        val args = Type.getArgumentTypes(mn.desc)
        if (args.size != 1 || args[0].internalName != "net/minecraft/world/item/ItemStack") return 0
        val list = mn.instructions ?: return 0
        var count = 0
        var insn = list.first
        while (insn != null) {
            val next = insn.next
            if (insn.opcode == Opcodes.PUTFIELD && insn is FieldInsnNode &&
                insn.owner == AE_ITEM_KEY && insn.name == MAX_STACK_SIZE
            ) {
                list.set(insn, InsnNode(Opcodes.POP2))
                count++
            }
            insn = next
        }
        return count
    }

    private fun skipNoise(insn: AbstractInsnNode?): AbstractInsnNode? {
        var cur = insn
        while (cur is LineNumberNode || cur is FrameNode) cur = cur.next
        return cur
    }

    private fun jumpDelta(opcode: Int): Int = when (opcode) {
        Opcodes.GOTO -> 0
        in Opcodes.IFEQ..Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> -1
        in Opcodes.IF_ICMPEQ..Opcodes.IF_ACMPNE -> -2
        else -> 0
    }

    private fun stackDelta(insn: AbstractInsnNode): Int? {
        when (insn) {
            is LabelNode, is LineNumberNode, is FrameNode, is IincInsnNode -> return 0
            is LdcInsnNode -> return if (insn.cst is Long || insn.cst is Double) 2 else 1
            is MethodInsnNode -> return invokeDelta(insn.opcode == Opcodes.INVOKESTATIC, insn.desc)
            is InvokeDynamicInsnNode -> return invokeDelta(true, insn.desc)
            is FieldInsnNode -> {
                val size = Type.getType(insn.desc).size
                return when (insn.opcode) {
                    Opcodes.GETSTATIC -> size
                    Opcodes.PUTSTATIC -> -size
                    Opcodes.GETFIELD -> size - 1
                    Opcodes.PUTFIELD -> -size - 1
                    else -> null
                }
            }
            is MultiANewArrayInsnNode -> return 1 - insn.dims
            is TableSwitchInsnNode, is LookupSwitchInsnNode -> return -1
        }
        val op = insn.opcode
        if (op < 0) return 0
        return OPCODE_DELTA.getOrNull(op)?.takeIf { it != UNK }
    }

    private fun invokeDelta(statik: Boolean, desc: String): Int {
        val sizes = Type.getArgumentsAndReturnSizes(desc)
        val args = sizes shr 2
        val ret = sizes and 3
        val consumed = if (statik) args - 1 else args
        return ret - consumed
    }

    private const val UNK = Int.MIN_VALUE

    private val OPCODE_DELTA = IntArray(256) { UNK }.also { d ->
        d[Opcodes.NOP] = 0
        d[Opcodes.ACONST_NULL] = 1
        for (op in Opcodes.ICONST_M1..Opcodes.ICONST_5) d[op] = 1
        d[Opcodes.LCONST_0] = 2
        d[Opcodes.LCONST_1] = 2
        d[Opcodes.FCONST_0] = 1
        d[Opcodes.FCONST_1] = 1
        d[Opcodes.FCONST_2] = 1
        d[Opcodes.DCONST_0] = 2
        d[Opcodes.DCONST_1] = 2
        d[Opcodes.BIPUSH] = 1
        d[Opcodes.SIPUSH] = 1
        d[Opcodes.ILOAD] = 1
        d[Opcodes.LLOAD] = 2
        d[Opcodes.FLOAD] = 1
        d[Opcodes.DLOAD] = 2
        d[Opcodes.ALOAD] = 1
        d[Opcodes.IALOAD] = -1
        d[Opcodes.LALOAD] = 0
        d[Opcodes.FALOAD] = -1
        d[Opcodes.DALOAD] = 0
        d[Opcodes.AALOAD] = -1
        d[Opcodes.BALOAD] = -1
        d[Opcodes.CALOAD] = -1
        d[Opcodes.SALOAD] = -1
        d[Opcodes.ISTORE] = -1
        d[Opcodes.LSTORE] = -2
        d[Opcodes.FSTORE] = -1
        d[Opcodes.DSTORE] = -2
        d[Opcodes.ASTORE] = -1
        d[Opcodes.IASTORE] = -3
        d[Opcodes.LASTORE] = -4
        d[Opcodes.FASTORE] = -3
        d[Opcodes.DASTORE] = -4
        d[Opcodes.AASTORE] = -3
        d[Opcodes.BASTORE] = -3
        d[Opcodes.CASTORE] = -3
        d[Opcodes.SASTORE] = -3
        d[Opcodes.POP] = -1
        d[Opcodes.POP2] = -2
        d[Opcodes.DUP] = 1
        d[Opcodes.DUP_X1] = 1
        d[Opcodes.DUP_X2] = 1
        d[Opcodes.DUP2] = 2
        d[Opcodes.DUP2_X1] = 2
        d[Opcodes.DUP2_X2] = 2
        d[Opcodes.SWAP] = 0
        for (op in Opcodes.IADD..Opcodes.DREM) d[op] = if ((op - Opcodes.IADD) and 1 == 1) -2 else -1
        d[Opcodes.INEG] = 0
        d[Opcodes.LNEG] = 0
        d[Opcodes.FNEG] = 0
        d[Opcodes.DNEG] = 0
        d[Opcodes.ISHL] = -1
        d[Opcodes.LSHL] = -1
        d[Opcodes.ISHR] = -1
        d[Opcodes.LSHR] = -1
        d[Opcodes.IUSHR] = -1
        d[Opcodes.LUSHR] = -1
        d[Opcodes.IAND] = -1
        d[Opcodes.LAND] = -2
        d[Opcodes.IOR] = -1
        d[Opcodes.LOR] = -2
        d[Opcodes.IXOR] = -1
        d[Opcodes.LXOR] = -2
        d[Opcodes.I2L] = 1
        d[Opcodes.I2F] = 0
        d[Opcodes.I2D] = 1
        d[Opcodes.L2I] = -1
        d[Opcodes.L2F] = -1
        d[Opcodes.L2D] = 0
        d[Opcodes.F2I] = 0
        d[Opcodes.F2L] = 1
        d[Opcodes.F2D] = 1
        d[Opcodes.D2I] = -1
        d[Opcodes.D2L] = 0
        d[Opcodes.D2F] = -1
        d[Opcodes.I2B] = 0
        d[Opcodes.I2C] = 0
        d[Opcodes.I2S] = 0
        d[Opcodes.LCMP] = -3
        d[Opcodes.FCMPL] = -1
        d[Opcodes.FCMPG] = -1
        d[Opcodes.DCMPL] = -3
        d[Opcodes.DCMPG] = -3
        d[Opcodes.IFEQ] = -1
        d[Opcodes.IFNE] = -1
        d[Opcodes.IFLT] = -1
        d[Opcodes.IFGE] = -1
        d[Opcodes.IFGT] = -1
        d[Opcodes.IFLE] = -1
        d[Opcodes.IF_ICMPEQ] = -2
        d[Opcodes.IF_ICMPNE] = -2
        d[Opcodes.IF_ICMPLT] = -2
        d[Opcodes.IF_ICMPGE] = -2
        d[Opcodes.IF_ICMPGT] = -2
        d[Opcodes.IF_ICMPLE] = -2
        d[Opcodes.IF_ACMPEQ] = -2
        d[Opcodes.IF_ACMPNE] = -2
        d[Opcodes.GOTO] = 0
        d[Opcodes.IFNULL] = -1
        d[Opcodes.IFNONNULL] = -1
        d[Opcodes.NEW] = 1
        d[Opcodes.NEWARRAY] = 0
        d[Opcodes.ANEWARRAY] = 0
        d[Opcodes.ARRAYLENGTH] = 0
        d[Opcodes.CHECKCAST] = 0
        d[Opcodes.INSTANCEOF] = 0
    }
}
