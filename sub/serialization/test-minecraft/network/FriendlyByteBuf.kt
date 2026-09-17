package net.minecraft.network

import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class FriendlyByteBuf(private val buf: ByteBuf) {
    fun writeVarInt(v: Int): FriendlyByteBuf { writeVarIntInternal(v); return this }
    fun readVarInt(): Int = readVarIntInternal()
    fun writeVarLong(v: Long): FriendlyByteBuf { writeVarLongInternal(v); return this }
    fun readVarLong(): Long = readVarLongInternal()
    fun writeByte(v: Int): FriendlyByteBuf { buf.writeByte(v); return this }
    fun readByte(): Byte = buf.readByte()
    fun readUnsignedByte(): Short = buf.readUnsignedByte()
    fun writeShort(v: Int): FriendlyByteBuf { buf.writeShort(v); return this }
    fun readShort(): Short = buf.readShort()
    fun writeInt(v: Int): FriendlyByteBuf { buf.writeInt(v); return this }
    fun readInt(): Int = buf.readInt()
    fun writeLong(v: Long): FriendlyByteBuf { buf.writeLong(v); return this }
    fun readLong(): Long = buf.readLong()
    fun writeFloat(v: Float): FriendlyByteBuf { buf.writeFloat(v); return this }
    fun readFloat(): Float = buf.readFloat()
    fun writeDouble(v: Double): FriendlyByteBuf { buf.writeDouble(v); return this }
    fun readDouble(): Double = buf.readDouble()
    fun writeBoolean(v: Boolean): FriendlyByteBuf { buf.writeBoolean(v); return this }
    fun readBoolean(): Boolean = buf.readBoolean()
    fun writeUtf(v: String): FriendlyByteBuf { val b = v.toByteArray(Charsets.UTF_8); writeVarInt(b.size); buf.writeBytes(b); return this }
    fun readUtf(): String { val len = readVarInt(); val b = ByteArray(len); buf.readBytes(b); return String(b, Charsets.UTF_8) }
    fun writeByteArray(v: ByteArray): FriendlyByteBuf { writeVarInt(v.size); buf.writeBytes(v); return this }
    fun readByteArray(): ByteArray { val len = readVarInt(); val b = ByteArray(len); buf.readBytes(b); return b }
    fun writeIntArray(v: IntArray): FriendlyByteBuf { writeVarInt(v.size); for (i in v) buf.writeInt(i); return this }
    fun readIntArray(): IntArray { val len = readVarInt(); return IntArray(len) { buf.readInt() } }
    fun writeLongArray(v: LongArray): FriendlyByteBuf { writeVarInt(v.size); for (l in v) buf.writeLong(l); return this }
    fun readLongArray(): LongArray { val len = readVarInt(); return LongArray(len) { buf.readLong() } }
    fun writeUUID(v: UUID): FriendlyByteBuf { buf.writeLong(v.mostSignificantBits); buf.writeLong(v.leastSignificantBits); return this }
    fun readUUID(): UUID = UUID(buf.readLong(), buf.readLong())
    fun writeResourceLocation(v: ResourceLocation): FriendlyByteBuf { writeUtf(v.toString()); return this }
    fun readResourceLocation(): ResourceLocation = ResourceLocation(readUtf())
    fun writeBlockPos(v: BlockPos): FriendlyByteBuf { buf.writeInt(v.x); buf.writeInt(v.y); buf.writeInt(v.z); return this }
    fun readBlockPos(): BlockPos = BlockPos(buf.readInt(), buf.readInt(), buf.readInt())

    private fun writeVarIntInternal(value: Int) {
        var v = value
        while (true) {
            if ((v and -0x80) == 0) { buf.writeByte(v); return }
            buf.writeByte(v and 0x7F or 0x80); v = v ushr 7
        }
    }
    private fun readVarIntInternal(): Int {
        var numRead = 0; var result = 0; var read: Byte
        do { read = buf.readByte(); val value = (read.toInt() and 0x7F); result = result or (value shl 7 * numRead); numRead++; if (numRead > 5) throw RuntimeException("VarInt too big") } while ((read.toInt() and 0x80) != 0)
        return result
    }
    private fun writeVarLongInternal(value: Long) {
        var v = value
        while (true) {
            if ((v and -0x80L) == 0L) { buf.writeByte(v.toInt()); return }
            buf.writeByte(((v and 0x7F) or 0x80).toInt()); v = v ushr 7
        }
    }
    private fun readVarLongInternal(): Long {
        var numRead = 0; var result = 0L; var read: Byte
        do { read = buf.readByte(); val value = (read.toLong() and 0x7F); result = result or (value shl 7 * numRead); numRead++; if (numRead > 10) throw RuntimeException("VarLong too big") } while ((read.toInt() and 0x80) != 0)
        return result
    }
}
