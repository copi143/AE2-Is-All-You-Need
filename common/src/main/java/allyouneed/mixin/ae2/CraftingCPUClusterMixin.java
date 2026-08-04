package allyouneed.mixin.ae2;

import allyouneed.cell.CraftingStorage;
import allyouneed.util.CommonKt;
import allyouneed.api.BigCpuCapacity;
import appeng.block.crafting.ICraftingUnitType;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * Instance fields for BigInteger / unbounded CPU capacity (no global WeakHashMap).
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = CraftingCPUCluster.class, remap = false)
public class CraftingCPUClusterMixin implements BigCpuCapacity {

    @Unique
    private BigInteger allyouneed$bigStorage = BigInteger.ZERO;

    @Unique
    private boolean allyouneed$unbounded;

    @Override
    public @NotNull BigInteger getBigStorage() {
        return this.allyouneed$bigStorage;
    }

    @Override
    public void setBigStorage(@NotNull BigInteger bytes) {
        this.allyouneed$bigStorage = bytes;
    }

    @Override
    public boolean isUnboundedCapacity() {
        return this.allyouneed$unbounded;
    }

    @Override
    public void setUnboundedCapacity(boolean unbounded) {
        this.allyouneed$unbounded = unbounded;
    }

    @Unique
    private void allyouneed$clear() {
        this.allyouneed$bigStorage = BigInteger.ZERO;
        this.allyouneed$unbounded = false;
    }

    @Unique
    private void allyouneed$add(BigInteger bytes) {
        if (this.allyouneed$unbounded || bytes == null || bytes.signum() <= 0) {
            return;
        }
        this.allyouneed$bigStorage = this.allyouneed$bigStorage.add(bytes);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void allyouneed$initStorage(CallbackInfo ci) {
        allyouneed$clear();
    }

    @Inject(method = "addBlockEntity", at = @At("TAIL"))
    private void allyouneed$addStorage(CraftingBlockEntity te, CallbackInfo ci) {
        ICraftingUnitType type = te.getUnitBlock().type;
        if (type instanceof CraftingStorage craftingStorage && craftingStorage.isCreative()) {
            this.allyouneed$unbounded = true;
            this.allyouneed$bigStorage = BigInteger.valueOf(Long.MAX_VALUE);
            return;
        }
        long bytes = te.getStorageBytes();
        if (bytes > 0) {
            allyouneed$add(BigInteger.valueOf(bytes));
        }
    }

    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void allyouneed$getAvailableStorage(CallbackInfoReturnable<Long> cir) {
        if (this.allyouneed$unbounded) {
            cir.setReturnValue(Long.MAX_VALUE);
            return;
        }
        if (this.allyouneed$bigStorage.signum() > 0) {
            cir.setReturnValue(CommonKt.saturateToLong(this.allyouneed$bigStorage));
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void allyouneed$destroy(CallbackInfo ci) {
        allyouneed$clear();
    }
}
