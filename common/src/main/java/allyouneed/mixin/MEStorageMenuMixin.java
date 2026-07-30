package allyouneed.mixin;

import allyouneed.api.BigStackSource;
import allyouneed.util.BigAmounts;
import allyouneed.util.BigKeyCounter;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.common.MEStorageMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publish BigInteger network totals while building ME inventory update packets,
 * and detect changes that would be invisible after long saturation.
 */
@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuMixin {

    @Shadow
    @Nullable
    protected MEStorage storage;

    @Shadow
    private IncrementalUpdateHelper updateHelper;

    @Unique
    private BigKeyCounter allyouneed$previousBigStacks = new BigKeyCounter();

    @Redirect(
            method = "broadcastChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"
            )
    )
    private KeyCounter allyouneed$captureBigStacks(MEStorage storage) {
        KeyCounter stacks = storage.getAvailableStacks();
        BigKeyCounter big;
        if (storage instanceof BigStackSource source && source.allyouneed$getLastBigStacks() != null) {
            big = source.allyouneed$getLastBigStacks().copy();
        } else {
            big = BigKeyCounter.fromKeyCounter(stacks);
            if (big == null) {
                big = new BigKeyCounter();
            }
        }
        BigAmounts.setCurrent(big);
        return stacks;
    }

    @Inject(
            method = "broadcastChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/menu/me/common/IncrementalUpdateHelper;hasChanges()Z"
            )
    )
    private void allyouneed$detectBigChanges(CallbackInfo ci) {
        BigKeyCounter current = BigAmounts.getCurrent();
        if (current == null) {
            return;
        }
        current.collectChangedKeys(this.allyouneed$previousBigStacks, key -> this.updateHelper.addChange(key));
    }

    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void allyouneed$finishBigSnapshot(CallbackInfo ci) {
        BigKeyCounter current = BigAmounts.getCurrent();
        if (current != null) {
            this.allyouneed$previousBigStacks = current;
        }
        BigAmounts.clearCurrent();
    }
}
