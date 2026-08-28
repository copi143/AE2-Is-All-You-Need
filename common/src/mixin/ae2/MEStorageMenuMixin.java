package allyouneed.mixin.ae2;

import allyouneed.api.BigStackSource;
import allyouneed.item.packet.AllPackets;
import allyouneed.util.bigint.BigAmounts;
import allyouneed.util.bigint.ObjectCounter;
import appeng.api.stacks.AEKey;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publish BigInteger network totals while building ME inventory update packets,
 * and detect changes that would be invisible after long saturation.
 */
@Mixin(value = MEStorageMenu.class)
public abstract class MEStorageMenuMixin {

    @Final
    @Shadow(remap = false)
    protected @Nullable MEStorage storage;

    @Final
    @Shadow(remap = false)
    protected @Nullable IEnergySource powerSource;

    @Shadow(remap = false)
    protected abstract boolean canInteractWithGrid();

    @Final
    @Shadow(remap = false)
    private IncrementalUpdateHelper updateHelper;

    @Unique
    private ObjectCounter<AEKey> allyouneed$previousBigStacks = new ObjectCounter<>();

    @Redirect(method = "broadcastChanges", at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;", remap = false))
    private KeyCounter allyouneed$captureBigStacks(MEStorage storage) {
        KeyCounter stacks = storage.getAvailableStacks();
        ObjectCounter<AEKey> big;
        if (storage instanceof BigStackSource source && source.getLastBigStacks() != null) {
            big = source.getLastBigStacks().copy();
        } else {
            big = ObjectCounter.fromKeyCounter(stacks);
            if (big == null) {
                big = new ObjectCounter<>();
            }
        }
        BigAmounts.setCurrent(big);
        return stacks;
    }

    @Inject(method = "broadcastChanges", at = @At(value = "INVOKE", target = "Lappeng/menu/me/common/IncrementalUpdateHelper;hasChanges()Z", remap = false))
    private void allyouneed$detectBigChanges(CallbackInfo ci) {
        ObjectCounter<AEKey> current = BigAmounts.getCurrent();
        if (current == null) {
            return;
        }
        current.collectChangedKeys(this.allyouneed$previousBigStacks, key -> this.updateHelper.addChange(key));
    }

    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void allyouneed$finishBigSnapshot(CallbackInfo ci) {
        ObjectCounter<AEKey> current = BigAmounts.getCurrent();
        if (current != null) {
            this.allyouneed$previousBigStacks = current;
        }
        BigAmounts.clearCurrent();
    }

    @Inject(method = "putCarriedItemIntoNetwork", at = @At("HEAD"), cancellable = true, remap = false)
    private void allyouneed$putPacketIntoNetwork(boolean singleItem, CallbackInfo ci) {
        if (this.storage == null || this.powerSource == null) return;
        var self = (MEStorageMenu) (Object) this;
        var held = self.getCarried();
        var leftover = allyouneed$insertHeldPacket(held, singleItem ? 1 : held.getCount());
        if (leftover == null) return;
        self.setCarried(leftover);
        ci.cancel();
    }

    @Inject(method = "transferStackToMenu", at = @At("HEAD"), cancellable = true, remap = false)
    private void allyouneed$transferPacketToMenu(ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        if (!this.canInteractWithGrid() || this.storage == null || this.powerSource == null) return;
        var leftover = allyouneed$insertHeldPacket(input, input.getCount());
        if (leftover == null) return;
        cir.setReturnValue(leftover);
    }

    @Unique
    private ItemStack allyouneed$insertHeldPacket(ItemStack stack, int maxCount) {
        if (this.storage == null || this.powerSource == null) return null;
        var storage = this.storage;
        var power = this.powerSource;
        var source = ((MEStorageMenu) (Object) this).getActionSource();
        return AllPackets.INSTANCE.insert(stack, maxCount, false, (key, amount, mode) ->
            StorageHelper.poweredInsert(power, storage, key, amount, source, mode));
    }
}
