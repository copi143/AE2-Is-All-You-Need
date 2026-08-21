package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.CraftingMatrixSlot;
import appeng.menu.slot.FakeSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEBaseMenu.class)
public abstract class AEBaseMenuMixin {

    @Shadow(remap = false)
    protected abstract ItemStack transferStackToMenu(ItemStack input);

    @Shadow(remap = false)
    public abstract boolean isClientSide();

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void allyouneed$quickMovePacket(Player player, int idx, CallbackInfoReturnable<ItemStack> cir) {
        if (this.isClientSide()) return;
        var self = (AEBaseMenu) (Object) this;
        if (idx < 0 || idx >= self.slots.size()) return;
        var clickSlot = self.slots.get(idx);
        var stack = clickSlot.getItem();
        if (!AllPackets.INSTANCE.isPacket(stack)) return;
        if (!allyouneed$isPlayerSide(self, clickSlot)) return;

        var moving = stack.copy();
        var leftover = this.transferStackToMenu(moving);
        if (leftover == moving || leftover == null) {
            leftover = moving;
        }
        if (!leftover.isEmpty() && AllPackets.INSTANCE.isPacket(leftover)) {
            leftover = allyouneed$insertIntoMenuSlots(self, leftover);
        }
        clickSlot.set(leftover.isEmpty() ? ItemStack.EMPTY : leftover);
        self.broadcastChanges();
        cir.setReturnValue(ItemStack.EMPTY);
    }

    @Unique
    private static boolean allyouneed$isPlayerSide(AEBaseMenu self, Slot slot) {
        if (slot.container == self.getPlayerInventory()) return true;
        var semantic = self.getSlotSemantic(slot);
        return semantic == SlotSemantics.PLAYER_INVENTORY
            || semantic == SlotSemantics.PLAYER_HOTBAR
            || semantic == SlotSemantics.TOOLBOX
            || semantic == SlotSemantics.CRAFTING_GRID;
    }

    @Unique
    private static ItemStack allyouneed$insertIntoMenuSlots(AEBaseMenu self, ItemStack stack) {
        var current = stack;
        for (var cs : self.slots) {
            if (current.isEmpty()) break;
            if (allyouneed$isPlayerSide(self, cs)) continue;
            if (cs instanceof FakeSlot || cs instanceof CraftingMatrixSlot) continue;
            if (!(cs instanceof AppEngSlot aes) || !aes.isSlotEnabled()) continue;
            if (!cs.mayPlace(current)) continue;
            current = aes.getSlotInv().insertItem(0, current, false);
        }
        return current;
    }
}
