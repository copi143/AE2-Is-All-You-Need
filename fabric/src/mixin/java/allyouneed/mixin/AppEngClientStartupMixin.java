package allyouneed.mixin;

import allyouneed.FabricAE2Hooks;
import appeng.core.AppEngClientStartup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AppEngClientStartup.class, remap = false)
public class AppEngClientStartupMixin {
    @Inject(method = "onInitializeClient", at = @At("TAIL"), remap = false)
    private void ae2isallyouneed_afterAE2ClientInit(CallbackInfo ci) {
        FabricAE2Hooks.afterAE2Init();
    }
}
