package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AppEngSlot.class)
public abstract class AppEngSlotMixin extends Slot {

    @Shadow(remap = false)
    @Final
    private InternalInventory inventory;

    @Shadow(remap = false)
    @Final
    private int invSlot;

    @Shadow(remap = false)
    private AEBaseMenu menu;

    @Shadow(remap = false)
    public abstract boolean isSlotEnabled();

    public AppEngSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public ItemStack safeInsert(ItemStack stack, int increment) {
        if (!AllPackets.INSTANCE.isPacket(stack) || !this.isSlotEnabled()) {
            return super.safeInsert(stack, increment);
        }
        var leftover = allyouneed$insertPacket(stack, increment, false);
        if (leftover == null) {
            return super.safeInsert(stack, increment);
        }
        return leftover;
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void allyouneed$packetSet(ItemStack stack, CallbackInfo ci) {
        if (!this.isSlotEnabled() || !AllPackets.INSTANCE.isPacket(stack)) return;
        if (!(this.inventory instanceof ConfigMenuInventory cfg)) return;
        if (cfg.getDelegate().getMode() != GenericStackInv.Mode.STORAGE) return;

        cfg.getDelegate().setStack(this.invSlot, null);
        var leftover = AllPackets.INSTANCE.insert(stack, stack.getCount(), false,
            (key, amount, mode) -> cfg.getDelegate().insert(this.invSlot, key, amount, mode));
        if (leftover == null) return;

        ci.cancel();
        this.setChanged();
        if (!leftover.isEmpty() && this.menu != null && !this.menu.isClientSide()) {
            this.menu.getPlayer().getInventory().placeItemBackInInventory(leftover);
        }
    }

    @Unique
    private ItemStack allyouneed$insertPacket(ItemStack stack, int maxCount, boolean simulate) {
        if (!(this.inventory instanceof ConfigMenuInventory cfg)) {
            return null;
        }
        if (cfg.getDelegate().getMode() != GenericStackInv.Mode.STORAGE) {
            return null;
        }
        return AllPackets.INSTANCE.insert(stack, maxCount, simulate,
            (key, amount, mode) -> cfg.getDelegate().insert(this.invSlot, key, amount, mode));
    }
}
