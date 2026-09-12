package allyouneed.parts.logger

import appeng.menu.guisync.PacketWritable
import net.minecraft.network.FriendlyByteBuf

@JvmRecord
data class NetworkLogPage(val entries: List<NetworkLogEntry>, val total: Int, val offset: Int) : PacketWritable {
    constructor(buf: FriendlyByteBuf) : this(readEntries(buf), buf.readVarInt(), buf.readVarInt())

    override fun writeToPacket(data: FriendlyByteBuf) {
        data.writeVarInt(entries.size)
        for (entry in entries) {
            entry.write(data)
        }
        data.writeVarInt(total)
        data.writeVarInt(offset)
    }

    companion object {
        val EMPTY: NetworkLogPage = NetworkLogPage(listOf(), 0, 0)

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
