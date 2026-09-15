package allyouneed.mixin.ae2;

import allyouneed.api.BigStackSource;
import allyouneed.util.bigint.ObjectCounter;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;
import java.util.List;
import java.util.NavigableMap;

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
    private final ObjectCounter<AEKey> allyouneed$cellStacks = new ObjectCounter<>();

    @Unique
    private final KeyCounter allyouneed$scratch = new KeyCounter();

    @Unique
    private boolean allyouneed$cellsValid;

    @Override
    public @Nullable ObjectCounter<AEKey> getLastBigStacks() {
        return this.allyouneed$lastBigStacks;
    }

    @Override
    public void getBigAvailableStacks(ObjectCounter<AEKey> out) {
        this.allyouneed$ensureListed();
        out.addAll(this.allyouneed$lastBigStacks);
    }

    @Inject(method = "mount", at = @At("TAIL"))
    private void allyouneed$invalidateMount(int priority, MEStorage inventory, CallbackInfo ci) {
        if (!this.mountsInUse) {
            this.allyouneed$cellsValid = false;
        }
    }

    @Inject(method = "unmount", at = @At("TAIL"))
    private void allyouneed$invalidateUnmount(MEStorage inventory, CallbackInfo ci) {
        if (!this.mountsInUse) {
            this.allyouneed$cellsValid = false;
        }
    }

    @Inject(method = "insert", at = @At("RETURN"))
    private void allyouneed$afterInsert(AEKey what, long amount, Actionable type, IActionSource src, CallbackInfoReturnable<Long> cir) {
        this.allyouneed$afterChange(what, type, cir.getReturnValue());
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void allyouneed$afterExtract(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        this.allyouneed$afterChange(what, mode, cir.getReturnValue());
    }

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), cancellable = true)
    private void allyouneed$getAvailableStacksBig(KeyCounter out, CallbackInfo ci) {
        if (this.mountsInUse) {
            ci.cancel();
            return;
        }
        this.mountsInUse = true;
        try {
            this.allyouneed$rebuildFull();
            this.allyouneed$lastBigStacks.copySaturatedTo(out);
        } finally {
            this.mountsInUse = false;
        }
        ci.cancel();
    }

    @Unique
    private void allyouneed$ensureListed() {
        if (this.mountsInUse) {
            return;
        }
        this.mountsInUse = true;
        try {
            this.allyouneed$rebuildFull();
        } finally {
            this.mountsInUse = false;
        }
    }

    @Unique
    private void allyouneed$rebuildFull() {
        if (!this.allyouneed$cellsValid) {
            this.allyouneed$rebuildCells();
        }
        ObjectCounter<AEKey> big = this.allyouneed$lastBigStacks;
        big.clear();
        big.addAll(this.allyouneed$cellStacks);
        KeyCounter scratch = this.allyouneed$scratch;
        scratch.clear();
        boolean usedScratch = false;
        for (var invList : this.priorityInventory.values()) {
            for (var inv : invList) {
                if (BigStackSource.isCellMount(inv)) {
                    continue;
                }
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
    }

    @Unique
    private void allyouneed$rebuildCells() {
        ObjectCounter<AEKey> cells = this.allyouneed$cellStacks;
        cells.clear();
        KeyCounter scratch = this.allyouneed$scratch;
        scratch.clear();
        boolean usedScratch = false;
        for (var invList : this.priorityInventory.values()) {
            for (var inv : invList) {
                if (!BigStackSource.isCellMount(inv)) {
                    continue;
                }
                if (!BigStackSource.collectBigStacks(inv, cells)) {
                    inv.getAvailableStacks(scratch);
                    usedScratch = true;
                }
            }
        }
        if (usedScratch) {
            cells.addAll(scratch);
            scratch.clear();
        }
        this.allyouneed$cellsValid = true;
    }

    @Unique
    private void allyouneed$afterChange(AEKey what, Actionable type, long changed) {
        if (type != Actionable.MODULATE || changed <= 0L || !this.allyouneed$cellsValid) {
            return;
        }
        if (!this.allyouneed$recountCellKey(what)) {
            this.allyouneed$cellsValid = false;
        }
    }

    @Unique
    private boolean allyouneed$recountCellKey(AEKey what) {
        BigInteger sum = BigInteger.ZERO;
        boolean anyCell = false;
        for (var invList : this.priorityInventory.values()) {
            for (var inv : invList) {
                if (!BigStackSource.isCellMount(inv)) {
                    continue;
                }
                anyCell = true;
                BigInteger amount = BigStackSource.queryAmount(inv, what);
                if (amount == null) {
                    return false;
                }
                sum = sum.add(amount);
            }
        }
        if (!anyCell) {
            this.allyouneed$cellStacks.remove(what);
            return true;
        }
        this.allyouneed$cellStacks.set(what, sum);
        return true;
    }
}
