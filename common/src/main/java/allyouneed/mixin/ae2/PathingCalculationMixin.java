package allyouneed.mixin.ae2;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import allyouneed.api.AsyncChannelNodeHolder;
import allyouneed.async.IAsyncChannelSink;
import appeng.api.networking.IGrid;
import appeng.me.GridNode;
import appeng.me.pathfinding.PathingCalculation;

/**
 * When a formed async processing connector is granted a channel, it swallows all of its
 * available channels (up to 32) so that every node downstream of it is starved of channels.
 */
@Mixin(value = PathingCalculation.class, remap = false)
public abstract class PathingCalculationMixin {

    @Final
    @Shadow
    private Reference2IntOpenHashMap<GridNode> channelBottlenecks;

    @Final
    @Shadow
    private IGrid grid;

    @Inject(method = "compute", at = @At("HEAD"))
    private void allyouneed$resetSwallowedChannels(CallbackInfo ci) {
        for (var node : grid.getNodes()) {
            if (node instanceof AsyncChannelNodeHolder holder) {
                holder.setAsyncSwallowedChannels(0);
            }
        }
    }

    @Inject(method = "tryUseChannel", at = @At("RETURN"))
    private void allyouneed$swallowChannels(GridNode start, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        if (!(start instanceof AsyncChannelNodeHolder holder)) {
            return;
        }
        if (!(start.getOwner() instanceof IAsyncChannelSink sink) || !sink.isFormed()) {
            return;
        }
        int maxChannels = start.getMaxChannels();
        if (maxChannels == Integer.MAX_VALUE) {
            return; // Infinite channel mode, nothing to swallow.
        }
        channelBottlenecks.put(start, maxChannels);
        holder.setAsyncSwallowedChannels(maxChannels);
    }
}
