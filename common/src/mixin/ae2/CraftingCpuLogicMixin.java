package allyouneed.mixin.ae2;

import allyouneed.api.BigCpuCapacity;
import allyouneed.parts.logger.NetworkLogHooks;
import allyouneed.util.bigint.BigCpuStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.core.sync.packets.CraftingJobStatusPacket;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public class CraftingCpuLogicMixin {

    @Final
    @Shadow
    CraftingCPUCluster cluster;

    /**
     * Before the vanilla bytes check, reject too-small CPUs using BigInteger capacity.
     * When we can hold the job, vanilla's long comparison may still fail for unbounded
     * edge cases — override those by cancelling with success path skip is hard;
     * instead replace the comparison via forcing getAvailableStorage (separate redirect).
     */
    @SuppressWarnings("ConstantValue")
    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void allyouneed$rejectTooSmall(IGrid grid, ICraftingPlan plan, IActionSource src, ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // Only handle the size dimension here when unbounded / big path differs from long.
        // Busy/offline still handled by vanilla after we don't cancel.
        if (BigCpuStorage.hasClusterEntry(cluster) || ((BigCpuCapacity) (Object) cluster).isUnboundedCapacity()) {
            if (!BigCpuStorage.canHold(cluster, plan.bytes())) {
                cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
            }
        }
    }

    @Inject(method = "notifyJobOwner", at = @At("HEAD"))
    private void allyouneed$logJob(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status, CallbackInfo ci) {
        NetworkLogHooks.onCraftingJob(
            this.cluster.getGrid(),
            ((ExecutingCraftingJobAccessor) job).allyouneed$getFinalOutput(),
            status
        );
    }
}
