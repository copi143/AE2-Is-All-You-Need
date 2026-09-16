package allyouneed.mixin.ae2;

import allyouneed.api.BigStackSource;
import allyouneed.util.bigint.IncrementalCellIndex;
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
    private final IncrementalCellIndex<AEKey> allyouneed$index = new IncrementalCellIndex<>();

    @Unique
    private final KeyCounter allyouneed$scratch = new KeyCounter();

    @Override
    public @Nullable ObjectCounter<AEKey> getLastBigStacks() {
        return this.allyouneed$index.copyLast();
    }

    @Override
    public void getBigAvailableStacks(ObjectCounter<AEKey> out) {
        this.allyouneed$ensureListed();
        out.addAll(this.allyouneed$index.getLast());
    }

    @Inject(method = "mount", at = @At("TAIL"))
    private void allyouneed$invalidateMount(int priority, MEStorage inventory, CallbackInfo ci) {
        if (!this.mountsInUse) {
            this.allyouneed$index.invalidate();
        }
    }

    @Inject(method = "unmount", at = @At("TAIL"))
    private void allyouneed$invalidateUnmount(MEStorage inventory, CallbackInfo ci) {
        if (!this.mountsInUse) {
            this.allyouneed$index.invalidate();
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
            this.allyouneed$index.getLast().copySaturatedTo(out);
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
        if (!this.allyouneed$index.getValid()) {
            this.allyouneed$rebuildCells();
        }
        ObjectCounter<AEKey> big = this.allyouneed$index.getLast();
        big.clear();
        big.addAll(this.allyouneed$index.getCells());
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
        ObjectCounter<AEKey> cells = this.allyouneed$index.getCells();
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
        this.allyouneed$index.markValid();
    }

    @Unique
    private void allyouneed$afterChange(AEKey what, Actionable type, long changed) {
        this.allyouneed$index.onChange(
            type == Actionable.MODULATE,
            changed,
            what,
            () -> this.allyouneed$sumCellKey(what)
        );
    }

    @Unique
    private @Nullable BigInteger allyouneed$sumCellKey(AEKey what) {
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
                    return null;
                }
                sum = sum.add(amount);
            }
        }
        return anyCell ? sum : BigInteger.ZERO;
    }
}
