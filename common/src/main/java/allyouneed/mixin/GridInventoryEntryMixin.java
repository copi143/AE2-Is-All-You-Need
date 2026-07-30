package allyouneed.mixin;

import allyouneed.util.BigAmounts;
import appeng.menu.me.common.GridInventoryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * Treat entries with BigInteger stored amounts as meaningful even when long field is saturated.
 */
@Mixin(value = GridInventoryEntry.class, remap = false)
public class GridInventoryEntryMixin {

    @Inject(method = "isMeaningful", at = @At("HEAD"), cancellable = true)
    private void allyouneed$bigIsMeaningful(CallbackInfoReturnable<Boolean> cir) {
        GridInventoryEntry self = (GridInventoryEntry) (Object) this;
        if (BigAmounts.hasEntryAmount(self)) {
            BigInteger big = BigAmounts.getEntryAmount(self);
            if (big.signum() > 0) {
                cir.setReturnValue(true);
            }
        }
    }
}
