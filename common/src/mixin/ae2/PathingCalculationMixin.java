package allyouneed.mixin.ae2;

import java.util.Set;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.GridNode;
import appeng.me.pathfinding.PathingCalculation;
import appeng.parts.automation.AnnihilationPlanePart;
import appeng.parts.automation.FormationPlanePart;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import allyouneed.api.AsyncChannelNodeHolder;
import allyouneed.multiblock.async.IAsyncChannelSink;
import allyouneed.parts.planebus.PlaneBusPart;
import allyouneed.parts.planebus.PlaneBusClusters;

/**
 * 两处寻路调整：
 *
 * <p>1. 成型的异步处理连接器吞掉全部可用通道（最多 32），使下游节点全部缺频道。
 *
 * <p>2. 破坏面板专用线缆集群的未成型剥夺：频道分配本身走 AE2 原版多方块机制
 * （{@link PlaneBusPart} 声明 {@code GridFlags.MULTIBLOCK} 并注册 {@code IGridMultiblock}，
 * BFS 中首个拿到频道的成员让整个集群零成本搭车，只消耗 1 个真实频道）；本 mixin 在
 * BFS 结束后、向上传播分配结果之前，把<b>未成型</b>集群的所有成员剥夺频道并从多方块
 * 搭车名单中移除，使整个结构无法接入网络。
 */
@Mixin(value = PathingCalculation.class, remap = false)
public abstract class PathingCalculationMixin {

    @Final
    @Shadow
    private Reference2IntOpenHashMap<GridNode> channelBottlenecks;

    @Final
    @Shadow
    private IGrid grid;

    @Final
    @Shadow
    private Set<GridNode> channelNodes;

    @Final
    @Shadow
    private Set<GridNode> multiblocksWithChannel;

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

    /**
     * Runs after the BFS channel allocation and before {@code propagateAssignments()}, so edits
     * to {@code channelNodes} are still picked up when per-node used-channel counts propagate.
     */
    @Inject(method = "compute", at = @At(value = "INVOKE", target = "Lappeng/me/pathfinding/PathingCalculation;propagateAssignments()V"))
    private void allyouneed$reconcilePlaneBusClusters(CallbackInfo ci) {
        PlaneBusClusters.Snapshot snapshot = null;
        for (var node : grid.getNodes()) {
            if (!(node instanceof GridNode gridNode)) {
                continue;
            }
            var owner = gridNode.getOwner();
            BlockPos pos;
            if (owner instanceof PlaneBusPart bus) {
                pos = bus.getBlockEntity().getBlockPos();
            } else if (owner instanceof FormationPlanePart plane) {
                pos = plane.getBlockEntity().getBlockPos();
            } else if (owner instanceof AnnihilationPlanePart plane) {
                pos = plane.getBlockEntity().getBlockPos();
            } else {
                continue;
            }

            // 同一网格的节点必然位于同一维度。All nodes of a grid share one dimension.
            if (snapshot == null) {
                snapshot = PlaneBusClusters.snapshotFor(gridNode.getLevel().dimension());
            }
            var clusterId = snapshot.getClusterIdAtPos().get(pos);
            if (clusterId == null || Boolean.TRUE.equals(snapshot.getFormedById().get(clusterId))) {
                continue; // Not clustered, or formed: vanilla multiblock handling applies.
            }

            // 未成型：剥夺频道，整个结构无法接入网络。只对确实拿到过频道的成员退款；从未
            // 分配的成员没有账可退，盲目退款会扣减共享祖先的瓶颈计数、偷走其他设备的容量。
            // 搭车名单也要清理：成型快照可能让 BFS 把成员放进了 multiblocksWithChannel，若
            // 随后结构被破坏，这些成员绝不能带着免费频道激活。
            if (channelNodes.contains(gridNode)) {
                allyouneed$stripChannel(gridNode);
            }
            multiblocksWithChannel.remove(gridNode);
        }
    }

    @Unique
    private void allyouneed$refundBottlenecks(GridNode start) {
        var pi = start;
        while (pi != null) {
            if (channelBottlenecks.getInt(pi) > 0) {
                channelBottlenecks.addTo(pi, -1);
            }
            pi = pi.getHighestSimilarAncestor();
        }
    }

    @Unique
    private void allyouneed$stripChannel(GridNode node) {
        channelNodes.remove(node);
        allyouneed$refundBottlenecks(node);
    }
}
