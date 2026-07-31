package allyouneed.mixin;

import allyouneed.util.bigint.BigAmounts;
import appeng.core.sync.packets.MEInventoryUpdatePacket;
import appeng.menu.me.common.GridInventoryEntry;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * When stored amount saturates to {@link Long#MAX_VALUE}, append a flag and optional BigInteger payload.
 */
@Mixin(value = MEInventoryUpdatePacket.class, remap = false)
public abstract class MEInventoryUpdatePacketMixin {

    @Redirect(
            method = "writeEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/menu/me/common/GridInventoryEntry;getStoredAmount()J"
            )
    )
    private static long allyouneed$storedAmountForWrite(GridInventoryEntry entry) {
        if (BigAmounts.hasEntryAmount(entry)) {
            BigInteger big = BigAmounts.getEntryAmount(entry);
            if (big.bitLength() > 63) {
                return Long.MAX_VALUE;
            }
            return big.longValue();
        }
        return entry.getStoredAmount();
    }

    @Inject(method = "writeEntry", at = @At("RETURN"))
    private static void allyouneed$writeBigPayload(FriendlyByteBuf buffer, GridInventoryEntry entry, CallbackInfo ci) {
        BigInteger big = BigAmounts.hasEntryAmount(entry)
                ? BigAmounts.getEntryAmount(entry)
                : BigInteger.valueOf(Math.max(0L, entry.getStoredAmount()));

        // Only extend the packet when the long field is saturated at Long.MAX_VALUE
        if (big.bitLength() <= 63 && big.longValue() != Long.MAX_VALUE) {
            return;
        }

        if (big.bitLength() > 63) {
            buffer.writeBoolean(true);
            byte[] bytes = big.toByteArray();
            buffer.writeVarInt(bytes.length);
            buffer.writeBytes(bytes);
        } else {
            buffer.writeBoolean(false);
        }
    }

    @Inject(method = "readEntry", at = @At("RETURN"))
    private static void allyouneed$readBigPayload(FriendlyByteBuf buffer, CallbackInfoReturnable<GridInventoryEntry> cir) {
        GridInventoryEntry entry = cir.getReturnValue();
        if (entry == null || entry.getStoredAmount() != Long.MAX_VALUE) {
            return;
        }
        boolean hasBig = buffer.readBoolean();
        if (hasBig) {
            int len = buffer.readVarInt();
            byte[] bytes = new byte[len];
            buffer.readBytes(bytes);
            BigAmounts.setEntryAmount(entry, new BigInteger(bytes));
        } else {
            BigAmounts.setEntryAmount(entry, BigInteger.valueOf(Long.MAX_VALUE));
        }
    }
}
