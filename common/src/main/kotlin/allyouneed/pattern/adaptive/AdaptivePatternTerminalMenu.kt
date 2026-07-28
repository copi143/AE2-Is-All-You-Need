package allyouneed.pattern.adaptive

import allyouneed.pattern.ModItems
import appeng.api.stacks.GenericStack
import appeng.helpers.IPatternTerminalMenuHost
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.me.items.PatternEncodingTermMenu
import appeng.parts.encoding.EncodingMode
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class AdaptivePatternTerminalMenu(
    containerId: Int,
    playerInventory: Inventory,
    host: IPatternTerminalMenuHost?
) : PatternEncodingTermMenu(TYPE, containerId, playerInventory, host, true) {

    companion object {
        val TYPE: MenuType<AdaptivePatternTerminalMenu> = MenuTypeBuilder
            .create(::AdaptivePatternTerminalMenu, IPatternTerminalMenuHost::class.java)
            .build("adaptive_pattern_terminal")

        private const val ACTION_SET_PROBABILITY = "set_adaptive_probability"
        private const val ACTION_SET_TIMEOUT = "set_adaptive_timeout"
    }

    private val encodingLogic: AdaptivePatternEncodingLogic
    var probability: Double = 0.8
        private set
    var timeout: Int = 30
        private set

    init {
        encodingLogic = host!!.logic as AdaptivePatternEncodingLogic
        probability = encodingLogic.probability
        timeout = encodingLogic.timeout

        registerClientAction(ACTION_SET_PROBABILITY, Double::class.java) { setProbability(it) }
        registerClientAction(ACTION_SET_TIMEOUT, Int::class.java) { setTimeout(it) }
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

    override fun onServerDataSync() {
        super.onServerDataSync()
    }

    override fun onSlotChange(slot: Slot) {
        super.onSlotChange(slot)
        val encodedStack = encodingLogic.encodedPatternInv.getStackInSlot(0)
        if (!encodedStack.isEmpty && encodedStack.item is AdaptivePatternItem) {
            val tag = encodedStack.tag
            if (tag != null) {
                probability = tag.getDouble(AdaptiveStatisticalPattern.NBT_KEY_PROBABILITY).coerceIn(0.01, 1.0)
                timeout = tag.getInt(AdaptiveStatisticalPattern.NBT_KEY_TIMEOUT).coerceAtLeast(1)
                encodingLogic.setProbability(probability)
                encodingLogic.setTimeout(timeout)
            }
        }
    }

    override fun encode() {
        if (isClientSide) {
            sendClientAction("encode")
            return
        }

        if (mode != EncodingMode.PROCESSING) {
            super.encode()
            return
        }

        encodeAdaptivePattern()
    }

    private fun encodeAdaptivePattern() {
        val logic = encodingLogic
        val inputsInv = logic.encodedInputInv
        val outputsInv = logic.encodedOutputInv

        val sparseInputs = ArrayList<GenericStack>(inputsInv.size())
        var hasInput = false
        for (i in 0 until inputsInv.size()) {
            val stack = inputsInv.getStack(i)
            if (stack != null) {
                sparseInputs.add(stack)
                hasInput = true
            }
        }
        if (!hasInput) {
            super.encode()
            broadcastChanges()
            return
        }

        val sparseOutputs = ArrayList<GenericStack>(outputsInv.size())
        for (i in 0 until outputsInv.size()) {
            val stack = outputsInv.getStack(i)
            if (stack != null) {
                sparseOutputs.add(stack)
            }
        }
        if (sparseOutputs.isEmpty()) {
            super.encode()
            broadcastChanges()
            return
        }

        val output = sparseOutputs[0]
        val encodedPattern = ModItems.ADAPTIVE_PATTERN.encode(sparseInputs, output, probability, timeout)

        val encodedInv = logic.encodedPatternInv
        val blankInv = logic.blankPatternInv
        val existingEncoded = encodedInv.getStackInSlot(0)

        if (!existingEncoded.isEmpty) {
            if (!appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(existingEncoded)) {
                return
            }
            encodedInv.setItemDirect(0, encodedPattern)
        } else {
            val blankPattern = blankInv.getStackInSlot(0)
            if (blankPattern.isEmpty) {
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
