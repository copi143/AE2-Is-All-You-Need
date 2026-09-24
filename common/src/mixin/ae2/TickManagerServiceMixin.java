package allyouneed.mixin.ae2;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.service.TickManagerService;
import appeng.me.service.helpers.TickTracker;

import allyouneed.logic.tick.HeapIndexAccess;
import allyouneed.logic.tick.IndexedHeap;

/**
 * Replaces the per-level PriorityQueue&lt;TickTracker&gt; with an {@link IndexedHeap}: removal and
 * repositioning are O(log n) instead of O(n), which matters for grids with thousands of ticking
 * nodes. Ordering is unchanged (TickTracker's natural ordering). The original queue fields stay
 * allocated but unused.
 */
@Mixin(value = TickManagerService.class, remap = false)
public abstract class TickManagerServiceMixin {

    @Unique
    private final Map<Level, IndexedHeap<TickTracker>> ae2isallyouneed$heaps = new IdentityHashMap<>();

    @Unique
    @Nullable
    private IndexedHeap<TickTracker> ae2isallyouneed$currentlyTickingHeap;

    @Shadow
    private long currentTick;

    @Shadow
    @Final
    private Map<IGridNode, TickTracker> alertable;

    @Shadow
    @Final
    private Map<IGridNode, TickTracker> sleeping;

    @Shadow
    @Final
    private Map<IGridNode, TickTracker> awake;

    @Shadow
    @Nullable
    private IGridNode currentlyTicking;

    @Shadow
    public abstract boolean sleepDevice(IGridNode node);

    @Shadow
    private TickRateModulation unsafeTickingRequest(TickTracker tt, int diff) {
        throw new AssertionError();
    }

    @Unique
    private IndexedHeap<TickTracker> ae2isallyouneed$heap(@Nullable Level level) {
        return this.ae2isallyouneed$heaps.computeIfAbsent(level,
                key -> new IndexedHeap<TickTracker>(Comparator.naturalOrder(),
                        (ToIntFunction<TickTracker>) tt -> ((HeapIndexAccess) tt).ae2isallyouneed$getHeapIndex(),
                        (IndexedHeap.IndexSetter<TickTracker>) (tt, i) -> ((HeapIndexAccess) tt)
                                .ae2isallyouneed$setHeapIndex(i)));
    }

    /**
     * @author ae2isallyouneed
     * @reason IndexedHeap replaces PriorityQueue; logic identical to the original.
     */
    @Overwrite
    private void tickLevelQueue(@Nullable Level level) {
        var heap = this.ae2isallyouneed$heaps.get(level);
        if (heap == null) {
            return;
        }

        ae2isallyouneed$currentlyTickingHeap = heap;
        try {
            TickTracker tt;
            while (!heap.isEmpty()) {
                // Peek and stop once it reaches a TickTracker running at a later tick
                tt = heap.peek();
                if (tt.getNextTick() > this.currentTick) {
                    break;
                }

                if (heap.poll() != tt) {
                    throw new IllegalStateException();
                }
                var diff = (int) (this.currentTick - tt.getLastTick());
                currentlyTicking = tt.getNode();
                TickRateModulation mod;
                try {
                    mod = this.unsafeTickingRequest(tt, diff);
                } finally {
                    currentlyTicking = null;
                }

                // Update the last time this node was ticked
                tt.setLastTick(this.currentTick);

                var newRate = switch (mod) {
                    case URGENT -> tt.getRequest().minTickRate();
                    case FASTER -> tt.getCurrentRate() - 2; // TICK_RATE_SPEED_UP_FACTOR
                    case IDLE, SLEEP -> tt.getRequest().maxTickRate();
                    case SLOWER -> tt.getCurrentRate() + 1; // TICK_RATE_SLOW_DOWN_FACTOR
                    case SAME -> tt.getCurrentRate();
                };
                // This will clamp to the min,max range
                tt.setCurrentRate(newRate);

                if (mod == TickRateModulation.SLEEP) {
                    sleepDevice(tt.getNode());
                } else {
                    // Note that the node _may_ have been removed entirely from the grid in its own tick
                    if (this.awake.containsKey(tt.getNode())) {
                        heap.add(tt);
                    }
                }
            }
        } finally {
            ae2isallyouneed$currentlyTickingHeap = null;
        }

        if (heap.isEmpty()) {
            this.ae2isallyouneed$heaps.remove(level);
        }
    }

    /**
     * @author ae2isallyouneed
     * @reason IndexedHeap replaces PriorityQueue.
     */
    @Overwrite
    private void addToQueue(IGridNode node, TickTracker tt) {
        ae2isallyouneed$heap(node.getLevel()).add(tt);
    }

    /**
     * @author ae2isallyouneed
     * @reason IndexedHeap replaces PriorityQueue; removal is now O(log n).
     */
    @Overwrite
    private void removeFromQueue(IGridNode node, TickTracker tt) {
        var level = node.getLevel();
        var heap = ae2isallyouneed$heap(level);
        if (tt != null) {
            heap.remove(tt);
        }

        // Make sure we don't cleanup a queue we are iterating over,
        // as something might be added to it later even if it's empty now.
        if (ae2isallyouneed$currentlyTickingHeap != heap && heap.isEmpty()) {
            this.ae2isallyouneed$heaps.remove(level);
        }
    }

    /**
     * @author ae2isallyouneed
     * @reason IndexedHeap replaces PriorityQueue.
     */
    @Overwrite
    private void updateQueuePosition(IGridNode node, TickTracker tt) {
        this.removeFromQueue(node, tt);
        this.addToQueue(node, tt);
    }

    /**
     * @author ae2isallyouneed
     * @reason IndexedHeap replaces PriorityQueue; the queued check is O(1) via the heap index.
     */
    @Overwrite
    public TickManagerService.NodeStatus getStatus(IGridNode node) {
        var sleepingTracker = sleeping.get(node);
        var awakeTracker = awake.get(node);
        var alertableTracker = alertable.get(node);

        // Also check if the node is _really_ queued for ticking. If it's awake
        // and not queued, this indicates a bug.
        boolean isQueued = false;
        var heap = ae2isallyouneed$heaps.get(node.getLevel());
        if (awakeTracker != null && heap != null) {
            isQueued = heap.contains(awakeTracker);
        }

        // Get the tick-request stats
        var tracker = awakeTracker;
        if (tracker == null) {
            tracker = alertableTracker;
        }
        if (tracker == null) {
            tracker = sleepingTracker;
        }
        var currentRate = tracker != null ? tracker.getCurrentRate() : 0;
        var lastTick = tracker != null ? tracker.getLastTick() : 0;
        return new TickManagerService.NodeStatus(
                alertableTracker != null,
                sleepingTracker != null,
                awakeTracker != null,
                isQueued,
                currentRate,
                currentTick - lastTick);
    }
}
