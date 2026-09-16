package allyouneed.mixin.ae2;

import allyouneed.util.inventory.InventoryWatchers;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$FluidHandlerFacade", remap = false)
public abstract class FluidHandlerFacadeMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void allyouneed$link(IFluidHandler handler, CallbackInfo ci) {
        InventoryWatchers.linkChild(this, handler);
    }
}
