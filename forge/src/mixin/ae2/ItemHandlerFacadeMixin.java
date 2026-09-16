package allyouneed.mixin.ae2;

import allyouneed.util.inventory.InventoryWatchers;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$ItemHandlerFacade", remap = false)
public abstract class ItemHandlerFacadeMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void allyouneed$link(IItemHandler handler, CallbackInfo ci) {
        InventoryWatchers.linkChild(this, handler);
    }
}
