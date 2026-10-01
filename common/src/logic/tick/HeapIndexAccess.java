package allyouneed.logic.tick;

/**
 * Duck interface implemented by the TickTracker mixin, exposing the heap slot stored on each
 * tracker so {@link IndexedHeap} can reposition it in O(log n).
 */
public interface HeapIndexAccess {
    int ae2isallyouneed$getHeapIndex();

    void ae2isallyouneed$setHeapIndex(int index);
}
