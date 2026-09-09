package allyouneed.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue

object NewCallTransformer {
    fun apply(cn: ClassNode, keyClasses: Set<String>): Int = apply(cn) { it in keyClasses }

    fun apply(bytes: ByteArray, keyClasses: Set<String>): ByteArray = apply(bytes) { it in keyClasses }

    fun apply(cn: ClassNode, isKey: (String) -> Boolean): Int {
        val isKeyClass = isKey(cn.name)
        var rewritten = 0
        rewritten += retargetSuper(cn)
        for (mn in cn.methods) {
            rewritten += rewriteNews(mn, cn.name, isKey, Constants.AE_KEY_INTERNER)
        }
        if (isKeyClass) {
            rewritten += renameEqualsHash(cn)
            rewritten += renameDropSecondary(cn)
        }
        if (rewritten > 0) {
            logger.info("rewrote {} sites in {}", rewritten, cn.name.replace('/', '.'))
        } else if (isKeyClass) {
            logger.warn("visited key class {} but matched 0 sites", cn.name.replace('/', '.'))
        }
        return rewritten
    }

    fun apply(bytes: ByteArray, isKey: (String) -> Boolean): ByteArray {
        val cr = ClassReader(bytes)
        val cn = ClassNode()
        cr.accept(cn, 0)
        val n = apply(cn, isKey)
        if (n == 0) return bytes
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)
        cn.accept(cw)
        return cw.toByteArray()
    }

    fun applyResourceLocation(cn: ClassNode): Int {
        val isRl: (String) -> Boolean = { it == Constants.RESOURCE_LOCATION }
        var rewritten = 0
        for (mn in cn.methods) {
            rewritten += rewriteNews(mn, cn.name, isRl, Constants.RESOURCE_LOCATION_INTERNER)
        }
        if (rewritten > 0) {
            logger.info("rewrote {} ResourceLocation sites in {}", rewritten, cn.name.replace('/', '.'))
        }
        return rewritten
    }

    private fun rewriteNews(mn: MethodNode, owner: String, isKey: (String) -> Boolean, internerOwner: String): Int {
        val list = mn.instructions ?: return 0
        val news = list.filterIsInstance<TypeInsnNode>().filter { it.opcode == Opcodes.NEW && isKey(it.desc) }
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
            if (insn.name != "<init>" || !isKey(insn.owner)) continue
            val frame = frames[i] ?: continue
            val consume = argValues(insn.desc)
            if (frame.stackSize < consume) continue
            val receiver = frame.getStack(frame.stackSize - consume)
            val newInsn = receiver.insns.filterIsInstance<TypeInsnNode>()
                .firstOrNull { it.opcode == Opcodes.NEW && it.desc == insn.owner } ?: continue
            if (!matched.add(newInsn)) continue
            insertIntern(mn, insn, insn.owner, newInsn, frame, internerOwner)
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
        internerOwner: String,
    ) {
        val leftover = frame.stackSize - argValues(init.desc)
        val onStack = leftover > 0 && frame.getStack(leftover - 1).insns.contains(newInsn)
        if (onStack) {
            insertCall(mn, init, keyClass, internerOwner)
            return
        }
        var last: AbstractInsnNode = init
        for (i in 0 until frame.locals) {
            if (newInsn !in frame.getLocal(i).insns) continue
            val load = VarInsnNode(Opcodes.ALOAD, i)
            mn.instructions.insert(last, load)
            last = insertCall(mn, load, keyClass, internerOwner)
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
        internerOwner: String,
    ): AbstractInsnNode {
        val invoke = MethodInsnNode(
            Opcodes.INVOKESTATIC,
            internerOwner,
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
        if (cn.superName != Constants.AE_KEY) return 0
        cn.superName = Constants.AE_KEY_ASM
        var n = 1
        for (mn in cn.methods) {
            if (mn.name != "<init>") continue
            var insn = mn.instructions?.first
            while (insn != null) {
                if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL && insn.owner == Constants.AE_KEY && insn.name == "<init>") {
                    insn.owner = Constants.AE_KEY_ASM
                    n++
                }
                insn = insn.next
            }
        }
        return n
    }

    private fun renameEqualsHash(cn: ClassNode): Int {
        if (cn.methods.any { it.name == Constants.ASM_EQUALS && it.desc == "(Ljava/lang/Object;)Z" }) return 0
        val eq = cn.methods.firstOrNull { it.name == "equals" && it.desc == "(Ljava/lang/Object;)Z" } ?: return 0
        val hash = cn.methods.firstOrNull { it.name == "hashCode" && it.desc == "()I" } ?: return 0
        eq.name = Constants.ASM_EQUALS
        hash.name = Constants.ASM_HASH
        for (mn in cn.methods) {
            var insn = mn.instructions?.first
            while (insn != null) {
                if (insn is MethodInsnNode && insn.owner == cn.name) {
                    if (insn.name == "equals" && insn.desc == "(Ljava/lang/Object;)Z") insn.name = Constants.ASM_EQUALS
                    if (insn.name == "hashCode" && insn.desc == "()I") insn.name = Constants.ASM_HASH
                }
                insn = insn.next
            }
        }
        return 1
    }

    private fun renameDropSecondary(cn: ClassNode): Int {
        val drops = cn.methods.filter { it.name == Constants.DROP_SECONDARY }
        if (drops.isEmpty() || cn.methods.any { it.name == Constants.ASM_DROP_SECONDARY }) return 0
        for (mn in drops) mn.name = Constants.ASM_DROP_SECONDARY
        for (mn in cn.methods) {
            var insn = mn.instructions?.first
            while (insn != null) {
                if (insn is MethodInsnNode && insn.owner == cn.name && insn.name == Constants.DROP_SECONDARY) {
                    insn.name = Constants.ASM_DROP_SECONDARY
                }
                insn = insn.next
            }
        }
        return drops.size
    }

    private class CopyPreservingInterpreter : SourceInterpreter(ASM9) {
        override fun copyOperation(insn: AbstractInsnNode, value: SourceValue): SourceValue = value
    }
}
