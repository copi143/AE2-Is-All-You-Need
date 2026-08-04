package allyouneed.mixin;

import allyouneed.api.BigStackSource;
import allyouneed.util.bigint.BigKeyCounter;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.NavigableMap;

/**
 * Aggregate per-mount stacks with BigInteger to avoid long overflow, then write
 * saturated longs into the outgoing {@link KeyCounter} for AE2 compatibility.
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin implements BigStackSource {

    @Final
    @Shadow
    private NavigableMap<Integer, List<MEStorage>> priorityInventory;

    @Shadow
    private boolean mountsInUse;

    @Unique
    private BigKeyCounter allyouneed$lastBigStacks = new BigKeyCounter();

    @Override
    public @Nullable BigKeyCounter getLastBigStacks() {
        return this.allyouneed$lastBigStacks;
    }

    @Override
    public void getBigAvailableStacks(BigKeyCounter out) {
        out.addAll(this.allyouneed$lastBigStacks);
    }

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), cancellable = true)
    private void allyouneed$getAvailableStacksBig(KeyCounter out, CallbackInfo ci) {
        if (this.mountsInUse) {
            ci.cancel();
            return;
        }

        this.mountsInUse = true;
        try {
            BigKeyCounter big = new BigKeyCounter();
            for (var invList : this.priorityInventory.values()) {
                for (var inv : invList) {
                    if (!BigStackSource.collectBigStacks(inv, big)) {
                        KeyCounter tmp = new KeyCounter();
                        inv.getAvailableStacks(tmp);
                        big.addAll(tmp);
                    }
                }
            }
            this.allyouneed$lastBigStacks = big;
            big.copySaturatedTo(out);
        } finally {
            this.mountsInUse = false;
        }
        ci.cancel();
    }
}
