package allyouneed.parts.logger;

import appeng.menu.guisync.PacketWritable;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record NetworkLogDump(int seq, int loggerId, List<NetworkLogEntry> entries) implements PacketWritable {
    public static final NetworkLogDump EMPTY = new NetworkLogDump(0, 0, List.of());

    public NetworkLogDump(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), readEntries(buf));
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
        data.writeVarInt(seq);
        data.writeVarInt(loggerId);
        data.writeVarInt(entries.size());
        for (NetworkLogEntry entry : entries) {
            entry.write(data);
        }
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
