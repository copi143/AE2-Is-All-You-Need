package allyouneed.mixin;

import allyouneed.util.BigCpuStorage;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;

/**
 * Sort crafting CPUs by BigInteger storage so values past {@link Long#MAX_VALUE} stay ordered.
 * Capacity checks go through {@link CraftingCPUCluster#getAvailableStorage()} (see {@link CraftingCPUClusterMixin}).
 */
@Mixin(value = CraftingService.class, remap = false)
public class CraftingServiceCpuMixin {

    @Shadow
    @Final
    @Mutable
    private static Comparator<CraftingCPUCluster> FAST_FIRST_COMPARATOR;

    @Shadow
    @Final
    @Mutable
    private static Comparator<CraftingCPUCluster> FAST_LAST_COMPARATOR;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void allyouneed$bigComparators(CallbackInfo ci) {
        FAST_FIRST_COMPARATOR = Comparator.comparingInt(CraftingCPUCluster::getCoProcessors).reversed().thenComparing(BigCpuStorage::compareStorage);
        FAST_LAST_COMPARATOR = Comparator.comparingInt(CraftingCPUCluster::getCoProcessors).thenComparing(BigCpuStorage::compareStorage);
    }
}
