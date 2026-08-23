package allyouneed.logic.aekey

import allyouneed.item.packet.AllPackets
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
import kotlin.reflect.KClass

abstract class MetricLevelKey : AEKey() {
    interface Metric<out T : MetricLevelKey> {
        val id: String
        val typeKey: T
        val granularity: Int
    }

    abstract val metric: Metric<MetricLevelKey>
    abstract val level: Int
    abstract override fun getType(): Type<out MetricLevelKey>
    override fun dropSecondary(): MetricLevelKey = metric.typeKey
    override fun getPrimaryKey(): Metric<MetricLevelKey> = metric
    override fun getId(): ResourceLocation =
        (if (level > 0) "${type.shortTypeId}/$metric/$level" else "${type.shortTypeId}/$metric").rlAE

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
        if (amount <= 0) return
        drops.add(AllPackets.createMetricLevelPacket(metric, this.level, amount))
    }

    override fun isTagged(tag: TagKey<*>): Boolean = false

    override fun computeDisplayName(): Component = Component.translatable(
        if (level > 0) "gui.$MODID.${type.typeId}.$metric.$level" else "gui.$MODID.${type.typeId}.$metric"
    )

    abstract class Type<T : MetricLevelKey>(
        val typeId: String,
        keyClass: KClass<T>,
        val idMap: Map<String, Metric<T>>,
        val factory: (Metric<T>, Int) -> T,
        private val amountPerByte: Int = 8,
        private val amountPerUnit: Int = 1,
        val shortTypeId: String = typeId,
        private val unitSymbol: String = shortTypeId.uppercase(),
    ) : AEKeyType(shortTypeId.rlAE, keyClass.java, Component.translatable("gui.$MODID.$typeId")) {
        override fun readFromPacket(data: FriendlyByteBuf): AEKey? {
            val metric = idMap[data.readUtf()] ?: return null
            val level = data.readVarInt().coerceAtLeast(0)
            return factory(metric, level)
        }

        override fun loadKeyFromTag(tag: CompoundTag): AEKey? {
            val metric = idMap[tag.getString("m")] ?: return null
            val level = tag.getInt("l")
            return factory(metric, level)
        }

        override fun getAmountPerOperation(): Int = 1
        override fun getAmountPerByte(): Int = amountPerByte
        override fun getAmountPerUnit(): Int = amountPerUnit
        override fun getUnitSymbol(): String = unitSymbol
    }
}
