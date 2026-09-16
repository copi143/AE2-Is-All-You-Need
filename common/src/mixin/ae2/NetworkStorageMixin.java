package allyouneed.mixin.ae2;

import allyouneed.api.BigStackSource;
import allyouneed.api.KeyLocation;
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
import java.util.ArrayList;
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

    @Unique
    private final ObjectCounter<AEKey> allyouneed$mountTmp = new ObjectCounter<>();

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
        for (var invList : this.priorityInventory.values()) {
            for (var inv : invList) {
                if (BigStackSource.isCellMount(inv)) {
                    continue;
                }
                this.allyouneed$collectMount(inv, big);
            }
        }
    }

    @Unique
    private void allyouneed$rebuildCells() {
        ObjectCounter<AEKey> cells = this.allyouneed$index.getCells();
        cells.clear();
        for (var invList : this.priorityInventory.values()) {
            for (var inv : invList) {
                if (!BigStackSource.isCellMount(inv)) {
                    continue;
                }
                this.allyouneed$collectMount(inv, cells);
            }
        }
        this.allyouneed$index.markValid();
    }

    @Unique
    private void allyouneed$collectMount(MEStorage inv, ObjectCounter<AEKey> dest) {
        ObjectCounter<AEKey> tmp = this.allyouneed$mountTmp;
        tmp.clear();
        KeyCounter scratch = this.allyouneed$scratch;
        if (!BigStackSource.collectBigStacks(inv, tmp)) {
            scratch.clear();
            inv.getAvailableStacks(scratch);
            tmp.addAll(scratch);
            scratch.clear();
        }
        tmp.forEachEntry((key, value) -> {
            dest.add(key, value);
            dest.addLocation(key, inv, value.toBigInteger());
        });
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
        ArrayList<KeyLocation> locs = new ArrayList<>();
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
                if (amount.signum() > 0) {
                    locs.add(new KeyLocation(inv, amount));
                }
                sum = sum.add(amount);
            }
        }
        this.allyouneed$index.getCells().setLocations(what, locs);
        return anyCell ? sum : BigInteger.ZERO;
    }
}
