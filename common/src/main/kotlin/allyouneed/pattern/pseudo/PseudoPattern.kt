package allyouneed.pattern.pseudo

import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.GenericStack
import appeng.crafting.pattern.EncodedPatternItem
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import allyouneed.pattern.AEPatternUtil

class PseudoPatternItem(props: Item.Properties) : EncodedPatternItem(props) {

    fun encode(displayName: Component?, icon: ItemStack?, inputs: Array<GenericStack?>): ItemStack {
        val tag = CompoundTag()
        if (displayName != null) tag.putString("displayName", Component.Serializer.toJson(displayName))
        if (icon != null && !icon.isEmpty) {
            val iconTag = CompoundTag()
            icon.save(iconTag)
            tag.put("iconItem", iconTag)
        }
        val inList = ListTag()
        for (gs in inputs) if (gs != null) inList.add(GenericStack.writeTag(gs))
        tag.put("in", inList)
        val stack = ItemStack(this)
        stack.tag = tag
        return stack
    }

    override fun decode(stack: ItemStack, level: Level, tryRecovery: Boolean): IPatternDetails? {
        val key = AEItemKey.of(stack) ?: return null
        return decode(key, level)
    }

    override fun decode(what: AEItemKey, level: Level): IPatternDetails? {
        if (!what.hasTag()) return null
        return try {
            AEPseudoPattern(what)
        } catch (e: Exception) {
            null
        }
    }
}

class AEPseudoPattern(private val definition: AEItemKey) : IPatternDetails {

    val displayName: Component?
    val icon: ItemStack?
    private val sparseInputs: Array<GenericStack?>
    private val inputs: Array<IPatternDetails.IInput>

    init {
        val tag = definition.tag ?: throw IllegalStateException("Pseudo pattern without tag")
        displayName = if (tag.contains("displayName")) Component.Serializer.fromJson(tag.getString("displayName")) else null
        icon = if (tag.contains("iconItem")) ItemStack.of(tag.getCompound("iconItem")) else null
        sparseInputs = readArray(tag, "in")
        val condensed = AEPatternUtil.condenseStacks(sparseInputs)
        inputs = Array(condensed.size) { i -> Input(condensed[i]) }
    }

    private fun readArray(tag: CompoundTag, key: String): Array<GenericStack?> {
        if (!tag.contains(key)) return emptyArray()
        val list = tag.getList(key, Tag.TAG_COMPOUND.toInt())
        val arr = arrayOfNulls<GenericStack>(list.size)
        for (i in 0 until list.size) arr[i] = GenericStack.readTag(list.getCompound(i))
        return arr
    }

    override fun getDefinition(): AEItemKey = definition
    override fun getInputs(): Array<IPatternDetails.IInput> = inputs
    override fun getOutputs(): Array<GenericStack> = emptyArray() // no real outputs -> no auto-crafting

    private class Input(private val stack: GenericStack) : IPatternDetails.IInput {
        override fun getMultiplier(): Long = stack.amount()
        override fun getPossibleInputs(): Array<GenericStack> = arrayOf(stack)
        override fun isValid(what: appeng.api.stacks.AEKey, level: Level): Boolean = what.matches(stack)
        override fun getRemainingKey(what: appeng.api.stacks.AEKey): appeng.api.stacks.AEKey? = null
    }
}
