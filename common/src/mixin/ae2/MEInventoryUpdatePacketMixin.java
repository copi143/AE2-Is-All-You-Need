package allyouneed.mixin.ae2;

import allyouneed.api.BigAmountHolder;
import allyouneed.util.bigint.BigAmounts;
import appeng.api.stacks.AEKey;
import appeng.core.sync.packets.MEInventoryUpdatePacket;
import appeng.menu.me.common.GridInventoryEntry;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

@Mixin(value = MEInventoryUpdatePacket.class, remap = false)
public abstract class MEInventoryUpdatePacketMixin {

    @Unique
    private static final int FLAG_KEY = 1;
    @Unique
    private static final int FLAG_STORED = 2;
    @Unique
    private static final int FLAG_BIG = 4;
    @Unique
    private static final int FLAG_REQ = 8;
    @Unique
    private static final int FLAG_CRAFT = 16;

    @Inject(method = "writeEntry", at = @At("HEAD"), cancellable = true)
    private static void allyouneed$writeCompact(FriendlyByteBuf buffer, GridInventoryEntry entry, CallbackInfo ci) {
        BigInteger big = BigAmounts.getEntryAmount(entry);
        boolean isBig = big.signum() > 0 && big.bitLength() > 63;
        long stored = isBig ? Long.MAX_VALUE : Math.max(0L, big.longValue());
        long requestable = entry.getRequestableAmount();

        int flags = 0;
        if (entry.getWhat() != null) flags |= FLAG_KEY;
        if (isBig) flags |= FLAG_BIG;
        else if (stored != 0L) flags |= FLAG_STORED;
        if (requestable != 0L) flags |= FLAG_REQ;
        if (entry.isCraftable()) flags |= FLAG_CRAFT;

        buffer.writeByte(flags);
        buffer.writeVarLong(entry.getSerial());
        if ((flags & FLAG_KEY) != 0) {
            AEKey.writeKey(buffer, entry.getWhat());
        }
        if ((flags & FLAG_BIG) != 0) {
            byte[] bytes = big.toByteArray();
            buffer.writeVarInt(bytes.length);
            buffer.writeBytes(bytes);
        } else if ((flags & FLAG_STORED) != 0) {
            buffer.writeVarLong(stored);
        }
        if ((flags & FLAG_REQ) != 0) {
            buffer.writeVarLong(requestable);
        }
        ci.cancel();
    }

    @Inject(method = "readEntry", at = @At("HEAD"), cancellable = true)
    private static void allyouneed$readCompact(FriendlyByteBuf buffer, CallbackInfoReturnable<GridInventoryEntry> cir) {
        int flags = buffer.readUnsignedByte();
        long serial = buffer.readVarLong();
        AEKey what = (flags & FLAG_KEY) != 0 ? AEKey.readKey(buffer) : null;
        long stored = 0L;
        BigInteger big = null;
        if ((flags & FLAG_BIG) != 0) {
            int len = buffer.readVarInt();
            byte[] bytes = new byte[len];
            buffer.readBytes(bytes);
            big = new BigInteger(bytes);
            stored = Long.MAX_VALUE;
        } else if ((flags & FLAG_STORED) != 0) {
            stored = buffer.readVarLong();
        }
        long requestable = (flags & FLAG_REQ) != 0 ? buffer.readVarLong() : 0L;
        boolean craftable = (flags & FLAG_CRAFT) != 0;
        GridInventoryEntry entry = new GridInventoryEntry(serial, what, stored, requestable, craftable);
        if (big != null) {
            ((BigAmountHolder) (Object) entry).setBigAmount(big);
        } else if (stored != 0L) {
            ((BigAmountHolder) (Object) entry).setBigAmount(BigInteger.valueOf(stored));
        }
        cir.setReturnValue(entry);
    }
}
