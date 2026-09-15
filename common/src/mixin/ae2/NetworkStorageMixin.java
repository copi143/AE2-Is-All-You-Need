package allyouneed.mixin.ae2;

import allyouneed.api.BigStackSource;
import allyouneed.util.bigint.ObjectCounter;
import appeng.api.stacks.AEKey;
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
    private final ObjectCounter<AEKey> allyouneed$lastBigStacks = new ObjectCounter<>();

    @Unique
    private final KeyCounter allyouneed$scratch = new KeyCounter();

    @Override
    public @Nullable ObjectCounter<AEKey> getLastBigStacks() {
        return this.allyouneed$lastBigStacks;
    }

    @Override
    public void getBigAvailableStacks(ObjectCounter<AEKey> out) {
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
            ObjectCounter<AEKey> big = this.allyouneed$lastBigStacks;
            big.clear();
            KeyCounter scratch = this.allyouneed$scratch;
            scratch.clear();
            boolean usedScratch = false;
            for (var invList : this.priorityInventory.values()) {
                for (var inv : invList) {
                    if (!BigStackSource.collectBigStacks(inv, big)) {
                        inv.getAvailableStacks(scratch);
                        usedScratch = true;
                    }
                }
            }
            if (usedScratch) {
                big.addAll(scratch);
                scratch.clear();
            }
            big.copySaturatedTo(out);
        } finally {
            this.mountsInUse = false;
        }
        ci.cancel();
    }
}
