package allyouneed.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import appeng.me.service.helpers.TickTracker;

import allyouneed.logic.tick.HeapIndexAccess;

@Mixin(value = TickTracker.class, remap = false)
public abstract class TickTrackerHeapIndexMixin implements HeapIndexAccess {
    @Unique
    private int ae2isallyouneed$heapIndex = -1;

    @Override
    public int ae2isallyouneed$getHeapIndex() {
        return ae2isallyouneed$heapIndex;
    }

    @Override
    public void ae2isallyouneed$setHeapIndex(int index) {
        ae2isallyouneed$heapIndex = index;
    }
}
