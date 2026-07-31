package allyouneed.mixin;

import allyouneed.util.bigint.BigCpuStorage;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public class CraftConfirmMenuMixin {

    @Shadow
    private CraftingPlanSummary plan;

    @Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true)
    private void allyouneed$cpuMatches(ICraftingCPU c, CallbackInfoReturnable<Boolean> cir) {
        if (this.plan == null) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(BigCpuStorage.canHold(c, this.plan.getUsedBytes()) && !c.isBusy());
    }
}
