package allyouneed.parts.logger

import appeng.menu.guisync.PacketWritable
import net.minecraft.network.FriendlyByteBuf

@JvmRecord
data class NetworkLogDump(val seq: Int, val loggerId: Int, val entries: List<NetworkLogEntry>) : PacketWritable {
    constructor(buf: FriendlyByteBuf) : this(buf.readVarInt(), buf.readVarInt(), readEntries(buf))

    override fun writeToPacket(data: FriendlyByteBuf) {
        data.writeVarInt(seq)
        data.writeVarInt(loggerId)
        data.writeVarInt(entries.size)
        for (entry in entries) {
            entry.write(data)
        }
    }

    companion object {
        val EMPTY: NetworkLogDump = NetworkLogDump(0, 0, listOf())

        private fun readEntries(buf: FriendlyByteBuf): List<NetworkLogEntry> {
            val n = buf.readVarInt()
            val list = ArrayList<NetworkLogEntry>(n)
            for (i in 0..<n) {
                list.add(NetworkLogEntry.read(buf))
            }
            return list
        }
    }
}
