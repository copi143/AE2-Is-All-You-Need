package allyouneed.pattern.machine

import allyouneed.logic.machine.MachineType
import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import appeng.api.stacks.KeyCounter
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object MachinePatternTags {
    const val MACHINE_TYPE = "machineType"
    const val INPUTS = "in"
    const val OUTPUT = "out"
}

/**
 * 已编码的机器样板。刻意不实现 [appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern]，
 * 使原版分子装配器拒绝执行，仅本模组装配室可运行。
 *
 * An encoded machine pattern. Deliberately does NOT implement
 * [appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern], so the vanilla molecular
 * assembler refuses it and only our machine assembler can execute it.
 */
class MachinePatternDetails(private val definition: AEItemKey) : IPatternDetails {

    private val machineTypeId: String
    /** 始终查当前 live 实例，避免重载后幽灵引用。Always resolves live instance. */
    val machineType: MachineType?
        get() = MachineType.byId(machineTypeId)
    private val sparseInputs: Array<GenericStack?>
    private val output: GenericStack
    private val inputs: Array<IPatternDetails.IInput>

    init {
        val tag = definition.tag ?: throw IllegalStateException("Machine pattern without tag")
        machineTypeId = tag.getString(MachinePatternTags.MACHINE_TYPE)

        sparseInputs = readArray(tag, MachinePatternTags.INPUTS)
        output = GenericStack.readTag(tag.getCompound(MachinePatternTags.OUTPUT))
            ?: throw IllegalStateException("Machine pattern without output")

        inputs = sparseInputs.filterNotNull().map { Input(it) }.toTypedArray()
    }

    private fun readArray(tag: CompoundTag, key: String): Array<GenericStack?> {
        val list = tag.getList(key, Tag.TAG_COMPOUND.toInt())
        val arr = arrayOfNulls<GenericStack>(list.size)
        for (i in list.indices) {
            arr[i] = GenericStack.readTag(list.getCompound(i))
        }
        return arr
    }

    val machineTypeIdValue: String get() = machineTypeId

    fun getSparseInputs(): Array<GenericStack?> = sparseInputs

    fun getOutput(): GenericStack = output

    override fun getDefinition(): AEItemKey = definition

    override fun getInputs(): Array<IPatternDetails.IInput> = inputs

    override fun getOutputs(): Array<GenericStack> = arrayOf(output)

    override fun supportsPushInputsToExternalInventory(): Boolean = false

    override fun equals(other: Any?): Boolean =
        other is MachinePatternDetails && other.definition == this.definition

    override fun hashCode(): Int = definition.hashCode()

    // --- Machine assembler integration ---

    /**
     * 根据当前 3×3 装配网格解析产物；无匹配则空。
     * Resolves output for the filled 3x3 assembler grid, or empty if nothing matches.
     */
    fun assemble(container: CraftingContainer, level: Level): ItemStack {
        val type = machineType ?: return ItemStack.EMPTY
        // 手动数据包配方优先，再绑定配方源（见 MachineType.resolve）
        return type.resolve(level, container) ?: ItemStack.EMPTY
    }

    /**
     * 指定装配网格槽是否接受该物品。
     * Whether the given item is acceptable in the given assembler grid slot.
     */
    fun isItemValid(slot: Int, key: AEItemKey?, level: Level): Boolean {
        if (slot >= sparseInputs.size) {
            return key == null
        }
        val encoded = sparseInputs[slot]
        return if (key == null) {
            encoded == null
        } else {
            encoded != null && encoded.what().equals(key)
        }
    }

    /**
     * 指定装配网格槽是否被本机器使用。
     * Whether the given assembler grid slot is used by this machine.
     */
    fun isSlotEnabled(slot: Int): Boolean {
        return slot < (machineType?.inputSlots ?: 0)
    }

    /**
     * 用推入的输入填充装配网格。
     * Fills the assembler grid from the pushed inputs.
     */
    fun fillCraftingGrid(table: Array<KeyCounter>, setGrid: (Int, ItemStack) -> Unit) {
        for (i in table.indices) {
            var placed = false
            for (entry in table[i]) {
                if (entry.longValue > 0 && entry.key is AEItemKey) {
                    val itemKey = entry.key as AEItemKey
                    setGrid(i, itemKey.toStack(entry.longValue.toInt()))
                    table[i].remove(itemKey, entry.longValue)
                    placed = true
                    break
                }
            }
            if (!placed) {
                table[i].removeZeros()
            }
        }
    }

    fun getRemainingItems(container: CraftingContainer, level: Level): List<ItemStack> {
        val type = machineType ?: return List(container.containerSize) { ItemStack.EMPTY }
        return type.remainders(level, container)
    }


    private class Input(private val stack: GenericStack) : IPatternDetails.IInput {
        private val template: Array<GenericStack> = arrayOf(GenericStack(stack.what(), 1))

        override fun getPossibleInputs(): Array<GenericStack> = template

        override fun getMultiplier(): Long = stack.amount()

        override fun isValid(what: AEKey, level: Level): Boolean = what.matches(stack)

        override fun getRemainingKey(template: AEKey): AEKey? = null
    }
}
