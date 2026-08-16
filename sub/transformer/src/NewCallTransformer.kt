package allyouneed.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
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
    const val AE_KEY = "appeng/api/stacks/AEKey"
    const val AE_KEY_ASM = "allyouneed/core/AEKeyAsm"
    const val ASM_EQUALS = "asm\$equals"
    const val ASM_HASH = "asm\$hashCode"
    const val ASM_DROP = "asm\$dropSecondary"
    const val DROP_SECONDARY = "dropSecondary"

    fun apply(cn: ClassNode, keyClasses: Set<String>): Int {
        var rewritten = 0
        rewritten += retargetSuper(cn)
        for (mn in cn.methods) {
            rewritten += rewriteNews(mn, cn.name, keyClasses)
        }
        if (cn.name in keyClasses) {
            rewritten += renameEqualsHash(cn)
            rewritten += renameDropSecondary(cn)
        }
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
            val consume = argValues(insn.desc)
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
        val leftover = frame.stackSize - argValues(init.desc)
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

    private fun argValues(desc: String): Int = 1 + Type.getArgumentTypes(desc).size

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

    private fun retargetSuper(cn: ClassNode): Int {
        if (cn.superName != AE_KEY) return 0
        cn.superName = AE_KEY_ASM
        var n = 1
        for (mn in cn.methods) {
            if (mn.name != "<init>") continue
            var insn = mn.instructions?.first
            while (insn != null) {
                if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL &&
                    insn.owner == AE_KEY && insn.name == "<init>"
                ) {
                    insn.owner = AE_KEY_ASM
                    n++
                }
                insn = insn.next
            }
        }
        return n
    }

    private fun renameEqualsHash(cn: ClassNode): Int {
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
        return 1
    }

    private fun renameDropSecondary(cn: ClassNode): Int {
        val drops = cn.methods.filter { it.name == DROP_SECONDARY }
        if (drops.isEmpty() || cn.methods.any { it.name == ASM_DROP }) return 0
        for (mn in drops) mn.name = ASM_DROP
        for (mn in cn.methods) {
            var insn = mn.instructions?.first
            while (insn != null) {
                if (insn is MethodInsnNode && insn.owner == cn.name && insn.name == DROP_SECONDARY) {
                    insn.name = ASM_DROP
                }
                insn = insn.next
            }
        }
        return drops.size
    }

    private class CopyPreservingInterpreter : SourceInterpreter(Opcodes.ASM9) {
        override fun copyOperation(insn: AbstractInsnNode, value: SourceValue): SourceValue = value
    }
}
