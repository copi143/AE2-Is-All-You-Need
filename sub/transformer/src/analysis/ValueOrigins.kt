package allyouneed.transformer.analysis

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.Interpreter
import org.objectweb.asm.tree.analysis.Value

/** Bounded intraprocedural provenance; null origins mean unknown, never an empty proof. */
internal object ValueOrigins {
    data class OriginValue(val basic: BasicValue, val origins: Set<AbstractInsnNode>?) : Value {
        override fun getSize(): Int = basic.size
    }

    fun analyze(owner: String, method: MethodNode): Array<Frame<OriginValue>?>? {
        if (method.instructions.size() > 4096 || method.maxLocals > 256 || method.maxStack > 256) return null
        return Analyzer(OriginsInterpreter()).analyze(owner, method)
    }

    private class OriginsInterpreter : Interpreter<OriginValue>(Opcodes.ASM9) {
        private val basic = BasicInterpreter()
        private fun produced(value: BasicValue?, insn: AbstractInsnNode): OriginValue? =
            value?.let { OriginValue(it, setOf(insn)) }

        override fun newValue(type: Type?): OriginValue? = basic.newValue(type)?.let { OriginValue(it, null) }
        override fun newOperation(insn: AbstractInsnNode): OriginValue? = produced(basic.newOperation(insn), insn)
        override fun copyOperation(insn: AbstractInsnNode, value: OriginValue): OriginValue = value
        override fun unaryOperation(insn: AbstractInsnNode, value: OriginValue): OriginValue? {
            val result = basic.unaryOperation(insn, value.basic) ?: return null
            return OriginValue(result, if (insn.opcode == Opcodes.CHECKCAST) value.origins else setOf(insn))
        }
        override fun binaryOperation(insn: AbstractInsnNode, first: OriginValue, second: OriginValue): OriginValue? =
            produced(basic.binaryOperation(insn, first.basic, second.basic), insn)
        override fun ternaryOperation(insn: AbstractInsnNode, first: OriginValue, second: OriginValue, third: OriginValue): OriginValue? =
            produced(basic.ternaryOperation(insn, first.basic, second.basic, third.basic), insn)
        override fun naryOperation(insn: AbstractInsnNode, values: MutableList<out OriginValue>): OriginValue? =
            produced(basic.naryOperation(insn, values.map { it.basic }), insn)
        override fun returnOperation(insn: AbstractInsnNode, value: OriginValue, expected: OriginValue) =
            basic.returnOperation(insn, value.basic, expected.basic)
        override fun merge(first: OriginValue, second: OriginValue): OriginValue {
            if (first == second) return first
            val origins = if (first.origins == null || second.origins == null) null
            else (first.origins + second.origins).takeIf { it.size <= 8 }
            return OriginValue(basic.merge(first.basic, second.basic), origins)
        }
    }
}
