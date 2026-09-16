package allyouneed.mixin.ae2;

import appeng.parts.storagebus.StorageBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = StorageBusPart.class, remap = false)
public interface StorageBusPartInvoker {
    @Invoker("invalidateOnExternalStorageChange")
    void allyouneed$invalidateExternal();
}
