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

abstract class LevelOnlyKey : AEKey() {
    abstract val level: Int
    abstract val packetType: String
    abstract override fun getType(): Type<out LevelOnlyKey>
    override fun dropSecondary(): LevelOnlyKey = type.level0
    override fun getPrimaryKey(): Unit = Unit
    override fun getId(): ResourceLocation = (if (level > 0) "${type.typeId}/$level" else type.typeId).rlAE

    override fun writeToPacket(data: FriendlyByteBuf) {
        data.writeVarInt(level)
    }

    override fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("l", level)
        return tag
    }

    override fun addDrops(amount: Long, drops: MutableList<ItemStack>, level: Level, pos: BlockPos) {
        if (amount <= 0) return
        drops.add(AllPackets.createLevelPacket(packetType, this.level, amount))
    }

    override fun isTagged(tag: TagKey<*>): Boolean = false

    override fun computeDisplayName(): Component = Component.translatable(
        if (level > 0) "gui.$MODID.${type.typeId}.$level" else "gui.$MODID.${type.typeId}"
    )

    abstract class Type<T : LevelOnlyKey>(
        val typeId: String,
        keyClass: KClass<T>,
        private val factory: (Int) -> T,
        private val amountPerByte: Int = 8,
        private val amountPerUnit: Int = 1,
        private val unitSymbol: String = typeId.uppercase(),
    ) : AEKeyType(typeId.rlAE, keyClass.java, Component.translatable("gui.$MODID.$typeId")) {
        override fun readFromPacket(data: FriendlyByteBuf): AEKey {
            return factory(data.readVarInt().coerceAtLeast(0))
        }

        override fun loadKeyFromTag(tag: CompoundTag): AEKey {
            return factory(tag.getInt("l"))
        }

        override fun getAmountPerOperation(): Int = 1
        override fun getAmountPerByte(): Int = amountPerByte
        override fun getAmountPerUnit(): Int = amountPerUnit
        override fun getUnitSymbol(): String = unitSymbol

        val level0: LevelOnlyKey = factory(0)
    }
}
