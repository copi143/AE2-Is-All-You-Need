package allyouneed.mixin.ae2;

import allyouneed.api.DirtyExternalStorage;
import allyouneed.api.ExternalInventoryWatch;
import allyouneed.util.inventory.InventoryWatchers;
import appeng.me.storage.CompositeStorage;
import appeng.me.storage.ITickingMonitor;
import appeng.parts.AEBasePart;
import appeng.parts.storagebus.StorageBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusPartMixin implements ExternalInventoryWatch {

    @Shadow
    private ITickingMonitor monitor;

    @Unique
    private InventoryWatchers.Handle allyouneed$watch;

    @Inject(method = "invalidateOnExternalStorageChange", at = @At("HEAD"))
    private void allyouneed$markDirty(CallbackInfo ci) {
        if (this.monitor instanceof DirtyExternalStorage dirty) {
            dirty.markExternalDirty();
        }
    }

    @Inject(method = "updateTarget", at = @At("TAIL"))
    private void allyouneed$retargetWatch(boolean forceFullUpdate, CallbackInfo ci) {
        this.unwatchExternal();
        if (!(this.monitor instanceof CompositeStorage storage)) {
            return;
        }
        var part = (AEBasePart) (Object) this;
        var level = part.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        var pos = part.getBlockEntity().getBlockPos().relative(part.getSide());
        var handlers = new ArrayList<Object>();
        handlers.add(storage);
        for (var wrapped : ((CompositeStorageAccessor) storage).allyouneed$getStorages().values()) {
            handlers.add(wrapped);
        }
        var self = (StorageBusPartInvoker) this;
        this.allyouneed$watch = InventoryWatchers.watch(level, pos, handlers, self::allyouneed$invalidateExternal);
    }

    @Override
    public void unwatchExternal() {
        InventoryWatchers.unwatch(this.allyouneed$watch);
        this.allyouneed$watch = null;
    }
}
