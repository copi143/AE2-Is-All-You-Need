package allyouneed.mixin.ae2;

import allyouneed.api.BigAmountHolder;
import appeng.menu.me.common.GridInventoryEntry;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * Carry optional BigInteger amount on the entry instance (no global WeakHashMap).
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = GridInventoryEntry.class, remap = false)
public class GridInventoryEntryMixin implements BigAmountHolder {

    @Unique
    @Nullable
    private BigInteger allyouneed$bigAmount;

    @Override
    public @Nullable BigInteger getBigAmount() {
        return this.allyouneed$bigAmount;
    }

    @Override
    public void setBigAmount(@Nullable BigInteger amount) {
        this.allyouneed$bigAmount = amount;
    }

    @Inject(method = "isMeaningful", at = @At("HEAD"), cancellable = true)
    private void allyouneed$bigIsMeaningful(CallbackInfoReturnable<Boolean> cir) {
        if (this.allyouneed$bigAmount != null && this.allyouneed$bigAmount.signum() > 0) {
            cir.setReturnValue(true);
        }
    }
}
