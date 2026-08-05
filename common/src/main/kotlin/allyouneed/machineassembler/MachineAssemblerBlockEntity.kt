package allyouneed.machineassembler

import allyouneed.logic.machine.MachineType
import allyouneed.logic.machine.MachineTypeRegistry
import allyouneed.pattern.machine.MachinePatternDetails
import appeng.api.config.Actionable
import appeng.api.config.PowerMultiplier
import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.implementations.IPowerChannelState
import appeng.api.implementations.blockentities.ICraftingMachine
import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.api.inventories.InternalInventory
import appeng.api.networking.IGridNode
import appeng.api.networking.IGridNodeListener
import appeng.api.networking.ticking.IGridTickable
import appeng.api.networking.ticking.TickRateModulation
import appeng.api.networking.ticking.TickingRequest
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.KeyCounter
import appeng.api.upgrades.IUpgradeInventory
import appeng.api.upgrades.IUpgradeableObject
import appeng.api.upgrades.UpgradeInventories
import appeng.api.util.AECableType
import appeng.blockentity.grid.AENetworkInvBlockEntity
import appeng.core.AELog
import appeng.core.AppEng
import appeng.core.definitions.AEItems
import appeng.crafting.CraftingEvent
import appeng.menu.AutoCraftingMenu
import appeng.util.inv.AppEngInternalInventory
import appeng.util.inv.CombinedInternalInventory
import appeng.util.inv.FilteredInternalInventory
import appeng.util.inv.filter.IAEItemFilter
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.TransientCraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * A molecular assembler with an additional machine slot. It only executes [MachinePatternDetails]
 * whose machine type matches the installed machine, so patterns are only ever pushed to matching machines.
 */
class MachineAssemblerBlockEntity(
    blockEntityType: BlockEntityType<*>,
    pos: BlockPos,
    blockState: BlockState,
) : AENetworkInvBlockEntity(blockEntityType, pos, blockState),
    IUpgradeableObject,
    IGridTickable,
    ICraftingMachine,
    IPowerChannelState {

    companion object {
        const val GRID_SLOTS = 9 + 1
        const val PATTERN_SLOTS = 1
        const val MACHINE_SLOTS = 1
        const val OUTPUT_SLOT = 9

        val INV_MAIN: ResourceLocation = AppEng.makeId("molecular_assembler")
        val INV_MACHINE: ResourceLocation = AppEng.makeId("machine")
    }

    private val craftingInv: CraftingContainer
    private val gridInv = AppEngInternalInventory(this, GRID_SLOTS, 1)
    private val patternInv = AppEngInternalInventory(this, PATTERN_SLOTS, 1)
    private val machineInv = AppEngInternalInventory(this, MACHINE_SLOTS, 1)
    private val gridInvExt: InternalInventory = FilteredInternalInventory(gridInv, CraftingGridFilter())
    private val internalInv: InternalInventory =
        CombinedInternalInventory(gridInv, patternInv, machineInv)
    private val upgrades: IUpgradeInventory

    private var isPowered = false
    private var pushDirection: Direction? = null
    private var myPattern: ItemStack = ItemStack.EMPTY
    private var myPlan: MachinePatternDetails? = null
    private var progress = 0.0
    private var isAwake = false
    private var forcePlan = false
    private var reboot = true

    init {
        mainNode
            .setIdlePowerUsage(0.0)
            .addService(IGridTickable::class.java, this)
        upgrades = UpgradeInventories.forMachine(
            MachineAssemblerRegistration.block,
            getUpgradeSlots(),
            { saveChanges() },
        )
        craftingInv = TransientCraftingContainer(AutoCraftingMenu(), 3, 3)
    }

    private fun getUpgradeSlots(): Int = 5

    fun installedMachine(): MachineType? = MachineTypeRegistry.byItem(machineInv.getStackInSlot(0))

    override fun getCraftingMachineInfo(): PatternContainerGroup {
        val machineType = installedMachine()
        val name = if (machineType != null) {
            Component.literal(machineType.name)
        } else if (hasCustomName()) {
            customName
        } else {
            MachineAssemblerRegistration.block.asItem().description
        }
        val icon = if (machineType != null) {
            AEItemKey.of(machineType.icon)
        } else {
            AEItemKey.of(MachineAssemblerRegistration.block)
        }

        val tooltip: List<Component>
        val accelerationCards = getInstalledUpgrades(AEItems.SPEED_CARD)
        tooltip = if (accelerationCards == 0) {
            emptyList()
        } else {
            listOf(
                Component.translatable(
                    "gui.ae2isallyouneed.machine_speed_cards",
                    accelerationCards,
                ),
            )
        }

        return PatternContainerGroup(icon, name, tooltip)
    }

    override fun pushPattern(patternDetails: IPatternDetails, table: Array<KeyCounter>, where: Direction): Boolean {
        if (this.myPattern.isEmpty) {
            val isEmpty = this.gridInv.isEmpty && this.patternInv.isEmpty

            if (isEmpty && patternDetails is MachinePatternDetails) {
                val pattern = patternDetails as MachinePatternDetails
                val installed = installedMachine()
                if (installed == null || pattern.machineType != installed) {
                    return false
                }

                this.forcePlan = true
                this.myPlan = pattern
                this.pushDirection = where

                this.fillGrid(table, pattern)

                this.updateSleepiness()
                this.saveChanges()
                return true
            }
        }
        return false
    }

    private fun fillGrid(table: Array<KeyCounter>, adapter: MachinePatternDetails) {
        adapter.fillCraftingGrid(table) { slot, stack ->
            this.gridInv.setItemDirect(slot, stack)
        }

        for (list in table) {
            list.removeZeros()
            if (!list.isEmpty) {
                throw RuntimeException("Could not fill grid with some items, including " + list.iterator().next())
            }
        }
    }

    private fun updateSleepiness() {
        val wasEnabled = this.isAwake
        this.isAwake = this.myPlan != null && this.hasMats() || this.canPush()
        if (wasEnabled != this.isAwake) {
            mainNode.ifPresent { grid, node ->
                if (this.isAwake) {
                    grid.tickManager.wakeDevice(node)
                } else {
                    grid.tickManager.sleepDevice(node)
                }
            }
        }
    }

    private fun canPush(): Boolean = !this.gridInv.getStackInSlot(OUTPUT_SLOT).isEmpty

    private fun hasMats(): Boolean {
        if (this.myPlan == null) {
            return false
        }

        for (x in 0 until this.craftingInv.containerSize) {
            this.craftingInv.setItem(x, this.gridInv.getStackInSlot(x))
        }

        return !this.myPlan!!.assemble(this.craftingInv, this.level!!).isEmpty
    }

    override fun acceptsPlans(): Boolean = this.patternInv.isEmpty

    override fun readFromStream(data: FriendlyByteBuf): Boolean {
        val c = super.readFromStream(data)
        val oldPower = this.isPowered
        this.isPowered = data.readBoolean()
        return this.isPowered != oldPower || c
    }

    override fun writeToStream(data: FriendlyByteBuf) {
        super.writeToStream(data)
        data.writeBoolean(this.isPowered)
    }

    override fun saveAdditional(data: CompoundTag) {
        super.saveAdditional(data)
        if (this.forcePlan) {
            val pattern = myPlan?.definition?.toStack() ?: myPattern
            if (!pattern.isEmpty) {
                val compound = CompoundTag()
                pattern.save(compound)
                data.put("myPlan", compound)
                data.putInt("pushDirection", (this.pushDirection ?: Direction.DOWN).ordinal)
            }
        }

        this.upgrades.writeToNBT(data, "upgrades")
    }

    override fun loadTag(data: CompoundTag) {
        super.loadTag(data)

        this.forcePlan = false
        this.myPattern = ItemStack.EMPTY
        this.myPlan = null

        if (data.contains("myPlan")) {
            val pattern = ItemStack.of(data.getCompound("myPlan"))
            if (!pattern.isEmpty) {
                this.forcePlan = true
                this.myPattern = pattern
                this.pushDirection = Direction.entries.toTypedArray()[data.getInt("pushDirection")]
            }
        }

        this.upgrades.readFromNBT(data, "upgrades")
        this.recalculatePlan()
    }

    private fun recalculatePlan() {
        this.reboot = true

        if (this.forcePlan) {
            if (level != null && myPlan == null) {
                if (!myPattern.isEmpty) {
                    if (PatternDetailsHelper.decodePattern(myPattern, level, false) is MachinePatternDetails) {
                        this.myPlan = PatternDetailsHelper.decodePattern(myPattern, level, false) as MachinePatternDetails
                    }
                }

                this.myPattern = ItemStack.EMPTY

                if (myPlan == null) {
                    AELog.warn("Unable to restore auto-crafting pattern after load: %s", myPattern.tag)
                    this.forcePlan = false
                }
            }

            return
        }

        val stack = this.patternInv.getStackInSlot(0)

        var reset = true

        if (!stack.isEmpty) {
            if (ItemStack.isSameItemSameTags(stack, this.myPattern)) {
                reset = false
            } else if (PatternDetailsHelper.decodePattern(stack, level, false) is MachinePatternDetails) {
                reset = false
                this.progress = 0.0
                this.myPattern = stack
                this.myPlan = PatternDetailsHelper.decodePattern(stack, level, false) as MachinePatternDetails
            }
        }

        if (reset) {
            this.progress = 0.0
            this.forcePlan = false
            this.myPlan = null
            this.myPattern = ItemStack.EMPTY
            this.pushDirection = null
        }

        this.updateSleepiness()
    }

    override fun getCableConnectionType(dir: Direction): AECableType = AECableType.COVERED

    override fun getSubInventory(id: ResourceLocation): InternalInventory? {
        return when (id) {
            UPGRADES -> this.upgrades
            INV_MAIN -> this.internalInv
            INV_MACHINE -> this.machineInv
            else -> super.getSubInventory(id)
        }
    }

    override fun getInternalInventory(): InternalInventory = this.internalInv

    override fun getExposedInventoryForSide(side: Direction): InternalInventory = this.gridInvExt

    override fun onChangeInventory(inv: InternalInventory, slot: Int) {
        if (inv === this.gridInv || inv === this.patternInv) {
            this.recalculatePlan()
        }
        if (inv === this.machineInv) {
            // A different machine invalidates any running plan
            this.recalculatePlan()
            this.saveChanges()
        }
    }

    fun getCraftingProgress(): Int = this.progress.toInt()

    override fun addAdditionalDrops(level: Level, pos: BlockPos, drops: MutableList<ItemStack>) {
        super.addAdditionalDrops(level, pos, drops)

        for (upgrade in upgrades) {
            drops.add(upgrade)
        }
    }

    override fun clearContent() {
        super.clearContent()
        upgrades.clear()
    }

    override fun getTickingRequest(node: IGridNode): TickingRequest {
        this.recalculatePlan()
        this.updateSleepiness()
        return TickingRequest(1, 1, !this.isAwake, false)
    }

    override fun tickingRequest(node: IGridNode, ticksSinceLastCall: Int): TickRateModulation {
        if (!this.gridInv.getStackInSlot(OUTPUT_SLOT).isEmpty) {
            this.pushOut(this.gridInv.getStackInSlot(OUTPUT_SLOT))

            if (this.gridInv.getStackInSlot(OUTPUT_SLOT).isEmpty) {
                this.saveChanges()
            }

            this.ejectHeldItems()
            this.updateSleepiness()
            this.progress = 0.0
            return if (this.isAwake) TickRateModulation.IDLE else TickRateModulation.SLEEP
        }

        if (this.myPlan == null) {
            this.updateSleepiness()
            return TickRateModulation.SLEEP
        }

        val elapsed = if (this.reboot) 1 else ticksSinceLastCall

        if (!this.isAwake) {
            return TickRateModulation.SLEEP
        }

        this.reboot = false
        var speed = 10
        when (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
            0 -> this.progress += this.userPower(elapsed, 10, 1.0)
            1 -> {
                speed = 13
                this.progress += this.userPower(elapsed, speed, 1.3)
            }
            2 -> {
                speed = 17
                this.progress += this.userPower(elapsed, speed, 1.7)
            }
            3 -> {
                speed = 20
                this.progress += this.userPower(elapsed, speed, 2.0)
            }
            4 -> {
                speed = 25
                this.progress += this.userPower(elapsed, speed, 2.5)
            }
            5 -> {
                speed = 50
                this.progress += this.userPower(elapsed, speed, 5.0)
            }
        }

        if (this.progress >= 100) {
            for (x in 0 until this.craftingInv.containerSize) {
                this.craftingInv.setItem(x, this.gridInv.getStackInSlot(x))
            }

            this.progress = 0.0
            val output = this.myPlan!!.assemble(this.craftingInv, this.level!!)
            if (!output.isEmpty) {
                CraftingEvent.fireAutoCraftingEvent(this.level, this.myPlan, output, this.craftingInv)

                val craftingRemainders = this.myPlan!!.getRemainingItems(this.craftingInv, this.level!!)

                this.pushOut(output.copy())

                for (x in 0 until this.craftingInv.containerSize) {
                    this.gridInv.setItemDirect(x, craftingRemainders[x])
                }

                if (this.patternInv.isEmpty) {
                    this.forcePlan = false
                    this.myPlan = null
                    this.pushDirection = null
                }

                this.ejectHeldItems()

                this.saveChanges()
                this.updateSleepiness()
                return if (this.isAwake) TickRateModulation.IDLE else TickRateModulation.SLEEP
            }
        }

        return TickRateModulation.FASTER
    }

    private fun ejectHeldItems() {
        if (this.gridInv.getStackInSlot(OUTPUT_SLOT).isEmpty) {
            for (x in 0 until GRID_SLOTS - 1) {
                val held = this.gridInv.getStackInSlot(x)
                if (!held.isEmpty
                    && (this.myPlan == null || !this.myPlan!!.isItemValid(x, AEItemKey.of(held), this.level!!))
                ) {
                    this.gridInv.setItemDirect(OUTPUT_SLOT, held)
                    this.gridInv.setItemDirect(x, ItemStack.EMPTY)
                    this.saveChanges()
                    return
                }
            }
        }
    }

    private fun userPower(ticksPassed: Int, bonusValue: Int, acceleratorTax: Double): Int {
        val grid = mainNode.grid
        return if (grid != null) {
            (grid.energyService.extractAEPower(
                ticksPassed * bonusValue * acceleratorTax,
                Actionable.MODULATE,
                PowerMultiplier.CONFIG,
            ) / acceleratorTax).toInt()
        } else {
            0
        }
    }

    private fun pushOut(output: ItemStack) {
        var remaining = output
        if (this.pushDirection == null) {
            for (d in Direction.entries) {
                remaining = this.pushTo(remaining, d)
            }
        } else {
            remaining = this.pushTo(remaining, this.pushDirection!!)
        }

        if (remaining.isEmpty && this.forcePlan) {
            this.forcePlan = false
            this.recalculatePlan()
        }

        this.gridInv.setItemDirect(OUTPUT_SLOT, remaining)
    }

    private fun pushTo(output: ItemStack, d: Direction): ItemStack {
        if (output.isEmpty) {
            return output
        }

        val te: BlockEntity = this.level?.getBlockEntity(this.worldPosition.relative(d)) ?: return output

        val adaptor = InternalInventory.wrapExternal(te, d.opposite) ?: return output

        val size = output.count
        val remaining = adaptor.addItems(output)
        val newSize = if (remaining.isEmpty) 0 else remaining.count

        if (size != newSize) {
            this.saveChanges()
        }

        return remaining
    }

    override fun onMainNodeStateChanged(reason: IGridNodeListener.State) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            var newState = false

            val grid = mainNode.grid
            if (grid != null) {
                newState = this.mainNode.isPowered && grid.energyService.extractAEPower(
                    1.0,
                    Actionable.SIMULATE,
                    PowerMultiplier.CONFIG,
                ) > 0.0001
            }

            if (newState != this.isPowered) {
                this.isPowered = newState
                this.markForUpdate()
            }
        }
    }

    override fun isPowered(): Boolean = this.isPowered

    override fun isActive(): Boolean = this.isPowered

    override fun getUpgrades(): IUpgradeInventory = upgrades

    fun getCurrentPattern(): MachinePatternDetails? {
        return if (isClientSide) {
            val patternItem = patternInv.getStackInSlot(0)
            PatternDetailsHelper.decodePattern(patternItem, level) as? MachinePatternDetails
        } else {
            myPlan
        }
    }

    private inner class CraftingGridFilter : IAEItemFilter {
        private fun hasPattern(): Boolean =
            this@MachineAssemblerBlockEntity.myPlan != null && !this@MachineAssemblerBlockEntity.patternInv.isEmpty

        override fun allowExtract(inv: InternalInventory, slot: Int, amount: Int): Boolean = slot == OUTPUT_SLOT

        override fun allowInsert(inv: InternalInventory, slot: Int, stack: ItemStack): Boolean {
            if (slot >= OUTPUT_SLOT) {
                return false
            }

            if (this.hasPattern()) {
                return this@MachineAssemblerBlockEntity.myPlan!!.isItemValid(
                    slot,
                    AEItemKey.of(stack),
                    this@MachineAssemblerBlockEntity.level!!,
                )
            }
            return false
        }
    }
}
