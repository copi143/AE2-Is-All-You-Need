package allyouneed.mixin.ae2;

import appeng.api.networking.IGrid;
import appeng.me.pathfinding.PathingCalculation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import allyouneed.logic.pathing.ChannelAllocStats;
import allyouneed.logic.pathing.ChannelAllocator;

@Mixin(value = PathingCalculation.class, remap = false)
public abstract class PathingCalculationMixin {

    @Final
    @Shadow
    private IGrid grid;

    @Shadow
    private int channelsInUse;

    @Shadow
    private int channelsByBlocks;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void allyouneed$allocate(CallbackInfo ci) {
        ChannelAllocStats stats = ChannelAllocator.allocate(grid);
        this.channelsInUse = stats.getChannelsInUse();
        this.channelsByBlocks = stats.getChannelsByBlocks();
        ci.cancel();
    }
}
