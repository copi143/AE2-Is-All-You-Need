package allyouneed.pattern.machine

import allyouneed.machine.MachineType
import allyouneed.machine.MachineTypeRegistry
import appeng.api.inventories.InternalInventory
import appeng.api.stacks.GenericStack
import appeng.helpers.IPatternTerminalLogicHost
import appeng.parts.encoding.EncodingMode
import appeng.parts.encoding.PatternEncodingLogic
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

/**
 * Encoding logic for the machine pattern terminal.
 *
 * Holds the currently selected machine as a *virtual* selection (no real inventory slot).
 */
class MachinePatternEncodingLogic(host: IPatternTerminalLogicHost) : PatternEncodingLogic(host) {

    var virtualMachineTypeId: String = firstMachineId()
        private set

    val virtualMachineType: MachineType?
        get() = MachineTypeRegistry.byId(virtualMachineTypeId)

    fun setVirtualMachineType(id: String) {
        if (MachineTypeRegistry.byId(id) != null && id != virtualMachineTypeId) {
            virtualMachineTypeId = id
            saveChanges()
        }
    }

    private fun firstMachineId(): String =
        MachineTypeRegistry.getAll().firstOrNull()?.id ?: ""

    override fun onChangeInventory(inv: InternalInventory, slot: Int) {
        super.onChangeInventory(inv, slot)

        if (inv === encodedPatternInv) {
            val pattern = encodedPatternInv.getStackInSlot(0)
            if (!pattern.isEmpty && pattern.item is MachinePatternItem) {
                loadMachinePattern(pattern)
            }
        }
    }

    private fun loadMachinePattern(pattern: ItemStack) {
        val tag = pattern.tag ?: return
        if (!tag.contains(MachinePatternTags.MACHINE_TYPE, Tag.TAG_STRING.toInt())) return
        val typeId = tag.getString(MachinePatternTags.MACHINE_TYPE)
        if (MachineTypeRegistry.byId(typeId) == null) return

        setMode(EncodingMode.PROCESSING)
        virtualMachineTypeId = typeId

        val inputList = tag.getList(MachinePatternTags.INPUTS, Tag.TAG_COMPOUND.toInt())
        val inputs = (0 until inputList.size).map { GenericStack.readTag(inputList.getCompound(it)) }

        val output = GenericStack.readTag(tag.getCompound(MachinePatternTags.OUTPUT))

        val inputInv = encodedInputInv
        inputInv.beginBatch()
        try {
            for (i in 0 until inputInv.size()) {
                inputInv.setStack(i, if (i < inputs.size) inputs[i] else null)
            }
        } finally {
            inputInv.endBatch()
        }

        val outputInv = encodedOutputInv
        outputInv.beginBatch()
        try {
            if (output != null) {
                outputInv.setStack(0, output)
            }
            for (i in 1 until outputInv.size()) {
                outputInv.setStack(i, null)
            }
        } finally {
            outputInv.endBatch()
        }

        saveChanges()
    }

    override fun readFromNBT(tag: CompoundTag) {
        super.readFromNBT(tag)
        if (tag.contains(MachinePatternTags.MACHINE_TYPE, Tag.TAG_STRING.toInt())) {
            val id = tag.getString(MachinePatternTags.MACHINE_TYPE)
            if (MachineTypeRegistry.byId(id) != null) {
                virtualMachineTypeId = id
            }
        }
    }

    override fun writeToNBT(tag: CompoundTag) {
        super.writeToNBT(tag)
        tag.putString(MachinePatternTags.MACHINE_TYPE, virtualMachineTypeId)
    }
}
