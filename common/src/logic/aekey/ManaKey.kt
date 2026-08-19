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

/**
 * 魔力 AEKey。
 *
 * 代表一种魔力类型及其二级标识（如 Blood Magic 的不同等级、Ars Nouveau 的不同法术类型等）。
 * [metric] 指定魔力系统，[level] 指定二级标识，空字符串表示基础魔力类型。
 *
 * Mana AEKey.
 *
 * Represents a mana type and its secondary identifier (e.g., Blood Magic ritual tier, Ars Nouveau spell type).
 * [metric] specifies the mana system, [level] specifies the secondary identifier; empty string means the base mana type.
 */
data class ManaKey(val metric: ManaType, val level: Int = 0) : AEKey() {
    override fun getType(): Type = Type
    override fun dropSecondary(): ManaKey = ManaKey(metric)
    override fun getPrimaryKey(): ManaType = metric
    override fun getId(): ResourceLocation = (if (level > 0) "$TYPE_ID/$metric/$level" else "$TYPE_ID/$metric").rlAE

    override fun writeToPacket(data: FriendlyByteBuf) {
        data.writeUtf(metric.id)
        data.writeVarInt(level)
    }

    override fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("m", metric.id)
        tag.putInt("l", level)
        return tag
    }

    override fun addDrops(amount: Long, drops: MutableList<ItemStack>, level: Level, pos: BlockPos) {
    }

    override fun isTagged(tag: TagKey<*>): Boolean = false

    override fun computeDisplayName(): Component = Component.translatable(
        if (level > 0) "gui.$MODID.mana.$metric.$level" else "gui.$MODID.mana.$metric"
    )

    object Type : AEKeyType(TYPE_ID.rlAE, ManaKey::class.java, Component.translatable("gui.$MODID.mana")) {
        override fun readFromPacket(data: FriendlyByteBuf): AEKey? {
            val metric = ManaType.idMap[data.readUtf()] ?: return null
            val level = data.readVarInt().coerceAtLeast(0)
            return ManaKey(metric, level)
        }

        override fun loadKeyFromTag(tag: CompoundTag): AEKey? {
            val metric = ManaType.idMap[tag.getString("m")] ?: return null
            val level = tag.getInt("l")
            return ManaKey(metric, level)
        }

        override fun getAmountPerOperation(): Int = 1
        override fun getAmountPerByte(): Int = MANA_GRANULARITY * MANA_PER_BYTE
        override fun getAmountPerUnit(): Int = MANA_GRANULARITY
        override fun getUnitSymbol(): String = "AM"
    }

    companion object {
        const val TYPE_ID = "m"
        const val MANA_PER_BYTE = 64
        const val MANA_GRANULARITY = 1024
    }
}
