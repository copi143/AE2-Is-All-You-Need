package allyouneed.pattern.term

import appeng.api.parts.IPartItem
import appeng.api.parts.IPartModel
import appeng.helpers.IPatternTerminalLogicHost
import appeng.helpers.IPatternTerminalMenuHost
import appeng.items.parts.PartModels
import appeng.parts.PartModel
import appeng.parts.encoding.PatternEncodingLogic
import appeng.parts.reporting.AbstractTerminalPart
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

class UnifiedPatternEncodingTermPart(
    partItem: IPartItem<*>,
) : AbstractTerminalPart(partItem), IPatternTerminalLogicHost, IPatternTerminalMenuHost {

    private val encodingLogic = UnifiedPatternEncodingLogic(this)

    override fun addAdditionalDrops(drops: MutableList<ItemStack>, wrenched: Boolean) {
        super.addAdditionalDrops(drops, wrenched)
        for (stack in encodingLogic.blankPatternInv) {
            drops.add(stack)
        }
        for (stack in encodingLogic.encodedPatternInv) {
            drops.add(stack)
        }
    }

    override fun clearContent() {
        super.clearContent()
        encodingLogic.blankPatternInv.clear()
        encodingLogic.encodedPatternInv.clear()
    }

    override fun readFromNBT(data: CompoundTag) {
        super.readFromNBT(data)
        encodingLogic.readFromNBT(data)
    }

    override fun writeToNBT(data: CompoundTag) {
        super.writeToNBT(data)
        encodingLogic.writeToNBT(data)
    }

    override fun getMenuType(player: Player): MenuType<*> = UnifiedPatternEncodingTermMenu.TYPE

    override fun getStaticModels(): IPartModel = selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL)

    override fun getLogic(): PatternEncodingLogic = encodingLogic

    override fun markForSave() {
        host?.markForSave()
    }

    companion object {
        private val MODEL_OFF = ResourceLocation("ae2", "part/pattern_encoding_terminal_off")
        private val MODEL_ON = ResourceLocation("ae2", "part/pattern_encoding_terminal_on")
        private val MODEL_BASE = ResourceLocation("ae2", "part/display_base")
        private val MODEL_STATUS_OFF = ResourceLocation("ae2", "part/display_status_off")
        private val MODEL_STATUS_ON = ResourceLocation("ae2", "part/display_status_on")
        private val MODEL_STATUS_HAS_CHANNEL = ResourceLocation("ae2", "part/display_status_has_channel")

        @JvmField
        @PartModels
        val MODELS_OFF: IPartModel = PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF)

        @JvmField
        @PartModels
        val MODELS_ON: IPartModel = PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON)

        @JvmField
        @PartModels
        val MODELS_HAS_CHANNEL: IPartModel = PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL)
    }
}
