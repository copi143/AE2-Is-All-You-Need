package allyouneed.api;

import allyouneed.util.BigKeyCounter;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by storages that can report network totals beyond {@code long}.
 */
public interface BigStackSource {
    /**
     * Last big-integer snapshot produced by listing available stacks.
     * May be null if {@code getAvailableStacks} has not been called yet.
     */
    @Nullable
    BigKeyCounter allyouneed$getLastBigStacks();
}
