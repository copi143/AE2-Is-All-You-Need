package allyouneed.api;

import allyouneed.mixin.DelegatingMEInventoryAccessor;
import allyouneed.util.bigint.BigKeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.DriveWatcher;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by storages that can report amounts beyond {@code long}.
 */
public interface BigStackSource {
    /**
     * Unwrap common AE2 wrappers and collect big stacks when available.
     *
     * @return true if big stacks were collected (caller should skip long path)
     */
    static boolean collectBigStacks(MEStorage storage, BigKeyCounter out) {
        MEStorage current = storage;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (current instanceof BigStackSource source) {
                source.getBigAvailableStacks(out);
                return true;
            }
            if (current instanceof DriveWatcher watcher) {
                current = watcher.getCell();
                continue;
            }
            if (current instanceof DelegatingMEInventory) {
                current = ((DelegatingMEInventoryAccessor) current).allyouneed$getDelegate();
                continue;
            }
            break;
        }
        return false;
    }

    /**
     * Append this storage's full BigInteger totals into {@code out}.
     */
    void getBigAvailableStacks(BigKeyCounter out);

    /**
     * Last snapshot if any; optional cache for callers that already listed.
     */
    @Nullable
    default BigKeyCounter getLastBigStacks() {
        return null;
    }
}
