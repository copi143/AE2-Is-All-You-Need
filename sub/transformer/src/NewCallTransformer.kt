package allyouneed.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue

object NewCallTransformer {
    const val INTERNER_OWNER = "allyouneed/core/KeyInterner"
    const val CONTENT_IDENTITY = "allyouneed/core/ContentIdentity"
    const val ASM_EQUALS = "asm\$equals"
    const val ASM_HASH = "asm\$hashCode"

    fun apply(cn: ClassNode, keyClasses: Set<String>): Int {
        var rewritten = 0
        for (mn in cn.methods) {
            rewritten += rewriteNews(mn, cn.name, keyClasses)
        }
        if (cn.name in keyClasses) rewritten += rewriteEqualsHash(cn)
        if (rewritten > 0) {
            logger.info("rewrote {} sites in {}", rewritten, cn.name.replace('/', '.'))
        } else if (cn.name in keyClasses) {
            logger.warn("visited key class {} but matched 0 sites", cn.name.replace('/', '.'))
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
        val news = list.filterIsInstance<TypeInsnNode>()
            .filter { it.opcode == Opcodes.NEW && it.desc in keyClasses }
        if (news.isEmpty()) return 0
        val frames = try {
            Analyzer(CopyPreservingInterpreter()).analyze(owner, mn)
        } catch (t: Throwable) {
            logger.warn("analyze failed in {}.{}: {}", owner.replace('/', '.'), mn.name, t.message)
            return 0
        }
        val insns = list.toArray()
        val matched = HashSet<TypeInsnNode>()
        var count = 0
        for (i in insns.indices) {
            val insn = insns[i]
            if (insn.opcode != Opcodes.INVOKESPECIAL || insn !is MethodInsnNode) continue
            if (insn.name != "<init>" || insn.owner !in keyClasses) continue
            val frame = frames[i] ?: continue
            val consume = Type.getArgumentsAndReturnSizes(insn.desc) shr 2
            if (frame.stackSize < consume) continue
            val receiver = frame.getStack(frame.stackSize - consume)
            val newInsn = receiver.insns.filterIsInstance<TypeInsnNode>()
                .firstOrNull { it.opcode == Opcodes.NEW && it.desc == insn.owner }
                ?: continue
            if (!matched.add(newInsn)) continue
            insertIntern(mn, insn, insn.owner, newInsn, frame)
            count++
        }
        for (insn in news) {
            if (insn !in matched) {
                logger.warn("unmatched NEW {} in {}.{}", insn.desc.replace('/', '.'), owner.replace('/', '.'), mn.name)
            }
        }
        return count
    }

    private fun insertIntern(
        mn: MethodNode,
        init: MethodInsnNode,
        keyClass: String,
        newInsn: TypeInsnNode,
        frame: Frame<SourceValue>,
    ) {
        val consume = Type.getArgumentsAndReturnSizes(init.desc) shr 2
        val leftover = frame.stackSize - consume
        val onStack = leftover > 0 && frame.getStack(leftover - 1).insns.contains(newInsn)
        if (onStack) {
            insertCall(mn, init, keyClass)
            return
        }
        var last: AbstractInsnNode = init
        for (i in 0 until frame.locals) {
            if (newInsn !in frame.getLocal(i).insns) continue
            val load = VarInsnNode(Opcodes.ALOAD, i)
            mn.instructions.insert(last, load)
            last = insertCall(mn, load, keyClass)
            val store = VarInsnNode(Opcodes.ASTORE, i)
            mn.instructions.insert(last, store)
            last = store
        }
    }

    private fun insertCall(
        mn: MethodNode,
        after: AbstractInsnNode,
        keyClass: String,
    ): AbstractInsnNode {
        val invoke = MethodInsnNode(
            Opcodes.INVOKESTATIC,
            INTERNER_OWNER,
            "intern",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
        )
        val cast = TypeInsnNode(Opcodes.CHECKCAST, keyClass)
        mn.instructions.insert(after, invoke)
        mn.instructions.insert(invoke, cast)
        return cast
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

    private class CopyPreservingInterpreter : SourceInterpreter(Opcodes.ASM9) {
        override fun copyOperation(insn: AbstractInsnNode, value: SourceValue): SourceValue = value
    }
}
