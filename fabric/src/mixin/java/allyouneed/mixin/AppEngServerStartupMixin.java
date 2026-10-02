package allyouneed.mixin;

import allyouneed.FabricAE2Hooks;
import appeng.core.AppEngServerStartup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AppEngServerStartup.class, remap = false)
public class AppEngServerStartupMixin {
    @Inject(method = "onInitializeServer", at = @At("TAIL"), remap = false)
    private void ae2isallyouneed_afterAE2ServerInit(CallbackInfo ci) {
        FabricAE2Hooks.afterAE2Init();
    }
}
