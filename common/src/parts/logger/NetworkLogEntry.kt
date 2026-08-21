package allyouneed.parts.logger

import allyouneed.util.MODID
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NetworkLogEntry(
    val utcMillis: Long,
    val kind: NetworkLogKind,
    val args: List<String>,
) {
    fun write(buf: FriendlyByteBuf) {
        buf.writeLong(utcMillis)
        buf.writeByte(kind.ordinal)
        buf.writeVarInt(args.size)
        for (arg in args) {
            buf.writeUtf(arg, 256)
        }
    }

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong("t", utcMillis)
        tag.putByte("k", kind.ordinal.toByte())
        val list = ListTag()
        for (arg in args) {
            list.add(StringTag.valueOf(arg))
        }
        tag.put("a", list)
        return tag
    }

    fun formatLocalTime(): String =
        LOCAL_TIME.format(Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()))

    fun message(): Component =
        Component.translatable("gui.$MODID.log.${kind.langKey}", *args.toTypedArray())

    fun toComponent(): Component =
        Component.literal("[${formatLocalTime()}] ").append(message())

    fun toPlainLine(): String = "[${formatLocalTime()}] ${message().string}"

    companion object {
        private val LOCAL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        @JvmStatic
        fun read(buf: FriendlyByteBuf): NetworkLogEntry {
            val time = buf.readLong()
            val kind = NetworkLogKind.byOrdinal(buf.readUnsignedByte().toInt())
            val n = buf.readVarInt()
            val args = ArrayList<String>(n)
            repeat(n) { args.add(buf.readUtf(256)) }
            return NetworkLogEntry(time, kind, args)
        }

        fun fromNbt(tag: CompoundTag): NetworkLogEntry {
            val time = tag.getLong("t")
            val kind = NetworkLogKind.byOrdinal(tag.getByte("k").toInt() and 0xFF)
            val list = tag.getList("a", Tag.TAG_STRING.toInt())
            val args = ArrayList<String>(list.size)
            for (i in list.indices) {
                args.add(list.getString(i))
            }
            return NetworkLogEntry(time, kind, args)
        }
    }
}
