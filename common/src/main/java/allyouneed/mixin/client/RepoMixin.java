package allyouneed.mixin.client;

import allyouneed.util.BigAmounts;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.GridInventoryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.math.BigInteger;

/**
 * Preserve BigInteger amounts across incremental client repo updates.
 */
@Mixin(value = Repo.class, remap = false)
public class RepoMixin {

    /**
     * When an incremental update rebuilds a GridInventoryEntry without AEKey,
     * copy BigInteger amount from the packet entry onto the newly constructed one.
     */
    @Redirect(
            method = "handleUpdate(Lappeng/menu/me/common/GridInventoryEntry;)V",
            at = @At(
                    value = "NEW",
                    target = "(JLappeng/api/stacks/AEKey;JJZ)Lappeng/menu/me/common/GridInventoryEntry;"
            )
    )
    private GridInventoryEntry allyouneed$newEntryWithBig(
            long serial,
            AEKey what,
            long storedAmount,
            long requestableAmount,
            boolean craftable,
            GridInventoryEntry serverEntry) {
        GridInventoryEntry created = new GridInventoryEntry(serial, what, storedAmount, requestableAmount, craftable);
        if (BigAmounts.hasEntryAmount(serverEntry)) {
            BigAmounts.setEntryAmount(created, BigAmounts.getEntryAmount(serverEntry));
        } else {
            BigAmounts.setEntryAmount(created, BigInteger.valueOf(storedAmount));
        }
        return created;
    }
}
