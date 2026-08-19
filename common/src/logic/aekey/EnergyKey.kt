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
 * 能量 AEKey。
 *
 * 代表一种能量类型及其二级标识（如 GTCEu 电压等级、Mekanism 能量储存单元类型等）。
 * [metric] 指定能量系统，[level] 指定二级标识，空字符串表示基础能量类型。
 *
 * Energy AEKey.
 *
 * Represents an energy type and its secondary identifier (e.g., GTCEu voltage tier, Mekanism energy unit type).
 * [metric] specifies the energy system, [level] specifies the secondary identifier; empty string means the base energy type.
 */
data class EnergyKey(val metric: EnergyType, val level: Int = 0) : AEKey() {
    override fun getType(): Type = Type
    override fun dropSecondary(): EnergyKey = EnergyKey(metric)
    override fun getPrimaryKey(): EnergyType = metric
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
        if (level > 0) "gui.$MODID.energy.$metric.$level" else "gui.$MODID.energy.$metric"
    )

    object Type : AEKeyType(TYPE_ID.rlAE, EnergyKey::class.java, Component.translatable("gui.$MODID.energy")) {
        override fun readFromPacket(data: FriendlyByteBuf): AEKey? {
            val metric = EnergyType.idMap[data.readUtf()] ?: return null
            val level = data.readVarInt().coerceAtLeast(0)
            return EnergyKey(metric, level)
        }

        override fun loadKeyFromTag(tag: CompoundTag): AEKey? {
            val metric = EnergyType.idMap[tag.getString("m")] ?: return null
            val level = tag.getInt("l")
            return EnergyKey(metric, level)
        }

        override fun getAmountPerOperation(): Int = 1
        override fun getAmountPerByte(): Int = ENERGY_GRANULARITY * ENERGY_PER_BYTE
        override fun getAmountPerUnit(): Int = ENERGY_GRANULARITY
        override fun getUnitSymbol(): String = "AE"
    }

    companion object {
        const val TYPE_ID = "e"
        const val ENERGY_PER_BYTE = 64
        const val ENERGY_GRANULARITY = 1024
    }
}
