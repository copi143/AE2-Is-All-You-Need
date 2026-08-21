package allyouneed.parts.logger;

import appeng.menu.guisync.PacketWritable;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record NetworkLogPage(List<NetworkLogEntry> entries, int total, int offset) implements PacketWritable {
    public static final NetworkLogPage EMPTY = new NetworkLogPage(List.of(), 0, 0);

    public NetworkLogPage(FriendlyByteBuf buf) {
        this(readEntries(buf), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
        data.writeVarInt(entries.size());
        for (NetworkLogEntry entry : entries) {
            entry.write(data);
        }
        data.writeVarInt(total);
        data.writeVarInt(offset);
    }

    private static List<NetworkLogEntry> readEntries(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<NetworkLogEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(NetworkLogEntry.read(buf));
        }
        return List.copyOf(list);
    }
}
