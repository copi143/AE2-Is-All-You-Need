package allyouneed.pattern.machine

import allyouneed.machine.MachineType
import allyouneed.machine.MachineTypeRegistry
import allyouneed.pattern.ModItems
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.GenericStack
import appeng.helpers.IPatternTerminalMenuHost
import appeng.menu.guisync.GuiSync
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.me.items.PatternEncodingTermMenu
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.TransientCraftingContainer
import net.minecraft.world.item.ItemStack

class MachinePatternTerminalMenu(
    containerId: Int,
    playerInventory: Inventory,
    host: IPatternTerminalMenuHost?,
) : PatternEncodingTermMenu(TYPE, containerId, playerInventory, host, true) {

    companion object {
        val TYPE: MenuType<MachinePatternTerminalMenu> =
            MenuTypeBuilder.create(::MachinePatternTerminalMenu, IPatternTerminalMenuHost::class.java)
                .build("machine_pattern_terminal")

        private const val ACTION_SET_MACHINE = "set_machine"
    }

    private val encodingLogic = host!!.logic as MachinePatternEncodingLogic

    @GuiSync(90)
    var selectedMachineIndex: Int = MachineTypeRegistry.indexById(encodingLogic.virtualMachineTypeId)
        private set

    val selectedMachine: MachineType?
        get() = MachineTypeRegistry.getAll().getOrNull(selectedMachineIndex)

    init {
        registerClientAction(ACTION_SET_MACHINE, Int::class.java) { setMachineIndex(it) }
    }

    fun cycleMachine() {
        val machines = MachineTypeRegistry.getAll()
        if (machines.size <= 1) {
            return
        }
        setMachineIndex(selectedMachineIndex + 1)
    }

    fun setMachineIndex(index: Int) {
        val machines = MachineTypeRegistry.getAll()
        if (machines.isEmpty()) {
            return
        }
        val clamped = Math.floorMod(index, machines.size)
        selectedMachineIndex = clamped
        encodingLogic.setVirtualMachineType(machines[clamped].id)
        if (isClientSide) {
            sendClientAction(ACTION_SET_MACHINE, clamped)
        }
        broadcastChanges()
    }

    override fun broadcastChanges() {
        super.broadcastChanges()
        val idx = MachineTypeRegistry.indexById(encodingLogic.virtualMachineTypeId)
        if (idx >= 0) {
            selectedMachineIndex = idx
        }
    }

    override fun onSlotChange(slot: Slot) {
        super.onSlotChange(slot)
        val encodedStack = encodingLogic.encodedPatternInv.getStackInSlot(0)
        if (!encodedStack.isEmpty && encodedStack.item is MachinePatternItem) {
            val tag = encodedStack.tag
            if (tag != null) {
                val id = tag.getString(MachinePatternTags.MACHINE_TYPE)
                if (MachineTypeRegistry.byId(id) != null) {
                    encodingLogic.setVirtualMachineType(id)
                    selectedMachineIndex = MachineTypeRegistry.indexById(id)
                }
            }
        }
    }

    override fun encode() {
        if (isClientSide) {
            sendClientAction("encode")
            return
        }
        encodeMachinePattern()
    }

    private fun encodeMachinePattern() {
        val logic = encodingLogic
        val machineType = logic.virtualMachineType
        if (machineType == null) {
            broadcastChanges()
            return
        }

        val inputSlots = machineType.inputSlots
        val inputs = arrayOfNulls<GenericStack>(inputSlots)
        var hasInput = false
        for (i in 0 until inputSlots) {
            inputs[i] = logic.encodedInputInv.getStack(i)
            if (inputs[i] != null) {
                hasInput = true
            }
        }
        if (!hasInput) {
            broadcastChanges()
            return
        }

        val container: CraftingContainer = if (inputSlots <= 1) {
            TransientCraftingContainer(this, 1, 1)
        } else {
            TransientCraftingContainer(this, 3, 3)
        }
        for (i in 0 until inputSlots) {
            val gs = inputs[i] ?: continue
            val key = gs.what()
            if (key !is AEItemKey) {
                broadcastChanges()
                return
            }
            container.setItem(i, key.toStack(gs.amount().toInt()))
        }

        val output = machineType.resolve(player.level(), container)
        if (output == null) {
            broadcastChanges()
            return
        }
        val encodedOutput = GenericStack.fromItemStack(output) ?: run {
            broadcastChanges()
            return
        }

        val encodedPattern = ModItems.MACHINE_PATTERN.encode(machineType.id, inputs, encodedOutput)

        val encodedInv = logic.encodedPatternInv
        val blankInv = logic.blankPatternInv
        val existingEncoded = encodedInv.getStackInSlot(0)

        if (!existingEncoded.isEmpty) {
            if (!PatternDetailsHelper.isEncodedPattern(existingEncoded)) {
                broadcastChanges()
                return
            }
            encodedInv.setItemDirect(0, encodedPattern)
        } else {
            val blankPattern = blankInv.getStackInSlot(0)
            if (blankPattern.isEmpty) {
                broadcastChanges()
                return
            }
            blankPattern.shrink(1)
            blankInv.setItemDirect(0, if (blankPattern.isEmpty) ItemStack.EMPTY else blankPattern)
            encodedInv.setItemDirect(0, encodedPattern)
        }

        logic.saveChanges()
        broadcastChanges()
    }
}
