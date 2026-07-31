package allyouneed.mixin;

import allyouneed.cell.CraftingStorage;
import allyouneed.util.BigCpuStorage;
import appeng.block.crafting.ICraftingUnitType;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public class CraftingCPUClusterMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void allyouneed$initStorage(CallbackInfo ci) {
        BigCpuStorage.clearCluster((CraftingCPUCluster) (Object) this);
    }

    @Inject(method = "addBlockEntity", at = @At("TAIL"))
    private void allyouneed$addStorage(CraftingBlockEntity te, CallbackInfo ci) {
        CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        ICraftingUnitType type = te.getUnitBlock().type;
        if (type instanceof CraftingStorage craftingStorage && craftingStorage.isCreative()) {
            BigCpuStorage.addClusterBytes(self, BigInteger.valueOf(Long.MAX_VALUE), true);
            return;
        }
        long bytes = te.getStorageBytes();
        if (bytes > 0) {
            BigCpuStorage.addClusterBytes(self, BigInteger.valueOf(bytes), false);
        }
    }

    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void allyouneed$getAvailableStorage(CallbackInfoReturnable<Long> cir) {
        CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        if (BigCpuStorage.isUnbounded(self)) {
            cir.setReturnValue(Long.MAX_VALUE);
            return;
        }
        if (BigCpuStorage.hasClusterEntry(self)) {
            cir.setReturnValue(BigCpuStorage.getClusterStorageLong(self));
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void allyouneed$destroy(CallbackInfo ci) {
        BigCpuStorage.clearCluster((CraftingCPUCluster) (Object) this);
    }
}
