package allyouneed.parts.machineassembler

import allyouneed.logic.machine.MachineTypeRegistry
import allyouneed.pattern.machine.MachinePatternItem
import appeng.api.inventories.InternalInventory
import appeng.api.stacks.AEItemKey
import appeng.client.Point
import appeng.menu.SlotSemantic
import appeng.menu.SlotSemantics
import appeng.menu.guisync.GuiSync
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.implementations.UpgradeableMenu
import appeng.menu.interfaces.IProgressProvider
import appeng.menu.slot.AppEngSlot
import appeng.menu.slot.IOptionalSlot
import appeng.menu.slot.OutputSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object MachineSlotSemantics {
    val MACHINE: SlotSemantic = SlotSemantics.register("MACHINE", false)
}

class MachineAssemblerMenu(
    id: Int,
    playerInv: Inventory,
    be: MachineAssemblerBlockEntity,
) : UpgradeableMenu<MachineAssemblerBlockEntity>(TYPE, id, playerInv, be),
    IProgressProvider {

    companion object {
        val TYPE: MenuType<MachineAssemblerMenu> =
            MenuTypeBuilder.create(::MachineAssemblerMenu, MachineAssemblerBlockEntity::class.java)
                .build("machine_assembler")

        private const val MAX_CRAFT_PROGRESS = 100
    }

    private val molecularAssembler = be

    @GuiSync(4)
    var craftProgress: Int = 0
        private set

    private var encodedPatternSlot: Slot? = null

    fun isValidItemForSlot(slotIndex: Int, i: ItemStack): Boolean {
        val details = molecularAssembler.getCurrentPattern()
        if (details != null) {
            return details.isItemValid(slotIndex, AEItemKey.of(i), molecularAssembler.level!!)
        }
        return false
    }

    override fun setupConfig() {
        val mac = this.host.getSubInventory(MachineAssemblerBlockEntity.INV_MAIN)!!

        for (i in 0 until MachineAssemblerBlockEntity.GRID_SLOTS - 1) {
            this.addSlot(MachineAssemblerPatternSlot(this, mac, i), SlotSemantics.MACHINE_CRAFTING_GRID)
        }

        val machineInv = this.host.getSubInventory(MachineAssemblerBlockEntity.INV_MACHINE)!!
        this.addSlot(MachineAssemblerMachineSlot(machineInv, 0), MachineSlotSemantics.MACHINE)

        encodedPatternSlot = this.addSlot(
            MachineAssemblerPatternItemSlot(mac, 10),
            SlotSemantics.ENCODED_PATTERN,
        )

        this.addSlot(OutputSlot(mac, MachineAssemblerBlockEntity.OUTPUT_SLOT, null), SlotSemantics.MACHINE_OUTPUT)
    }

    override fun broadcastChanges() {
        this.craftProgress = this.molecularAssembler.getCraftingProgress()
        this.standardDetectAndSendChanges()
    }

    override fun getCurrentProgress(): Int = this.craftProgress

    override fun getMaxProgress(): Int = MAX_CRAFT_PROGRESS

    override fun onSlotChange(s: Slot) {
        if (s === encodedPatternSlot) {
            for (otherSlot in slots) {
                if (otherSlot !== s && otherSlot is AppEngSlot) {
                    otherSlot.resetCachedValidation()
                }
            }
        }
    }
}

/** Grid slot that only accepts items matching the current machine plan. */
class MachineAssemblerPatternSlot(
    private val menu: MachineAssemblerMenu,
    inv: InternalInventory,
    invSlot: Int,
) : AppEngSlot(inv, invSlot), IOptionalSlot {

    override fun mayPlace(stack: ItemStack): Boolean {
        return super.mayPlace(stack) && this.menu.isValidItemForSlot(this.containerSlot, stack)
    }

    override fun getCurrentValidationState(): Boolean {
        val stack = item
        return stack.isEmpty || mayPlace(stack)
    }

    override fun isRenderDisabled(): Boolean = true

    override fun isSlotEnabled(): Boolean {
        if (!inventory.getStackInSlot(containerSlot).isEmpty) {
            return true
        }

        val slot = containerSlot
        val pattern = menu.host.getCurrentPattern()
        return slot >= 0 && slot < MachineAssemblerBlockEntity.GRID_SLOTS - 1 && pattern != null && pattern.isSlotEnabled(
            slot
        )
    }

    override fun getBackgroundPos(): Point = Point(x - 1, y - 1)
}

/** Slot that accepts items recognized as a machine. */
class MachineAssemblerMachineSlot(inv: InternalInventory, invSlot: Int) : AppEngSlot(inv, invSlot) {
    override fun mayPlace(stack: ItemStack): Boolean {
        return super.mayPlace(stack) && MachineTypeRegistry.byItem(stack) != null
    }
}

/** Slot that only accepts encoded machine patterns. */
class MachineAssemblerPatternItemSlot(inv: InternalInventory, invSlot: Int) :
    AppEngSlot(inv, invSlot) {
    override fun mayPlace(stack: ItemStack): Boolean {
        return super.mayPlace(stack) && stack.item is MachinePatternItem
    }
}
