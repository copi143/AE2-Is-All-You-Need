package allyouneed.logic.aekey

import allyouneed.util.MODID
import allyouneed.util.rlAE
import appeng.api.stacks.AEKey
import appeng.api.stacks.AEKeyType
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

data class VirtualKey(val primary: Long, val secondary: Long = 0) : AEKey() {
    override fun getType(): Type = Type
    override fun dropSecondary(): VirtualKey = VirtualKey(primary)
    override fun getPrimaryKey(): Long = primary
    override fun getId(): ResourceLocation =
        (if (secondary != 0L) "$TYPE_ID/$primary/$secondary" else "$TYPE_ID/$primary").rlAE

    override fun writeToPacket(data: FriendlyByteBuf) {
        data.writeVarLong(primary)
        data.writeVarLong(secondary)
    }

    override fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong("1", primary)
        tag.putLong("2", secondary)
        return tag
    }

    override fun addDrops(amount: Long, drops: MutableList<ItemStack>, level: Level, pos: BlockPos) {
    }

    override fun isTagged(tag: TagKey<*>): Boolean = false

    override fun computeDisplayName(): Component = Component.literal(
        if (secondary != 0L) "VK($primary, $secondary)" else "VK($primary)",
    )

    object Type : AEKeyType(TYPE_ID.rlAE, VirtualKey::class.java, Component.translatable("gui.$MODID.virtual")) {
        override fun readFromPacket(data: FriendlyByteBuf): AEKey {
            return VirtualKey(data.readVarLong(), data.readVarLong())
        }

        override fun loadKeyFromTag(tag: CompoundTag): AEKey {
            return VirtualKey(tag.getLong("1"), tag.getLong("2"))
        }

        override fun getUnitSymbol(): String = "VK"
    }

    companion object {
        const val TYPE_ID = "v"
    }
}
