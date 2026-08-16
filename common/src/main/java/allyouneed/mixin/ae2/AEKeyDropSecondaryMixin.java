package allyouneed.mixin.ae2;

import allyouneed.api.KeyIdHolder;
import allyouneed.logic.aekey.EnergyKey;
import allyouneed.logic.aekey.ManaKey;
import allyouneed.logic.aekey.VirtualKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        value = {AEItemKey.class, AEFluidKey.class, EnergyKey.class, ManaKey.class, VirtualKey.class},
        remap = false
)
public abstract class AEKeyDropSecondaryMixin {

    @Inject(method = "dropSecondary", at = @At("HEAD"), cancellable = true)
    private void allyouneed$returnCachedDropSecondary(CallbackInfoReturnable<AEKey> cir) {
        AEKey cached = ((KeyIdHolder) this).getCachedSecondaryDropped();
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "dropSecondary", at = @At("RETURN"))
    private void allyouneed$storeCachedDropSecondary(CallbackInfoReturnable<AEKey> cir) {
        ((KeyIdHolder) this).setCachedSecondaryDropped(cir.getReturnValue());
    }
}
