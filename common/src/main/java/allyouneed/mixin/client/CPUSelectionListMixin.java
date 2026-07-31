package allyouneed.mixin.client;

import allyouneed.util.IecFormat;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CPUSelectionList.class, remap = false)
public class CPUSelectionListMixin {

    @Inject(method = "formatStorage", at = @At("HEAD"), cancellable = true)
    private void allyouneed$formatStorage(
            CraftingStatusMenu.CraftingCpuListEntry cpu,
            CallbackInfoReturnable<String> cir
    ) {
        long storage = cpu.storage();
        if (storage == Long.MAX_VALUE || storage < 0) {
            cir.setReturnValue("\u221E");
            return;
        }
        cir.setReturnValue(IecFormat.formatBytes(storage));
    }
}
