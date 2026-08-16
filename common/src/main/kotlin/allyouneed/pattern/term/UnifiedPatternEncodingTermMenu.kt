package allyouneed.pattern.term

import allyouneed.logic.machine.MachineType
import allyouneed.pattern.ModItems
import allyouneed.pattern.adaptive.AdaptivePatternItem
import allyouneed.pattern.adaptive.AdaptiveStatisticalPattern
import allyouneed.pattern.machine.MachinePatternItem
import allyouneed.pattern.machine.MachinePatternTags
import allyouneed.pattern.pseudo.PseudoPatternItem
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.GenericStack
import appeng.helpers.IPatternTerminalMenuHost
import appeng.menu.guisync.GuiSync
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.me.items.PatternEncodingTermMenu
import appeng.parts.encoding.EncodingMode
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.TransientCraftingContainer
import net.minecraft.world.item.ItemStack

class UnifiedPatternEncodingTermMenu(
    containerId: Int,
    playerInventory: Inventory,
    host: IPatternTerminalMenuHost?,
) : PatternEncodingTermMenu(TYPE, containerId, playerInventory, host, true) {

    private val encodingLogic = host!!.logic as UnifiedPatternEncodingLogic

    @GuiSync(88)
    var kind: EncodingKind = encodingLogic.kind
        private set

    @GuiSync(89)
    var selectedMachineIndex: Int = MachineType.indexById(encodingLogic.virtualMachineTypeId)
        private set

    @GuiSync(90)
    var probability: Double = encodingLogic.probability
        private set

    @GuiSync(91)
    var timeout: Int = encodingLogic.timeout
        private set

    val selectedMachine: MachineType?
        get() = MachineType.getAll().getOrNull(selectedMachineIndex)

    init {
        registerClientAction(ACTION_SET_KIND, EncodingKind::class.java) { setKind(it) }
        registerClientAction(ACTION_SET_MACHINE, Int::class.javaObjectType) { setMachineIndex(it) }
        registerClientAction(ACTION_SET_PROBABILITY, Double::class.javaObjectType) { setProbability(it) }
        registerClientAction(ACTION_SET_TIMEOUT, Int::class.javaObjectType) { setTimeout(it) }
    }

    fun setKind(kind: EncodingKind) {
        this.kind = kind
        encodingLogic.setKind(kind)
        if (isClientSide) {
            sendClientAction(ACTION_SET_KIND, kind)
        }
        broadcastChanges()
    }

    fun cycleMachine() {
        val machines = MachineType.getAll()
        if (machines.size <= 1) return
        setMachineIndex(selectedMachineIndex + 1)
    }

    fun setMachineIndex(index: Int) {
        val machines = MachineType.getAll()
        if (machines.isEmpty()) return
        val clamped = Math.floorMod(index, machines.size)
        selectedMachineIndex = clamped
        encodingLogic.setVirtualMachineType(machines[clamped].id)
        if (isClientSide) {
            sendClientAction(ACTION_SET_MACHINE, clamped)
        }
        broadcastChanges()
    }

    fun setProbability(probability: Double) {
        this.probability = probability.coerceIn(0.01, 1.0)
        encodingLogic.setProbability(this.probability)
        if (isClientSide) {
            sendClientAction(ACTION_SET_PROBABILITY, this.probability)
        }
    }

    fun setTimeout(timeout: Int) {
        this.timeout = timeout.coerceIn(1, 3600)
        encodingLogic.setTimeout(this.timeout)
        if (isClientSide) {
            sendClientAction(ACTION_SET_TIMEOUT, this.timeout)
        }
    }

    override fun setMode(mode: EncodingMode) {
        super.setMode(EncodingMode.PROCESSING)
        encodingLogic.mode = EncodingMode.PROCESSING
    }

    override fun broadcastChanges() {
        super.broadcastChanges()
        kind = encodingLogic.kind
        selectedMachineIndex = MachineType.indexById(encodingLogic.virtualMachineTypeId)
        probability = encodingLogic.probability
        timeout = encodingLogic.timeout
    }

    override fun onSlotChange(slot: Slot) {
        super.onSlotChange(slot)
        val encodedStack = encodingLogic.encodedPatternInv.getStackInSlot(0)
        if (encodedStack.isEmpty) return
        when (encodedStack.item) {
            is MachinePatternItem -> {
                val id = encodedStack.tag?.getString(MachinePatternTags.MACHINE_TYPE)
                if (id != null && MachineType.byId(id) != null) {
                    encodingLogic.setVirtualMachineType(id)
                    selectedMachineIndex = MachineType.indexById(id)
                    kind = EncodingKind.MACHINE
                }
            }
            is AdaptivePatternItem -> {
                val tag = encodedStack.tag ?: return
                probability = tag.getDouble(AdaptiveStatisticalPattern.NBT_KEY_PROBABILITY).coerceIn(0.01, 1.0)
                timeout = tag.getInt(AdaptiveStatisticalPattern.NBT_KEY_TIMEOUT).coerceAtLeast(1)
                encodingLogic.setProbability(probability)
                encodingLogic.setTimeout(timeout)
                kind = EncodingKind.PROBABILITY
            }
            is PseudoPatternItem -> {
                kind = EncodingKind.PSEUDO
            }
        }
    }

    override fun encode() {
        if (isClientSide) {
            sendClientAction("encode")
            return
        }
        when (kind) {
            EncodingKind.PROCESSING -> super.encode()
            EncodingKind.MACHINE -> encodeMachinePattern()
            EncodingKind.PROBABILITY -> encodeProbabilityPattern()
            EncodingKind.PSEUDO -> encodePseudoPattern()
        }
    }

    private fun encodeMachinePattern() {
        val machineType = encodingLogic.virtualMachineType
        if (machineType == null) {
            failEncode()
            return
        }
        val inputSlots = machineType.inputSlots
        val inputs = arrayOfNulls<GenericStack>(inputSlots)
        var hasInput = false
        for (i in 0 until inputSlots) {
            inputs[i] = encodingLogic.encodedInputInv.getStack(i)
            if (inputs[i] != null) hasInput = true
        }
        if (!hasInput) {
            failEncode()
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
                failEncode()
                return
            }
            container.setItem(i, key.toStack(gs.amount().toInt()))
        }
        val output = machineType.resolve(player.level(), container)
        val encodedOutput = output?.let { GenericStack.fromItemStack(it) }
        if (encodedOutput == null) {
            failEncode()
            return
        }
        commitEncoded(ModItems.MACHINE_PATTERN.encode(machineType.id, inputs, encodedOutput))
    }

    private fun encodeProbabilityPattern() {
        val inputsInv = encodingLogic.encodedInputInv
        val outputsInv = encodingLogic.encodedOutputInv
        val sparseInputs = ArrayList<GenericStack>(inputsInv.size())
        for (i in 0 until inputsInv.size()) {
            inputsInv.getStack(i)?.let { sparseInputs.add(it) }
        }
        if (sparseInputs.isEmpty()) {
            failEncode()
            return
        }
        val output = (0 until outputsInv.size()).firstNotNullOfOrNull { outputsInv.getStack(it) }
        if (output == null) {
            failEncode()
            return
        }
        commitEncoded(
            ModItems.ADAPTIVE_PATTERN.encode(sparseInputs, output, probability, timeout),
        )
    }

    private fun encodePseudoPattern() {
        val inputsInv = encodingLogic.encodedInputInv
        val inputs = arrayOfNulls<GenericStack>(inputsInv.size())
        var first: GenericStack? = null
        var hasInput = false
        for (i in 0 until inputsInv.size()) {
            val stack = inputsInv.getStack(i)
            inputs[i] = stack
            if (stack != null) {
                hasInput = true
                if (first == null) first = stack
            }
        }
        if (!hasInput) {
            failEncode()
            return
        }
        val icon = (first?.what() as? AEItemKey)?.toStack(1)
        commitEncoded(ModItems.PSEUDO_PATTERN.encode(null, icon, inputs))
    }

    private fun commitEncoded(encodedPattern: ItemStack) {
        val encodedInv = encodingLogic.encodedPatternInv
        val blankInv = encodingLogic.blankPatternInv
        val existingEncoded = encodedInv.getStackInSlot(0)
        if (!existingEncoded.isEmpty) {
            if (!PatternDetailsHelper.isEncodedPattern(existingEncoded)) {
                failEncode()
                return
            }
            encodedInv.setItemDirect(0, encodedPattern)
        } else {
            val blankPattern = blankInv.getStackInSlot(0)
            if (blankPattern.isEmpty) {
                failEncode()
                return
            }
            blankPattern.shrink(1)
            blankInv.setItemDirect(0, if (blankPattern.isEmpty) ItemStack.EMPTY else blankPattern)
            encodedInv.setItemDirect(0, encodedPattern)
        }
        encodingLogic.saveChanges()
        broadcastChanges()
    }

    private fun failEncode() {
        player.displayClientMessage(Component.translatable("gui.ae2isallyouneed.encode_failed"), true)
        broadcastChanges()
    }

    companion object {
        val TYPE: MenuType<UnifiedPatternEncodingTermMenu> =
            MenuTypeBuilder.create(::UnifiedPatternEncodingTermMenu, IPatternTerminalMenuHost::class.java)
                .build("pattern_encoding_terminal")

        private const val ACTION_SET_KIND = "set_kind"
        private const val ACTION_SET_MACHINE = "set_machine"
        private const val ACTION_SET_PROBABILITY = "set_adaptive_probability"
        private const val ACTION_SET_TIMEOUT = "set_adaptive_timeout"
    }
}
