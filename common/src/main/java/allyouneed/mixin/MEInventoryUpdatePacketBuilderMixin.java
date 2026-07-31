package allyouneed.mixin;

import allyouneed.util.BigAmounts;
import allyouneed.util.SiFormat;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.sync.packets.MEInventoryUpdatePacket;
import appeng.menu.me.common.GridInventoryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.math.BigInteger;

/**
 * When constructing packet entries, pull stored amounts from the BigInteger snapshot
 * and attach the full value to each entry for encoding.
 */
@Mixin(value = MEInventoryUpdatePacket.Builder.class, remap = false)
public abstract class MEInventoryUpdatePacketBuilderMixin {

    @Unique
    private BigInteger allyouneed$pendingBig;

    @Redirect(
            method = "addChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/stacks/KeyCounter;get(Lappeng/api/stacks/AEKey;)J",
                    ordinal = 0
            )
    )
    private long allyouneed$storedFromBigChanges(KeyCounter counter, AEKey key) {
        return allyouneed$resolveStored(counter, key);
    }

    @Redirect(
            method = "addFull",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/stacks/KeyCounter;get(Lappeng/api/stacks/AEKey;)J",
                    ordinal = 0
            )
    )
    private long allyouneed$storedFromBigFull(KeyCounter counter, AEKey key) {
        return allyouneed$resolveStored(counter, key);
    }

    @Redirect(
            method = {"addChanges", "addFull"},
            at = @At(
                    value = "NEW",
                    target = "(JLappeng/api/stacks/AEKey;JJZ)Lappeng/menu/me/common/GridInventoryEntry;"
            )
    )
    private GridInventoryEntry allyouneed$createEntryWithBig(
            long serial,
            AEKey what,
            long storedAmount,
            long requestableAmount,
            boolean craftable) {
        GridInventoryEntry entry = new GridInventoryEntry(serial, what, storedAmount, requestableAmount, craftable);
        if (this.allyouneed$pendingBig != null) {
            BigAmounts.setEntryAmount(entry, this.allyouneed$pendingBig);
            this.allyouneed$pendingBig = null;
        }
        return entry;
    }

    @Unique
    private long allyouneed$resolveStored(KeyCounter counter, AEKey key) {
        BigInteger big = BigAmounts.getCurrentAmount(key);
        if (big != null) {
            this.allyouneed$pendingBig = big;
            return SiFormat.saturateToLong(big);
        }
        this.allyouneed$pendingBig = null;
        return counter.get(key);
    }
}
