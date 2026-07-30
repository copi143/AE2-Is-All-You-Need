package allyouneed.mixin;

import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DelegatingMEInventory.class, remap = false)
public interface DelegatingMEInventoryAccessor {
    @Invoker("getDelegate")
    MEStorage allyouneed$getDelegate();
}
